package com.smart.retry.core.scanner;

import com.smart.retry.common.annotation.RetryOnMethod;
import com.smart.retry.common.exception.RetryException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

public class RetryMethodScannerTest {

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
}
