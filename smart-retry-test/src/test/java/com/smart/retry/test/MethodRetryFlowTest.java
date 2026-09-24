package com.smart.retry.test;

import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.model.RetryTask;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 方法注解重试完整流程测试
 *
 * @Author Codex
 * @Version MethodRetryFlowTest.java, v 0.1 2026年09月19日 Codex
 */
public class MethodRetryFlowTest extends AbstractTest {

    private static final String TASK_CODE =
            "com.smart.retry.test.MethodRetryTarget#callWithRetry";

    @Autowired
    private MethodRetryTarget methodRetryTarget;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code = ?", TASK_CODE);
        MethodRetryNotify.reset();
        methodRetryTarget.reset();
    }

    @After
    public void tearDown() {
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code = ?", TASK_CODE);
        MethodRetryNotify.reset();
    }

    @Test
    public void testMethodRetryFlowFromFailureToSuccess() throws Exception {
        String runId = "method-retry-" + System.nanoTime();

        try {
            methodRetryTarget.callWithRetry(runId);
            Assert.fail("首次调用应抛出异常并注册重试任务");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("method retry attempt 1"));
        }

        Assert.assertTrue("方法重试任务应在30秒内完成",
                MethodRetryNotify.awaitCompletion(30, TimeUnit.SECONDS));

        Assert.assertEquals("方法应执行3次", 3, methodRetryTarget.getExecuteCount());

        RetryTask task = MethodRetryNotify.getTask();
        Assert.assertNotNull("重试通知应携带任务", task);
        Assert.assertEquals("任务终态应为 SUCCESS",
                RetryTaskStatus.SUCCESS.getCode(), task.getStatus());
        Assert.assertEquals("retryNum 应为0", Integer.valueOf(0), task.getRetryNum());
    }
}
