package com.smart.retry.test;

import com.smart.retry.common.RetryTaskOperator;
import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.model.RetryTaskBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 监听器模式重试完整流程测试
 *
 * @Author Codex
 * @Version ClassRetryFlowTest.java, v 0.1 2026年09月22日 Codex
 */
public class ClassRetryFlowTest extends AbstractTest {

    private static final String TASK_CODE = "class-retry-flow";

    @Autowired
    private RetryTaskOperator<TestParam> retryTaskOperator;

    @Autowired
    private ClassRetryFlowListener classRetryFlowListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code = ?", TASK_CODE);
        ClassRetryFlowNotify.reset();
        classRetryFlowListener.reset();
    }

    @After
    public void tearDown() {
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code = ?", TASK_CODE);
        ClassRetryFlowNotify.reset();
    }

    @Test
    public void testClassRetryFlowFromFailureToSuccess() throws Exception {
        String runId = "class-retry-" + System.nanoTime();
        TestParam param = new TestParam(runId);
        param.setIndex(1);

        RetryTaskBuilder<TestParam> builder = RetryTaskBuilder.<TestParam>of()
                .withTaskCode(TASK_CODE)
                .withTaskDesc("监听器模式完整重试流程")
                .withRetryNum(3)
                .withDelaySecond(1)
                .withIntervalSecond(1)
                .withNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED)
                .withParam(param);

        long taskId = retryTaskOperator.createTask(builder);
        Assert.assertTrue("监听器重试任务应创建成功", taskId > 0);
        Assert.assertTrue("监听器重试任务应在30秒内完成",
                ClassRetryFlowNotify.awaitCompletion(30, TimeUnit.SECONDS));

        Assert.assertEquals("监听器应执行3次", 3, classRetryFlowListener.getExecuteCount(runId));
        RetryTask task = ClassRetryFlowNotify.getTask();
        Assert.assertNotNull("重试通知应携带任务", task);
        Assert.assertEquals("任务终态应为 SUCCESS",
                RetryTaskStatus.SUCCESS.getCode(), task.getStatus());
        Assert.assertEquals("retryNum 应为0", Integer.valueOf(0), task.getRetryNum());
    }
}
