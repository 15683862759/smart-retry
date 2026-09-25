package com.smart.retry.core.retry;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.annotation.RetryOnMethod;
import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.model.RetryAttemptContext;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.serializer.SmartSerializer;
import com.smart.retry.core.ShardingContextHolder;
import com.smart.retry.core.SimpleContainer;
import com.smart.retry.core.config.SmartExecutorConfigure;
import com.smart.retry.core.serializer.JsonSerializer;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteRetryerTest {

    @BeforeEach
    void setUp() {
        ShardingContextHolder.initShardingIndex(Collections.singletonList(0L));
    }

    @AfterEach
    void tearDown() {
        ShardingContextHolder.initShardingIndex(Collections.emptyList());
    }

    @Test
    void testRegisterTaskDoesNotOverflowLargeFirstDelaySecond() throws Throwable {
        final AtomicReference<RetryTask> savedTask = new AtomicReference<>();
        RetryConfiguration configuration = new TestConfiguration(taskAccessProxy(savedTask));
        new SimpleContainer(configuration, new SmartExecutorConfigure());

        Method method = getClass().getDeclaredMethod("retryTarget");
        RetryOnMethod retryable = method.getAnnotation(RetryOnMethod.class);
        MethodInvocation invocation = new TestMethodInvocation(method);
        RetryAttemptContext context = new RetryAttemptContext();
        context.setMethod(method);
        context.setRetryable(retryable);

        long beforeRegister = System.currentTimeMillis();
        new RemoteRetryer(configuration, invocation, retryable, context).retry();

        RetryTask saved = savedTask.get();
        Assertions.assertNotNull(saved);
        Assertions.assertNotNull(saved.getNextPlanTime());
        Assertions.assertTrue(saved.getNextPlanTime().getTime() >= beforeRegister + Integer.MAX_VALUE * 1000L,
                "最大 int 秒首次延迟不应回绕成过去时间");
    }

    @RetryOnMethod(maxAttempt = 2, firstDelaySecond = Integer.MAX_VALUE)
    private void retryTarget() {
    }

    private RetryTaskAccess taskAccessProxy(AtomicReference<RetryTask> savedTask) {
        return (RetryTaskAccess) Proxy.newProxyInstance(
                RetryTaskAccess.class.getClassLoader(),
                new Class<?>[]{RetryTaskAccess.class},
                (proxy, method, args) -> {
                    if ("saveRetryTask".equals(method.getName())) {
                        savedTask.set((RetryTask) args[0]);
                        return 1L;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
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
            return new JsonSerializer();
        }
    }

    private static class TestMethodInvocation implements MethodInvocation {
        private final Method method;

        private TestMethodInvocation(Method method) {
            this.method = method;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return new Object[0];
        }

        @Override
        public Object proceed() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return method;
        }
    }
}
