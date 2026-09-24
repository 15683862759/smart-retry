package com.smart.retry.test;

import com.smart.retry.common.RetryContainer;
import com.smart.retry.common.RetryTaskOperator;
import com.smart.retry.common.SmartRetryRunFlag;
import com.smart.retry.core.HeartbeatContainer;
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
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.TimeUnit;

/**
 * 重试完整流程与容器销毁测试
 *
 * @Author Codex
 * @Version ContainerLifecycleFlowTest.java, v 0.1 2026年09月25日 Codex
 * @Description: TODO
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ContainerLifecycleFlowTest extends AbstractTest {

    private static final String TASK_CODE = "class-retry-flow";

    @Autowired
    private RetryTaskOperator<TestParam> retryTaskOperator;

    @Autowired
    private ClassRetryFlowListener classRetryFlowListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RetryContainer retryContainer;

    @Autowired
    private HeartbeatContainer heartbeatContainer;

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
    public void testClassRetryFlowThenContainerStops() throws Exception {
        String runId = "lifecycle-retry-" + System.nanoTime();
        TestParam param = new TestParam(runId);
        param.setIndex(1);

        RetryTaskBuilder<TestParam> builder = RetryTaskBuilder.<TestParam>of()
                .withTaskCode(TASK_CODE)
                .withTaskDesc("容器销毁前完整重试流程")
                .withRetryNum(3)
                .withDelaySecond(1)
                .withIntervalSecond(1)
                .withNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED)
                .withParam(param);

        Assert.assertTrue("容器销毁前应创建监听器重试任务",
                retryTaskOperator.createTask(builder) > 0);
        Assert.assertTrue("容器销毁前重试任务应完成",
                ClassRetryFlowNotify.awaitCompletion(30, TimeUnit.SECONDS));
        Assert.assertEquals("监听器应执行3次", 3,
                classRetryFlowListener.getExecuteCount(runId));
        RetryTask task = ClassRetryFlowNotify.getTask();
        Assert.assertNotNull("重试通知应携带任务", task);
        Assert.assertEquals(RetryTaskStatus.SUCCESS.getCode(), task.getStatus());

        retryContainer.destroy();
        heartbeatContainer.destroy();

        Assert.assertFalse("容器销毁后调度开关应关闭", SmartRetryRunFlag.getFlag());
        Assert.assertTrue("生产者线程应退出", awaitThreadExit("smart-retry-producer", 5));
        Assert.assertTrue("调度线程应退出", awaitThreadExit("smart-retry-scheduler", 5));
        Assert.assertTrue("心跳线程应退出", awaitThreadExit("smart-retry-heartbeat", 5));
        Assert.assertTrue("死分片扫描线程应退出", awaitThreadExit("smart-retry-scramble", 5));
    }

    private static boolean awaitThreadExit(String threadName, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> threadName.equals(thread.getName()) && thread.isAlive());
            if (!alive) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }
}
