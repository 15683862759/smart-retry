package com.smart.retry.core;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.SmartRetryRunFlag;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.model.RetryTaskObject;
import com.smart.retry.common.serializer.SmartSerializer;
import com.smart.retry.core.cache.RetryCache;
import com.smart.retry.core.config.SmartExecutorConfigure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

public class SimpleContainerLifecycleTest {

    @AfterEach
    void tearDown() {
        SmartRetryRunFlag.setFlag(false);
        ShardingContextHolder.initShardingIndex(Collections.emptyList());
        RetryCache.clear();
        RetryTaskCache.clear();
    }

    @Test
    void destroyOneContainerKeepsOtherContainerRunning() throws Exception {
        RetryTaskAccess taskAccess = emptyTaskAccess();
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);

        TestConfiguration firstConfiguration = new TestConfiguration(taskAccess);
        TestConfiguration secondConfiguration = new TestConfiguration(taskAccess);
        SimpleContainer first = new SimpleContainer(firstConfiguration, configure);
        SimpleContainer second = new SimpleContainer(secondConfiguration, configure);

        first.start();
        second.start();
        try {
            // 模拟 ApplicationReadyEvent 后扫描完成；启动窗口内全局开关必须保持关闭。
            SmartRetryRunFlag.setFlag(true);
            first.destroy();

            Assertions.assertThrows(IllegalStateException.class,
                    () -> SimpleContainer.getContainer(firstConfiguration),
                    "容器销毁后应从配置绑定表中移除");
            Assertions.assertSame(second, SimpleContainer.getContainer(secondConfiguration));
            Assertions.assertTrue(SmartRetryRunFlag.getFlag(),
                    "仍有一个容器运行时，全局调度开关不应被关闭");
            Assertions.assertTrue(awaitProducerThreadCount(1, 2),
                    "另一个容器的生产者线程应继续运行");
        } finally {
            first.destroy();
            second.destroy();
        }

        Assertions.assertFalse(SmartRetryRunFlag.getFlag());
        Assertions.assertTrue(awaitProducerThreadCount(0, 2),
                "全部容器销毁后不应残留生产者线程");
    }

    @Test
    void producerWaitsUntilRetryDefinitionsAreRegistered() throws Exception {
        CountingTaskAccess taskAccess = new CountingTaskAccess();
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);
        SimpleContainer container = new SimpleContainer(new TestConfiguration(taskAccess), configure);

        container.start();
        try {
            TimeUnit.MILLISECONDS.sleep(200);

            Assertions.assertEquals(0, taskAccess.listRetryTaskCount,
                    "消费者注册完成前，Producer 不应扫描数据库");
        } finally {
            container.destroy();
        }
    }

    @Test
    void producerBacksOffAfterDatabaseScanFailure() throws Exception {
        AtomicInteger failureCount = new AtomicInteger();
        RetryTaskAccess taskAccess = failingTaskAccess(failureCount);
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);
        SimpleContainer container =
                new SimpleContainer(new TestConfiguration(taskAccess), configure);

        container.start();
        try {
            SmartRetryRunFlag.setFlag(true);
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2);
            while (failureCount.get() == 0 && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            Assertions.assertEquals(1, failureCount.get(),
                    "首次数据库扫描失败后应进入一个扫描周期的退避等待");

            TimeUnit.MILLISECONDS.sleep(200);
            Assertions.assertEquals(1, failureCount.get(),
                    "扫描失败后不应立即重试，避免数据库故障期间忙轮询");
        } finally {
            container.destroy();
        }
    }

    @Test
    void taskExecutorUsesConfiguredKeepAliveSeconds() throws Exception {
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);
        configure.getExecutor().setKeepAliveSeconds(17);
        SimpleContainer container =
                new SimpleContainer(new TestConfiguration(emptyTaskAccess()), configure);

        container.start();
        try {
            java.lang.reflect.Field field = SimpleContainer.class.getDeclaredField("consumerExecutor");
            field.setAccessible(true);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(container);

            Assertions.assertEquals(17, executor.getKeepAliveTime(TimeUnit.SECONDS),
                    "keepAliveSeconds 配置必须传给消费线程池");
        } finally {
            container.destroy();
        }
    }

    @Test
    void destroyRemovesContainerThatWasNeverStarted() {
        TestConfiguration configuration = new TestConfiguration(emptyTaskAccess());
        SimpleContainer container = new SimpleContainer(configuration, new SmartExecutorConfigure());

        container.destroy();

        Assertions.assertThrows(IllegalStateException.class,
                () -> SimpleContainer.getContainer(configuration),
                "未启动容器销毁后也应从配置绑定表中移除");
    }

    @Test
    void enqueueAfterContainerDestroyDoesNotLeaveMemoryMark() {
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);
        SimpleContainer container = new SimpleContainer(
                new TestConfiguration(emptyTaskAccess()), configure);
        container.start();
        container.destroy();

        RetryTask task = new RetryTask();
        task.setTaskCode("destroyed-container-task");
        task.setUniqueKey("task");
        task.setNextPlanTime(new Date());
        container.enqueueAfterCommit(task);

        Assertions.assertEquals(0, RetryTaskCache.size(),
                "容器销毁后不应把任务重新放入内存队列或留下脏去重键");
    }

    @Test
    void validateTaskInDBRejectsTaskMissingStrategyExecutionFields() {
        RetryTask dbTask = validWaitingTask();
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);
        SimpleContainer container =
                new SimpleContainer(new TestConfiguration(fixedTaskAccess(dbTask)), configure);
        ShardingContextHolder.initShardingIndex(Collections.singletonList(1L));

        try {
            dbTask.setOriginRetryNum(null);
            Assertions.assertFalse(container.validateTaskInDB(dbTask),
                    "originRetryNum 缺失时不应执行，否则策略计算会反复 NPE");

        dbTask.setOriginRetryNum(3);
        dbTask.setIntervalSecond(0);
        Assertions.assertFalse(container.validateTaskInDB(dbTask),
                "intervalSecond 非正数时不应执行，否则退避计算会产生非法间隔");

        dbTask.setIntervalSecond(null);
        Assertions.assertFalse(container.validateTaskInDB(dbTask),
                "intervalSecond 缺失时不应执行，否则策略计算会 NPE");

            dbTask.setIntervalSecond(60);
            dbTask.setNextPlanTime(null);
            Assertions.assertFalse(container.validateTaskInDB(dbTask),
                    "nextPlanTime 缺失时不应执行，否则任务会被立即重新调度");
        } finally {
            container.destroy();
        }
    }

    @Test
    void validateTaskInDBRefreshesStaleInMemoryTaskBeforeExecution() {
        RetryTask dbTask = validWaitingTask();
        dbTask.setTaskCode("db-task-code");
        dbTask.setUniqueKey("db-unique-key");

        RetryTask staleTask = new RetryTask();
        staleTask.setId(dbTask.getId());
        staleTask.setTaskCode("stale-task-code");
        staleTask.setUniqueKey("stale-unique-key");

        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);
        SimpleContainer container =
                new SimpleContainer(new TestConfiguration(fixedTaskAccess(dbTask)), configure);
        ShardingContextHolder.initShardingIndex(Collections.singletonList(1L));

        try {
            Assertions.assertTrue(container.validateTaskInDB(staleTask),
                    "数据库中的合法任务应允许执行");
            Assertions.assertEquals("db-task-code", staleTask.getTaskCode(),
                    "执行必须使用数据库最新 taskCode，避免使用过期任务定义");
            Assertions.assertEquals("db-unique-key", staleTask.getUniqueKey(),
                    "执行后释放去重键必须使用数据库最新 uniqueKey");
            Assertions.assertEquals(dbTask.getRetryNum(), staleTask.getRetryNum(),
                    "重试次数必须来自数据库快照，避免旧对象覆盖最新扣减结果");
            Assertions.assertEquals(dbTask.getOriginRetryNum(), staleTask.getOriginRetryNum(),
                    "策略计算必须使用数据库最新原始重试次数");
            Assertions.assertEquals(dbTask.getIntervalSecond(), staleTask.getIntervalSecond(),
                    "策略计算必须使用数据库最新间隔");
            Assertions.assertEquals(dbTask.getNextPlanTime(), staleTask.getNextPlanTime(),
                    "策略计算必须使用数据库最新下次执行时间");
            Assertions.assertEquals(dbTask.getShardingKey(), staleTask.getShardingKey(),
                    "认领任务必须使用数据库最新分片");
        } finally {
            container.destroy();
        }
    }

    @Test
    void destroyOneContainerKeepsGlobalCachesForRunningContainer() {
        String taskCode = "surviving-container-task";
        String taskKey = taskCode + "-task";
        RetryTaskAccess taskAccess = emptyTaskAccess();
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.setTaskFindInterval(1);

        TestConfiguration firstConfiguration = new TestConfiguration(taskAccess);
        TestConfiguration secondConfiguration = new TestConfiguration(taskAccess);
        SimpleContainer first = new SimpleContainer(firstConfiguration, configure);
        SimpleContainer second = new SimpleContainer(secondConfiguration, configure);
        first.start();
        second.start();
        RetryCache.put(taskCode, RetryTaskObject.of().withTaskCode(taskCode));
        Assertions.assertTrue(RetryTaskCache.tryMark(taskKey));

        try {
            first.destroy();

            Assertions.assertSame(second, SimpleContainer.getContainer(secondConfiguration),
                    "另一个容器仍应保持绑定");
            Assertions.assertNotNull(RetryCache.get(taskCode),
                    "销毁一个容器不应清空仍运行容器的任务定义");
            Assertions.assertEquals(1, RetryTaskCache.size(),
                    "销毁一个容器不应清空仍运行容器的内存任务标记");
        } finally {
            first.destroy();
            second.destroy();
        }

        Assertions.assertNull(RetryCache.get(taskCode), "最后一个容器销毁后才应清空任务定义");
        Assertions.assertEquals(0, RetryTaskCache.size(), "最后一个容器销毁后才应清空内存任务标记");
    }

    private static RetryTaskAccess emptyTaskAccess() {
        return (RetryTaskAccess) Proxy.newProxyInstance(
                RetryTaskAccess.class.getClassLoader(),
                new Class<?>[]{RetryTaskAccess.class},
                (proxy, method, args) -> {
                    if ("listRetryTask".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static RetryTaskAccess failingTaskAccess(AtomicInteger failureCount) {
        return (RetryTaskAccess) Proxy.newProxyInstance(
                RetryTaskAccess.class.getClassLoader(),
                new Class<?>[]{RetryTaskAccess.class},
                (proxy, method, args) -> {
                    if ("listRetryTask".equals(method.getName())) {
                        failureCount.incrementAndGet();
                        throw new IllegalStateException("database unavailable");
                    }
                    if ("listDeadTask".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    return null;
                });
    }

    private static RetryTask validWaitingTask() {
        RetryTask task = new RetryTask();
        task.setId(1L);
        task.setStatus(RetryTaskStatus.WAITING.getCode());
        task.setRetryNum(1);
        task.setOriginRetryNum(3);
        task.setIntervalSecond(60);
        task.setNextPlanTime(new Date());
        task.setShardingKey(1L);
        return task;
    }

    private static RetryTaskAccess fixedTaskAccess(RetryTask task) {
        return (RetryTaskAccess) Proxy.newProxyInstance(
                RetryTaskAccess.class.getClassLoader(),
                new Class<?>[]{RetryTaskAccess.class},
                (proxy, method, args) -> {
                    if ("getRetryTask".equals(method.getName())) {
                        return task;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class CountingTaskAccess implements RetryTaskAccess {
        private volatile int listRetryTaskCount;

        @Override
        public List<com.smart.retry.common.model.RetryTask> listDeadTask(int maxExecuteTime) {
            return Collections.emptyList();
        }

        @Override
        public List<com.smart.retry.common.model.RetryTask> listRetryTask(java.util.Date maxNextPlanTime, int limit) {
            listRetryTaskCount++;
            return Collections.emptyList();
        }

        @Override
        public List<com.smart.retry.common.model.RetryTask> listRetryTask() {
            listRetryTaskCount++;
            return Collections.emptyList();
        }

        @Override
        public com.smart.retry.common.model.RetryTask getRetryTask(long taskId) {
            return null;
        }

        @Override
        public long saveRetryTask(com.smart.retry.common.model.RetryTask retryTask) {
            return 0;
        }

        @Override
        public void updateRetryTask(com.smart.retry.common.model.RetryTask retryTask) {
        }

        @Override
        public int claimRetryTask(Long id, String executor, java.util.Date nextPlanTime, Long shardingKey) {
            return 0;
        }

        @Override
        public int markRetryTaskTerminal(Long id, int status, String executor, int retryNum,
                                         java.util.Date nextPlanTime, String attribute) {
            return 0;
        }

        @Override
        public int markNullTaskObjectFail(Long id, String executor, int retryNum, String attribute) {
            return 0;
        }

        @Override
        public int reviveDeadRetryTask(Long id, java.util.Date deadTaskTime) {
            return 0;
        }

        @Override
        public void deleteRetryTask(long taskId) {
        }

        @Override
        public void stopRetryTask(long taskId) {
        }

        @Override
        public int deleteHistoryRetryTask(int clearBeforeDays, int limitRows) {
            return 0;
        }
    }

    private static boolean awaitProducerThreadCount(int expected, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            if (producerThreadCount() == expected) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        return producerThreadCount() == expected;
    }

    private static long producerThreadCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> "smart-retry-producer".equals(thread.getName()))
                .filter(Thread::isAlive)
                .count();
    }

    private static class TestConfiguration implements RetryConfiguration {
        private final RetryTaskAccess taskAccess;

        private TestConfiguration(RetryTaskAccess taskAccess) {
            this.taskAccess = taskAccess;
        }

        @Override
        public RetryTaskAccess getRetryTaskAcess() {
            return taskAccess;
        }

        @Override
        public Identifier getIdentifier() {
            return (taskCode, argStr) -> taskCode + ":" + argStr;
        }

        @Override
        public SmartSerializer getSmartSerializer() {
            return new SmartSerializer() {
                @Override
                public String serializer(Method method, Object[] args) {
                    return null;
                }

                @Override
                public Object[] deSerializer(Method method, String serivlizerVal) {
                    return new Object[0];
                }
            };
        }
    }
}
