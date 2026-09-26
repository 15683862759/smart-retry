package com.smart.retry.test.web;

import com.smart.retry.common.RetryListener;
import com.smart.retry.common.annotation.RetryOnClass;
import com.smart.retry.common.constant.ExecuteResultStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 故障转移演示的监听器模式消费者。
 *
 * <p>前两次返回失败，第三次返回成功。执行计数保存在内存中，
 * 实例宕机后计数会自然归零，接管实例仍会完整经历失败到成功的过程。
 *
 * @Author Codex
 * @Version FailoverDemoListener.java, v 0.1 2026年09月26日 Codex
 */
@Component
@RetryOnClass(taskCode = "failover-class-demo")
public class FailoverDemoListener implements RetryListener<FailoverDemoParam> {

    private final Map<String, AtomicInteger> executeCounts = new ConcurrentHashMap<>();

    @Override
    public ExecuteResultStatus consume(FailoverDemoParam param) {
        int attempt = executeCounts
                .computeIfAbsent(param.getRunId(), key -> new AtomicInteger())
                .incrementAndGet();
        return attempt < 3 ? ExecuteResultStatus.FAIL : ExecuteResultStatus.SUCCESS;
    }
}
