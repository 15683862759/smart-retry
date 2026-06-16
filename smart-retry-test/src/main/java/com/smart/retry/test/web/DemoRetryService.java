package com.smart.retry.test.web;

import com.smart.retry.common.annotation.RetryOnMethod;
import org.springframework.stereotype.Service;

/**
 * @Author xiaoqiang
 * @Version DemoRetryService.java, v 0.1 2026年06月15日 traceId
 * @Description: 演示场景：业务方法总是抛异常，触发重试，用于验证 traceId 是否正确传递。
 */
@Service
public class DemoRetryService {

    /**
     * 总是抛 IllegalStateException，用于演示 traceId 在多次重试链路中的传递。
     */
    @RetryOnMethod(maxAttempt = 2, firstDelaySecond = 2, intervalSecond = 5)
    public void alwaysFail(String bizNo) {
        throw new IllegalStateException("always-fail for bizNo=" + bizNo);
    }
}
