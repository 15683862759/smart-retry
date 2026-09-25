package com.smart.retry.core;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.SmartRetryRunFlag;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleContainerLifecycleTest {

    @AfterEach
    void tearDown() {
        SmartRetryRunFlag.setFlag(false);
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
    void destroyRemovesContainerThatWasNeverStarted() {
        TestConfiguration configuration = new TestConfiguration(emptyTaskAccess());
        SimpleContainer container = new SimpleContainer(configuration, new SmartExecutorConfigure());

        container.destroy();

        Assertions.assertThrows(IllegalStateException.class,
                () -> SimpleContainer.getContainer(configuration),
                "未启动容器销毁后也应从配置绑定表中移除");
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
