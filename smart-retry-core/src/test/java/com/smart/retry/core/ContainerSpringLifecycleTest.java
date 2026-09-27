package com.smart.retry.core;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.RetryTaskHeart;
import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.serializer.SmartSerializer;
import com.smart.retry.core.config.SmartExecutorConfigure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class ContainerSpringLifecycleTest {

    private AnnotationConfigApplicationContext applicationContext;

    @AfterEach
    void closeContext() {
        if (applicationContext != null && applicationContext.isActive()) {
            applicationContext.close();
        }
    }

    @Test
    void springContextCloseInvokesContainerDestroyMethods() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(LifecycleConfiguration.class);
        applicationContext.refresh();

        RecordingHeart heart = applicationContext.getBean(RecordingHeart.class);
        SimpleContainer container = applicationContext.getBean(SimpleContainer.class);
        RetryConfiguration configuration = applicationContext.getBean(RetryConfiguration.class);

        Assertions.assertSame(container, SimpleContainer.getContainer(configuration),
                "容器启动后应保持在全局注册表中");

        applicationContext.close();

        Assertions.assertEquals(1, heart.stopCount.get(),
                "Spring 关闭上下文时必须回调 HeartbeatContainer.destroy()");
        Assertions.assertThrows(IllegalStateException.class,
                () -> SimpleContainer.getContainer(configuration),
                "Spring 关闭上下文时必须回调 SimpleContainer.destroy()");
    }

    @Configuration
    static class LifecycleConfiguration {

        @Bean
        RecordingHeart recordingHeart() {
            return new RecordingHeart();
        }

        @Bean
        HeartbeatContainer heartbeatContainer(RecordingHeart recordingHeart) {
            return new HeartbeatContainer(recordingHeart);
        }

        @Bean
        RetryTaskAccess retryTaskAccess() {
            return (RetryTaskAccess) Proxy.newProxyInstance(
                    RetryTaskAccess.class.getClassLoader(),
                    new Class<?>[]{RetryTaskAccess.class},
                    (proxy, method, args) -> null);
        }

        @Bean
        SmartExecutorConfigure smartExecutorConfigure() {
            return new SmartExecutorConfigure();
        }

        @Bean
        RetryConfiguration retryConfiguration(RetryTaskAccess retryTaskAccess) {
            return new RetryConfiguration() {
                @Override
                public RetryTaskAccess getRetryTaskAcess() {
                    return retryTaskAccess;
                }

                @Override
                public Identifier getIdentifier() {
                    return (taskCode, argStr) -> taskCode;
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
            };
        }

        @Bean
        SimpleContainer simpleContainer(RetryConfiguration retryConfiguration,
                                        SmartExecutorConfigure smartExecutorConfigure) {
            return new SimpleContainer(retryConfiguration, smartExecutorConfigure);
        }
    }

    static class RecordingHeart implements RetryTaskHeart {
        private final AtomicInteger stopCount = new AtomicInteger();

        @Override
        public void stop() {
            stopCount.incrementAndGet();
        }
    }
}
