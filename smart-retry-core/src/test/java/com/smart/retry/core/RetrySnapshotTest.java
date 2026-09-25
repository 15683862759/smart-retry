package com.smart.retry.core;

import com.smart.retry.common.model.MethodChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void shouldRestoreParentAsTailAfterNestedMethodLeaves() throws Exception {
        Method outerMethod = Sample.class.getDeclaredMethod("outer");
        Method nestedMethod = Sample.class.getDeclaredMethod("nested");
        MethodChain outer = chain(outerMethod);
        MethodChain nested = chain(nestedMethod);
        RetrySnapshot.setInterceptorChain(outer);
        RetrySnapshot.setInterceptorChain(nested);

        Assertions.assertFalse(outer.isTail(), "嵌套调用期间父方法不应是链尾");

        RetrySnapshot.removeInterceptorChain(nestedMethod);

        assertNull(RetrySnapshot.getChainByMethod(nestedMethod));
        Assertions.assertTrue(outer.isTail(),
                "嵌套方法退出后父方法应恢复为链尾，否则外层异常无法注册重试");
    }

    private MethodChain chain(Method method) {
        MethodChain methodChain = new MethodChain();
        methodChain.setMethod(method);
        return methodChain;
    }

    private static class Sample {
        void retry() {
        }

        void outer() {
        }

        void nested() {
        }
    }
}
