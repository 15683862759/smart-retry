package com.smart.retry.core;

import com.smart.retry.common.model.MethodChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RetrySnapshotTest {

    private Method method;

    @BeforeEach
    void setUp() throws Exception {
        method = Sample.class.getDeclaredMethod("retry");
    }

    @AfterEach
    void tearDown() {
        RetrySnapshot.removeInterceptorChain(null);
    }

    @Test
    void shouldRemoveInnermostChainWhenSameMethodIsRecursive() {
        MethodChain outer = chain(method);
        MethodChain inner = chain(method);
        RetrySnapshot.setInterceptorChain(outer);
        RetrySnapshot.setInterceptorChain(inner);

        assertEquals(inner, RetrySnapshot.getChainByMethod(method));

        RetrySnapshot.removeInterceptorChain(method);
        assertEquals(outer, RetrySnapshot.getChainByMethod(method));

        RetrySnapshot.removeInterceptorChain(method);
        assertNull(RetrySnapshot.getChainByMethod(method));
    }

    @Test
    void shouldNotFailWhenChainIsAbsent() {
        assertDoesNotThrow(() -> RetrySnapshot.removeInterceptorChain(method));
        assertNull(RetrySnapshot.getChainByMethod(method));
    }

    private MethodChain chain(Method method) {
        MethodChain methodChain = new MethodChain();
        methodChain.setMethod(method);
        return methodChain;
    }

    private static class Sample {
        void retry() {
        }
    }
}
