package com.smart.retry.test;

import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.notify.NotifyContext;
import com.smart.retry.common.notify.RetryTaskNotify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 方法注解重试通知
 *
 * @Author Codex
 * @Version MethodRetryNotify.java, v 0.1 2026年09月19日 Codex
 */
public class MethodRetryNotify implements RetryTaskNotify {

    private static volatile CountDownLatch latch = new CountDownLatch(1);
    private static volatile RetryTask task;

    @Override
    public void oneTimeNotify(NotifyContext context) {
        RetryTask current = context.getRetryTask();
        if (current != null) {
            task = current;
        }
    }

    @Override
    public void allRetryTaskFinishNotify(NotifyContext context) {
        latch.countDown();
    }

    public static boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    public static RetryTask getTask() {
        return task;
    }

    public static void reset() {
        latch = new CountDownLatch(1);
        task = null;
    }
}
