package com.smart.retry.test;

import com.smart.retry.common.annotation.RetryOnMethod;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 方法注解重试目标
 *
 * @Author Codex
 * @Version MethodRetryTarget.java, v 0.1 2026年09月19日 Codex
 */
@Service
public class MethodRetryTarget {

    private final AtomicInteger executeCount = new AtomicInteger(0);

    @RetryOnMethod(
            maxAttempt = 3,
            firstDelaySecond = 2,
            intervalSecond = 1,
            retryTaskNotifies = {MethodRetryNotify.class}
    )
    public void callWithRetry(String runId) {
        int attempt = executeCount.incrementAndGet();
        if (attempt < 3) {
            throw new IllegalStateException("method retry attempt " + attempt + ", runId=" + runId);
        }
    }

    public int getExecuteCount() {
        return executeCount.get();
    }

    public void reset() {
        executeCount.set(0);
    }
}
