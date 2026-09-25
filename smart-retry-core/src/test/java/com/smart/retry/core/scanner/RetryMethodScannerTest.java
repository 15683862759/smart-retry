package com.smart.retry.core.scanner;

import com.smart.retry.common.annotation.RetryOnMethod;
import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.constant.ExecuteResultStatus;
import com.smart.retry.common.RetryListener;
import com.smart.retry.common.annotation.RetryOnClass;
import com.smart.retry.core.cache.RetryCache;
import com.smart.retry.core.util.RetryTaskCodeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.lang.reflect.Method;

public class RetryMethodScannerTest {

    @Test
    void keepsLegacyTaskCodeWhenNoAnnotatedOverloadExists() throws Exception {
        Method method = SingleMethodCase.class.getDeclaredMethod("execute", String.class);

        String taskCode = RetryTaskCodeBuilder.build(method);

        Assertions.assertEquals(SingleMethodCase.class.getName() + "#execute", taskCode);
    }

    @Test
    void addsParameterSignatureForAnnotatedOverloads() throws Exception {
        Method method = OverloadCase.class.getDeclaredMethod("execute", String.class);

        String taskCode = RetryTaskCodeBuilder.build(method);

        Assertions.assertEquals(OverloadCase.class.getName() + "#execute(java.lang.String)", taskCode);
    }

    @Test
    void scanDoesNotInstantiateUnrelatedLazyBean() {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(ScannerConfiguration.class);
        applicationContext.refresh();
        try {
            new RetryMethodScanner().scan(applicationContext);

            Assertions.assertFalse(applicationContext.getBeanFactory()
                    .containsSingleton("lazyBeanWithoutRetry"));
            Assertions.assertNotNull(RetryCache.get(RetryMethodBean.class.getName() + "#execute"));
        } finally {
            RetryCache.remove(RetryMethodBean.class.getName() + "#execute");
            applicationContext.close();
        }
    }

    @Test
    void rejectsAmbiguousListenerConsumeMethods() {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(AmbiguousListener.class);
        applicationContext.refresh();
        try {
            RetryException exception = Assertions.assertThrows(RetryException.class,
                    () -> new RetryClassScanner().scan(applicationContext));

            Assertions.assertTrue(exception.getMessage().contains("exactly one non-synthetic consume method"));
        } finally {
            applicationContext.close();
        }
    }

    @Test
    void scanRejectsBlankClassTaskCode() {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(BlankTaskCodeListener.class);
        applicationContext.refresh();
        try {
            RetryException exception = Assertions.assertThrows(RetryException.class,
                    () -> new RetryClassScanner().scan(applicationContext));

            Assertions.assertTrue(exception.getMessage().contains("taskCode"));
            Assertions.assertNull(RetryCache.get(""));
        } finally {
            RetryCache.remove("");
            applicationContext.close();
        }
    }

    @Test
    void acceptsIndependentIncludeAndExcludeTypes() throws Exception {
        Method method = method("independentTypes");

        Assertions.assertDoesNotThrow(() ->
                RetryMethodScanner.checkExceptionConfiguration("test-task", method.getAnnotation(RetryOnMethod.class)));
    }

    @Test
    void rejectsSameIncludeAndExcludeType() {
        Method method = method("sameType");

        RetryException exception = Assertions.assertThrows(RetryException.class, () ->
                RetryMethodScanner.checkExceptionConfiguration("test-task", method.getAnnotation(RetryOnMethod.class)));
        Assertions.assertTrue(exception.getMessage().contains("IllegalStateException"));
    }

    @Test
    void rejectsOverlappingExceptionHierarchy() {
        Method method = method("hierarchyTypes");

        RetryException exception = Assertions.assertThrows(RetryException.class, () ->
                RetryMethodScanner.checkExceptionConfiguration("test-task", method.getAnnotation(RetryOnMethod.class)));
        Assertions.assertTrue(exception.getMessage().contains("RuntimeException"));
    }

    @Test
    void rejectsNonPositiveIntervalSecond() {
        Method method = method("nonPositiveInterval");

        RetryException exception = Assertions.assertThrows(RetryException.class, () ->
                RetryMethodScanner.checkRetryConfiguration("test-task", method.getAnnotation(RetryOnMethod.class)));
        Assertions.assertTrue(exception.getMessage().contains("intervalSecond must be positive"));
    }

    @Test
    void scanRejectsNonPositiveIntervalSecond() {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(InvalidIntervalConfiguration.class);
        applicationContext.refresh();
        try {
            RetryException exception = Assertions.assertThrows(RetryException.class,
                    () -> new RetryMethodScanner().scan(applicationContext));

            Assertions.assertTrue(exception.getMessage().contains("intervalSecond must be positive"));
        } finally {
            applicationContext.close();
        }
    }

    @Test
    void scanRegistersRetryMethodOnJdkProxyBean() {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(ProxiedRetryConfiguration.class);
        applicationContext.refresh();
        String taskCode = ProxiedRetryTarget.class.getName() + "#execute";
        try {
            Object bean = applicationContext.getBean("proxiedRetryTarget");

            Assertions.assertTrue(AopUtils.isAopProxy(bean));
            new RetryMethodScanner().scan(applicationContext);

            Assertions.assertNotNull(RetryCache.get(taskCode),
                    "JDK proxy Bean 上的 @RetryOnMethod 不能丢失注册");
        } finally {
            RetryCache.remove(taskCode);
            applicationContext.close();
        }
    }

    private static Method method(String name) {
        Method method = Assertions.assertDoesNotThrow(() -> RetryMethodScannerTest.class.getDeclaredMethod(name));
        Assertions.assertNotNull(method.getAnnotation(RetryOnMethod.class));
        return method;
    }

    @RetryOnMethod(include = IllegalArgumentException.class, exclude = IllegalStateException.class)
    private void independentTypes() {
    }

    @RetryOnMethod(include = IllegalStateException.class, exclude = IllegalStateException.class)
    private void sameType() {
    }

    @RetryOnMethod(include = IllegalStateException.class, exclude = RuntimeException.class)
    private void hierarchyTypes() {
    }

    @RetryOnMethod(intervalSecond = 0)
    private void nonPositiveInterval() {
    }

    private static class SingleMethodCase {
        @RetryOnMethod
        void execute(String value) {
        }

        void execute(Integer value) {
        }
    }

    private static class OverloadCase {
        @RetryOnMethod
        void execute(String value) {
        }

        @RetryOnMethod
        void execute(Integer value) {
        }
    }

    @Configuration
    static class ScannerConfiguration {

        @Bean
        @Lazy
        LazyBeanWithoutRetry lazyBeanWithoutRetry() {
            return new LazyBeanWithoutRetry();
        }

        @Bean
        RetryMethodBean retryMethodBean() {
            return new RetryMethodBean();
        }
    }

    static class LazyBeanWithoutRetry {
    }

    static class RetryMethodBean {
        @RetryOnMethod
        public void execute() {
        }
    }

    @Configuration
    static class InvalidIntervalConfiguration {

        @Bean
        InvalidIntervalBean invalidIntervalBean() {
            return new InvalidIntervalBean();
        }
    }

    static class InvalidIntervalBean {

        @RetryOnMethod(intervalSecond = -1)
        public void execute() {
        }
    }

    @Configuration
    static class ProxiedRetryConfiguration {

        @Bean
        static RetryProxyBeanPostProcessor retryProxyBeanPostProcessor() {
            return new RetryProxyBeanPostProcessor();
        }

        @Bean
        ProxiedRetryTarget proxiedRetryTarget() {
            return new ProxiedRetryTarget();
        }
    }

    interface ProxiedRetryApi {

        void execute();
    }

    static class ProxiedRetryTarget implements ProxiedRetryApi {

        @RetryOnMethod
        public void execute() {
        }
    }

    static class RetryProxyBeanPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!"proxiedRetryTarget".equals(beanName)) {
                return bean;
            }
            ProxyFactory proxyFactory = new ProxyFactory(bean);
            proxyFactory.setInterfaces(ProxiedRetryApi.class);
            return proxyFactory.getProxy();
        }
    }

    @RetryOnClass(taskCode = "")
    static class BlankTaskCodeListener implements RetryListener<String> {
        @Override
        public ExecuteResultStatus consume(String param) {
            return ExecuteResultStatus.SUCCESS;
        }
    }

    @RetryOnClass(taskCode = "ambiguous-listener-test")
    static class AmbiguousListener implements RetryListener<String> {
        @Override
        public ExecuteResultStatus consume(String param) {
            return ExecuteResultStatus.SUCCESS;
        }

        public ExecuteResultStatus consume(Integer param) {
            return ExecuteResultStatus.SUCCESS;
        }
    }
}
