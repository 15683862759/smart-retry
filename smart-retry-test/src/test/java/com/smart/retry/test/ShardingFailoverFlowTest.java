package com.smart.retry.test;

import com.smart.retry.common.RetryTaskOperator;
import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.model.RetryTaskBuilder;
import com.smart.retry.common.utils.IpUtils;
import com.smart.retry.core.ShardingContextHolder;
import com.smart.retry.mybatis.heart.MybatisHeart;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.TimeUnit;

/**
 * 死实例分片接管后的完整重试流程测试
 *
 * <p>验证点：
 * 1. 心跳超时的分片会被存活实例抢占；
 * 2. 原本属于死实例的监听器任务会在新实例上继续执行；
 * 3. 原本属于死实例的方法注解任务会在新实例上继续执行；
 * 4. 两种任务都是前两次失败、第三次成功，最终状态和剩余次数正确。
 *
 * @Author Codex
 * @Version ShardingFailoverFlowTest.java, v 0.1 2026年09月26日 Codex
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ShardingFailoverFlowTest extends AbstractTest {

    private static final String CLASS_TASK_CODE = "class-retry-flow";

    private static final String METHOD_TASK_CODE =
            "com.smart.retry.test.MethodRetryTarget#callWithRetry";

    @Autowired
    private RetryTaskOperator<TestParam> retryTaskOperator;

    @Autowired
    private ClassRetryFlowListener classRetryFlowListener;

    @Autowired
    private MethodRetryTarget methodRetryTarget;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MybatisHeart mybatisHeart;

    private String deadInstanceId;

    @Before
    public void setUp() {
        deadInstanceId = "sharding-failover-" + System.nanoTime();
        // 共享本地库中可能有历史死分片；先刷新它们，保证本用例只抢占自己构造的死分片。
        jdbcTemplate.update("UPDATE retry_sharding SET last_heartbeat = NOW() WHERE instance_id <> ?",
                deadInstanceId);
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code IN (?, ?)",
                CLASS_TASK_CODE, METHOD_TASK_CODE);
        jdbcTemplate.update("DELETE FROM retry_sharding WHERE instance_id = ?",
                deadInstanceId);
        ClassRetryFlowNotify.reset();
        MethodRetryNotify.reset();
        classRetryFlowListener.reset();
        methodRetryTarget.reset();
    }

    @After
    public void tearDown() {
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code IN (?, ?)",
                CLASS_TASK_CODE, METHOD_TASK_CODE);
        jdbcTemplate.update("DELETE FROM retry_sharding WHERE instance_id = ?",
                deadInstanceId);
        mybatisHeart.initHeart();
        ClassRetryFlowNotify.reset();
        MethodRetryNotify.reset();
        classRetryFlowListener.reset();
        methodRetryTarget.reset();
    }

    @Test
    public void testClassAndMethodRetryContinueAfterDeadInstanceShardingTakenOver()
            throws Exception {
        long deadShardingKey = insertDeadSharding();
        String currentInstanceId = IpUtils.getIp() + ":7099";
        Assert.assertTrue("测试实例应在10秒内接管心跳超时的死分片",
                awaitShardingTakenOver(deadShardingKey, currentInstanceId));
        Assert.assertTrue("接管后的分片必须写入当前实例内存",
                awaitShardingInContext(deadShardingKey));

        String classRunId = "failover-class-" + System.nanoTime();
        long classTaskId = createClassTaskOnDeadSharding(classRunId, deadShardingKey);

        String methodRunId = "failover-method-" + System.nanoTime();
        long methodTaskId = createMethodTaskOnDeadSharding(methodRunId, deadShardingKey);

        Assert.assertTrue("监听器任务应在接管实例上完成三次重试",
                ClassRetryFlowNotify.awaitCompletion(30, TimeUnit.SECONDS));
        Assert.assertTrue("方法注解任务应在接管实例上完成三次重试",
                MethodRetryNotify.awaitCompletion(30, TimeUnit.SECONDS));

        assertSuccessTask(classTaskId, deadShardingKey);
        assertSuccessTask(methodTaskId, deadShardingKey);
        Assert.assertEquals("监听器应执行3次", 3,
                classRetryFlowListener.getExecuteCount(classRunId));
        Assert.assertEquals("方法应执行3次", 3, methodRetryTarget.getExecuteCount());
    }

    private long insertDeadSharding() {
        jdbcTemplate.update(
                "INSERT INTO retry_sharding(gmt_create, creator_id, instance_id, status, last_heartbeat) " +
                        "VALUES (DATE_SUB(NOW(), INTERVAL 10 MINUTE), ?, ?, 1, " +
                        "DATE_SUB(NOW(), INTERVAL 10 MINUTE))",
                deadInstanceId, deadInstanceId);
        Long shardingKey = jdbcTemplate.queryForObject(
                "SELECT id FROM retry_sharding WHERE instance_id = ?",
                Long.class, deadInstanceId);
        Assert.assertNotNull(shardingKey);
        return shardingKey;
    }

    private long createClassTaskOnDeadSharding(String runId, long deadShardingKey) {
        TestParam param = new TestParam(runId);
        param.setIndex(1);
        long taskId = retryTaskOperator.createTask(RetryTaskBuilder.<TestParam>of()
                .withTaskCode(CLASS_TASK_CODE)
                .withTaskDesc("死分片接管后的监听器重试流程")
                .withRetryNum(3)
                .withDelaySecond(10)
                .withIntervalSecond(1)
                .withNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED)
                .withParam(param));
        Assert.assertTrue("监听器重试任务应创建成功", taskId > 0);
        assignTaskToDeadSharding(taskId, deadShardingKey);
        return taskId;
    }

    private long createMethodTaskOnDeadSharding(String runId, long deadShardingKey) {
        try {
            methodRetryTarget.callWithRetry(runId);
            Assert.fail("方法首次调用应抛出异常并注册重试任务");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("method retry attempt 1"));
        }

        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM retry_task WHERE task_code = ?",
                Long.class, METHOD_TASK_CODE);
        Assert.assertNotNull("方法重试任务应已写入数据库", taskId);
        assignTaskToDeadSharding(taskId, deadShardingKey);
        return taskId;
    }

    private void assignTaskToDeadSharding(long taskId, long deadShardingKey) {
        int updated = jdbcTemplate.update(
                "UPDATE retry_task SET sharding_key = ?, next_plan_time = CURRENT_TIMESTAMP WHERE id = ?",
                deadShardingKey, taskId);
        Assert.assertEquals("任务必须固定到被接管的死分片", 1, updated);
    }

    private boolean awaitShardingTakenOver(long shardingKey, String expectedInstanceId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            String instanceId = jdbcTemplate.queryForObject(
                    "SELECT instance_id FROM retry_sharding WHERE id = ?",
                    String.class, shardingKey);
            if (expectedInstanceId.equals(instanceId)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }

    private boolean awaitShardingInContext(long shardingKey) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (ShardingContextHolder.shardingIndex().contains(shardingKey)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }

    private void assertSuccessTask(long taskId, long expectedShardingKey) {
        RetryTask task = jdbcTemplate.queryForObject(
                "SELECT id, task_code, status, retry_num, origin_retry_num, sharding_key " +
                        "FROM retry_task WHERE id = ?",
                (rs, rowNum) -> {
                    RetryTask result = new RetryTask();
                    result.setId(rs.getLong("id"));
                    result.setTaskCode(rs.getString("task_code"));
                    result.setStatus(rs.getInt("status"));
                    result.setRetryNum(rs.getInt("retry_num"));
                    result.setOriginRetryNum(rs.getInt("origin_retry_num"));
                    result.setShardingKey(rs.getLong("sharding_key"));
                    return result;
                }, taskId);
        Assert.assertNotNull(task);
        Assert.assertEquals("接管后的任务终态必须是 SUCCESS",
                RetryTaskStatus.SUCCESS.getCode(), task.getStatus());
        Assert.assertEquals("三次执行后剩余次数必须是0", Integer.valueOf(0), task.getRetryNum());
        Assert.assertEquals("任务必须保留原死实例分片归属",
                Long.valueOf(expectedShardingKey), task.getShardingKey());
    }
}
