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
