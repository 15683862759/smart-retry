package com.smart.retry.test.web;

import com.smart.retry.common.RetryTaskOperator;
import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.model.RetryTaskBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 故障转移演示任务创建入口。
 *
 * <p>两个接口都会把任务创建在当前实例分片上，便于先在 7007 创建任务、
 * 再停止 7007，观察 7006 接管并继续执行。
 *
 * @Author Codex
 * @Version FailoverDemoController.java, v 0.1 2026年09月26日 Codex
 */
@RestController
@RequestMapping("/demo/failover")
public class FailoverDemoController {

    private final RetryTaskOperator<FailoverDemoParam> retryTaskOperator;

    private final FailoverDemoService failoverDemoService;

    public FailoverDemoController(RetryTaskOperator<FailoverDemoParam> retryTaskOperator,
                                   FailoverDemoService failoverDemoService) {
        this.retryTaskOperator = retryTaskOperator;
        this.failoverDemoService = failoverDemoService;
    }

    /**
     * 创建监听器模式故障转移任务。
     *
     * @param runId 业务运行标识，用于隔离多次演示
     * @return 创建结果和任务 ID
     */
    @PostMapping("/class")
    public Map<String, Object> createClassTask(@RequestParam String runId) {
        long taskId = retryTaskOperator.createTask(RetryTaskBuilder.<FailoverDemoParam>of()
                .withTaskCode("failover-class-demo")
                .withTaskDesc("监听器模式故障转移演示")
                .withRetryNum(5)
                .withDelaySecond(2)
                .withIntervalSecond(2)
                .withNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED)
                .withParam(new FailoverDemoParam(runId)));
        Map<String, Object> result = new HashMap<>();
        result.put("taskCode", "failover-class-demo");
        result.put("taskId", taskId);
        return result;
    }

    /**
     * 触发方法注解模式故障转移任务。
     *
     * @param runId 业务运行标识，用于隔离多次演示
     * @return 任务注册结果；首次调用抛出的业务异常属于预期行为
     */
    @PostMapping("/method")
    public Map<String, Object> createMethodTask(@RequestParam String runId) {
        try {
            failoverDemoService.callWithRetry(runId);
        } catch (IllegalStateException expected) {
            Map<String, Object> result = new HashMap<>();
            result.put("taskCode", "com.smart.retry.test.web.FailoverDemoService#callWithRetry");
            result.put("registered", true);
            return result;
        }
        throw new IllegalStateException("方法首次调用应注册重试任务");
    }
}
