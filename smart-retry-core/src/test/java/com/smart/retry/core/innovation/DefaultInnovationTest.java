package com.smart.retry.core.innovation;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.serializer.SmartSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultInnovationTest {

    @AfterEach
    void tearDown() {
        com.smart.retry.core.cache.RetryCache.clear();
        com.smart.retry.core.RetryTaskCache.clear();
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
}
