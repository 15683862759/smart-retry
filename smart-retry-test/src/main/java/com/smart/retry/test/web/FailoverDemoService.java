package com.smart.retry.test.web;

import com.smart.retry.common.annotation.RetryOnMethod;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 故障转移演示的方法注解模式服务。
 *
 * <p>前两次抛出异常，第三次成功。方法和监听器一样使用内存计数，
 * 用于观察实例宕机后接管实例从失败到成功的完整重试过程。
 *
 * @Author Codex
 * @Version FailoverDemoService.java, v 0.1 2026年09月26日 Codex
 */
@Service
public class FailoverDemoService {

    private final Map<String, AtomicInteger> executeCounts = new ConcurrentHashMap<>();

    @RetryOnMethod(
            maxAttempt = 5,
            firstDelaySecond = 2,
            intervalSecond = 2
    )
    public void callWithRetry(String runId) {
        int attempt = executeCounts
                .computeIfAbsent(runId, key -> new AtomicInteger())
                .incrementAndGet();
        if (attempt < 3) {
            throw new IllegalStateException("failover method retry attempt " + attempt);
        }
    }
}
