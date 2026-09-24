package com.smart.retry.test;

import com.smart.retry.common.RetryListener;
import com.smart.retry.common.annotation.RetryOnClass;
import com.smart.retry.common.constant.ExecuteResultStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 监听器模式重试流程目标
 *
 * @Author Codex
 * @Version ClassRetryFlowListener.java, v 0.1 2026年09月22日 Codex
 */
@Component
@RetryOnClass(
        taskCode = "class-retry-flow",
        retryTaskNotifies = {ClassRetryFlowNotify.class}
)
public class ClassRetryFlowListener implements RetryListener<TestParam> {

    private final Map<String, AtomicInteger> executeCounts = new ConcurrentHashMap<>();

    @Override
    public ExecuteResultStatus consume(TestParam param) {
        int attempt = executeCounts
                .computeIfAbsent(param.getValue(), key -> new AtomicInteger())
                .incrementAndGet();
        return attempt < 3 ? ExecuteResultStatus.FAIL : ExecuteResultStatus.SUCCESS;
    }

    public int getExecuteCount(String runId) {
        AtomicInteger count = executeCounts.get(runId);
        return count == null ? 0 : count.get();
    }

    public void reset() {
        executeCounts.clear();
    }
}
