package com.smart.retry.core.innovation;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryListener;
import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.constant.ExecuteResultStatus;
import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.constant.RetryTaskTypeEnum;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.model.RetryTaskObject;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.serializer.SmartSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultInnovationTest {

    @AfterEach
    void tearDown() {
        com.smart.retry.core.cache.RetryCache.clear();
        com.smart.retry.core.RetryTaskCache.clear();
    }

    @Test
    void testLeaseLossInterruptsRunningWorkerThread() throws Throwable {
        AtomicBoolean consumeCompleted = new AtomicBoolean(false);
        SleepingListener listener = new SleepingListener(consumeCompleted, 3);
        RetryTaskAccess taskAccess = (RetryTaskAccess) Proxy.newProxyInstance(
                RetryTaskAccess.class.getClassLoader(),
                new Class<?>[]{RetryTaskAccess.class},
                (proxy, method, args) -> {
                    if ("claimRetryTask".equals(method.getName())) {
                        return 1;
                    }
                    if ("renewExecutionLease".equals(method.getName())) {
                        return 0;
                    }
                    if ("markRetryTaskTerminal".equals(method.getName())) {
                        return 0;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        RetryConfiguration configuration = configuration(taskAccess);

        RetryTask task = new RetryTask();
        task.setId(2L);
        task.setTaskCode("lease-lost-task");
        task.setRetryNum(1);
        task.setOriginRetryNum(1);
        task.setIntervalSecond(60);
        task.setNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED.getCode());
        task.setNextPlanTime(new Date());
        com.smart.retry.core.cache.RetryCache.put("lease-lost-task",
                RetryTaskObject.of()
                        .withTaskCode("lease-lost-task")
                        .withBeanObj(listener)
                        .withRetryType(RetryTaskTypeEnum.CLASS));

        Assertions.assertThrows(IllegalStateException.class,
                () -> new DefaultInnovation(task, configuration, 1).invoke(),
                "失去执行租约后必须中断旧工作线程，避免业务继续产生副作用");
        Assertions.assertFalse(consumeCompleted.get(),
                "失去执行租约后旧工作线程不应完整执行完业务方法");
    }

    @Test
    void testNullTaskObjectFailAdvancesNextPlanTime() throws Throwable {
        AtomicReference<Date> persistedNextPlanTime = new AtomicReference<>();
        RetryTaskAccess taskAccess = (RetryTaskAccess) Proxy.newProxyInstance(
                RetryTaskAccess.class.getClassLoader(),
                new Class<?>[]{RetryTaskAccess.class},
                (proxy, method, args) -> {
                    if ("markNullTaskObjectFail".equals(method.getName())
                            && method.getParameterCount() == 5) {
                        persistedNextPlanTime.set((Date) args[3]);
                        return 1;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        RetryConfiguration configuration = configuration(taskAccess);

        RetryTask task = new RetryTask();
        task.setId(1L);
        task.setTaskCode("unregistered-task-code");
        task.setRetryNum(2);
        task.setOriginRetryNum(2);
        task.setIntervalSecond(60);
        task.setNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED.getCode());
        Date originalNextPlanTime = new Date(System.currentTimeMillis() - 60_000L);
        task.setNextPlanTime(originalNextPlanTime);

        new DefaultInnovation(task, configuration).invoke();

        Assertions.assertNotNull(persistedNextPlanTime.get(),
                "未注册任务失败时必须持久化新的下次执行时间");
        Assertions.assertTrue(persistedNextPlanTime.get().after(originalNextPlanTime),
                "新的下次执行时间应按策略推进");
        Assertions.assertEquals(RetryTaskStatus.FAIL.getCode(), task.getStatus());
        Assertions.assertEquals(1, task.getRetryNum());
    }

    @Test
    void testClassListenerInheritsGenericTypeFromParentClass() throws Throwable {
        AtomicReference<Object> consumedParam = new AtomicReference<>();
        ChildListener listener = new ChildListener(consumedParam);
        RetryTaskAccess taskAccess = (RetryTaskAccess) Proxy.newProxyInstance(
                RetryTaskAccess.class.getClassLoader(),
                new Class<?>[]{RetryTaskAccess.class},
                (proxy, method, args) -> {
                    if ("claimRetryTask".equals(method.getName())) {
                        return 1;
                    }
                    if ("markRetryTaskTerminal".equals(method.getName())) {
                        return 1;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        RetryConfiguration configuration = configuration(taskAccess);

        RetryTask task = new RetryTask();
        task.setId(3L);
        task.setTaskCode("inherited-generic-listener");
        task.setRetryNum(1);
        task.setOriginRetryNum(1);
        task.setIntervalSecond(60);
        task.setNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED.getCode());
        task.setNextPlanTime(new Date());
        task.setParameters("{\"name\":\"order-1001\"}");
        com.smart.retry.core.cache.RetryCache.put("inherited-generic-listener",
                RetryTaskObject.of()
                        .withTaskCode("inherited-generic-listener")
                        .withBeanObj(listener)
                        .withRetryType(RetryTaskTypeEnum.CLASS));

        new DefaultInnovation(task, configuration).invoke();

        Assertions.assertNotNull(consumedParam.get());
        Assertions.assertEquals(ParentParam.class, consumedParam.get().getClass(),
                "监听器泛型参数从父类继承时必须按父类声明的业务类型反序列化");
        Assertions.assertEquals("order-1001", ((ParentParam) consumedParam.get()).getName());
    }

    private RetryConfiguration configuration(RetryTaskAccess taskAccess) {
        return (RetryConfiguration) Proxy.newProxyInstance(
                RetryConfiguration.class.getClassLoader(),
                new Class<?>[]{RetryConfiguration.class},
                (proxy, method, args) -> {
                    if ("getRetryTaskAcess".equals(method.getName())) {
                        return taskAccess;
                    }
                    if ("getIdentifier".equals(method.getName())) {
                        return (Identifier) (taskCode, argStr) -> taskCode;
                    }
                    if ("getSmartSerializer".equals(method.getName())) {
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
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class SleepingListener implements RetryListener<Object> {
        private final AtomicBoolean consumeCompleted;
        private final int sleepSeconds;

        private SleepingListener(AtomicBoolean consumeCompleted, int sleepSeconds) {
            this.consumeCompleted = consumeCompleted;
            this.sleepSeconds = sleepSeconds;
        }

        @Override
        public ExecuteResultStatus consume(Object param) {
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(sleepSeconds);
            while (System.currentTimeMillis() < deadline) {
                if (Thread.interrupted()) {
                    throw new IllegalStateException("worker interrupted after lease loss");
                }
            }
            consumeCompleted.set(true);
            return ExecuteResultStatus.SUCCESS;
        }
    }

    private static class ParentParam {
        private String name;

        String getName() {
            return name;
        }
    }

    private static class ParentListener implements RetryListener<ParentParam> {
        private final AtomicReference<Object> consumedParam;

        ParentListener(AtomicReference<Object> consumedParam) {
            this.consumedParam = consumedParam;
        }

        @Override
        public ExecuteResultStatus consume(ParentParam param) {
            consumedParam.set(param);
            return ExecuteResultStatus.SUCCESS;
        }
    }

    private static class ChildListener extends ParentListener {
        ChildListener(AtomicReference<Object> consumedParam) {
            super(consumedParam);
        }
    }
}
