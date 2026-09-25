package com.smart.retry.test;

import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.RetryTaskEnqueuer;
import com.smart.retry.common.RetryTaskOperator;
import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.model.RetryTaskBuilder;
import com.smart.retry.common.utils.IpUtils;
import com.smart.retry.core.ShardingContextHolder;
import com.smart.retry.web.dto.Result;
import com.smart.retry.web.dto.task.TaskCreateRequest;
import com.smart.retry.web.dao.WebRetryTaskDao;
import com.smart.retry.web.dao.WebRetryShardingDao;
import com.smart.retry.web.entity.RetryTaskDO;
import com.smart.retry.web.dto.task.TaskUpdateRequest;
import com.smart.retry.web.exception.BusinessException;
import com.smart.retry.web.exception.GlobalExceptionHandler;
import com.smart.retry.web.service.RetryTaskService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 数据库并发与任务控制缺陷回归测试。
 *
 * @Author Codex
 * @Version RetryDefectRegressionTest.java, v 0.1 2026年09月25日 Codex
 */
public class RetryDefectRegressionTest extends AbstractTest {

    private static final String TASK_CODE = "class-retry-flow";

    @Autowired
    private RetryTaskOperator<TestParam> retryTaskOperator;

    @Autowired
    private RetryTaskAccess retryTaskAccess;

    @Autowired
    private RetryTaskService retryTaskService;

    @Autowired
    private ObjectProvider<RetryTaskEnqueuer> retryTaskEnqueuerProvider;

    @Autowired
    private ClassRetryFlowListener classRetryFlowListener;

    @Autowired
    private WebRetryTaskDao webRetryTaskDao;

    @Autowired
    private WebRetryShardingDao webRetryShardingDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code = ?", TASK_CODE);
    }

    @After
    public void tearDown() {
        jdbcTemplate.update("DELETE FROM retry_task WHERE task_code = ?", TASK_CODE);
    }

    @Test
    public void testConcurrentSaveWithSameUniqueKeyKeepsOneTask() throws Exception {
        String uniqueKey = "defect-concurrent-" + System.nanoTime();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        try {
            Callable<Long> saveAction = () -> saveTask(uniqueKey);
            List<Future<Long>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return saveAction.call();
                }));
            }
            Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int saved = 0;
            for (Future<Long> future : futures) {
                Long result = future.get(10, TimeUnit.SECONDS);
                if (result > 0) {
                    saved++;
                }
            }
            Assert.assertEquals("同一 uniqueKey 并发保存只允许一条任务", 1, saved);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM retry_task WHERE unique_key = ?",
                    Integer.class, uniqueKey);
            Assert.assertEquals(Integer.valueOf(1), count);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testStopRetryTaskMakesTaskUnschedulable() throws Exception {
        TestParam param = new TestParam("stop-" + System.nanoTime());
        RetryTaskBuilder<TestParam> builder = RetryTaskBuilder.<TestParam>of()
                .withTaskCode(TASK_CODE)
                .withTaskDesc("停止任务回归测试")
                .withRetryNum(3)
                .withDelaySecond(60)
                .withIntervalSecond(1)
                .withNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED)
                .withParam(param);

        long taskId = retryTaskOperator.createTask(builder);
        Assert.assertTrue(taskId > 0);
        retryTaskAccess.stopRetryTask(taskId);

        RetryTask stoppedTask = retryTaskAccess.getRetryTask(taskId);
        Assert.assertNotNull(stoppedTask);
        Assert.assertEquals(RetryTaskStatus.FAIL.getCode(), stoppedTask.getStatus());
        Assert.assertEquals(Integer.valueOf(0), stoppedTask.getRetryNum());
        Assert.assertNull(stoppedTask.getExecutor());
        Assert.assertNull("停止后的任务不允许手动触发", retryTaskOperator.invokeTaskOnceSync(taskId));
    }

    @Test
    public void testClearHistoryWithEmptyShardingIsNoop() {
        List<Long> originalSharding = ShardingContextHolder.shardingIndex();
        try {
            ShardingContextHolder.initShardingIndex(Collections.emptyList());
            int deleted = retryTaskAccess.deleteHistoryRetryTask(30, 100);
            Assert.assertEquals(0, deleted);
        } finally {
            ShardingContextHolder.initShardingIndex(originalSharding);
        }
    }

    @Test
    public void testUnknownTaskCodeFailureAdvancesNextPlanTime() {
        String uniqueKey = "unknown-task-next-plan-" + System.nanoTime();
        long beforeInvoke = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO retry_task(gmt_create, gmt_modified, sharding_key, task_code, status, " +
                        "retry_num, origin_retry_num, next_plan_time, interval_second, " +
                        "next_plan_time_strategy, unique_key) " +
                        "VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0, 1, 1, " +
                        "CURRENT_TIMESTAMP, 60, 1, ?)",
                ShardingContextHolder.getRandomShardingIndex(),
                "unknown-task-code-" + System.nanoTime(), uniqueKey);
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM retry_task WHERE unique_key = ?", Long.class, uniqueKey);
        try {
            retryTaskOperator.invokeTaskOnceSync(taskId);

            RetryTask task = retryTaskAccess.getRetryTask(taskId);
            Assert.assertNotNull(task);
            Assert.assertEquals(RetryTaskStatus.FAIL.getCode(), task.getStatus());
            Assert.assertEquals(Integer.valueOf(0), task.getRetryNum());
            Assert.assertNotNull("未注册任务失败后必须推进下次执行时间",
                    task.getNextPlanTime());
            Assert.assertTrue("未注册任务应按固定间隔推迟下一次扫描",
                    task.getNextPlanTime().getTime() >= beforeInvoke + 59_000L);
        } finally {
            jdbcTemplate.update("DELETE FROM retry_task WHERE unique_key = ?", uniqueKey);
        }
    }

    @Test
    public void testCreateTaskRejectsMissingNextPlanTimeStrategyWithFriendlyError() {
        RetryTaskBuilder<TestParam> builder = RetryTaskBuilder.<TestParam>of()
                .withTaskCode(TASK_CODE)
                .withTaskDesc("策略空值回归测试")
                .withRetryNum(1)
                .withDelaySecond(60)
                .withIntervalSecond(1)
                .withNextPlanTimeStrategy(null)
                .withParam(new TestParam("strategy-null"));

        try {
            retryTaskOperator.createTask(builder);
            Assert.fail("策略为空时应抛出业务异常");
        } catch (RetryException expected) {
            Assert.assertEquals("next plan time strategy is null", expected.getMessage());
        }
    }

    @Test
    public void testIpValidationUsesStrictIpPortFormat() {
        Assert.assertFalse("URL 前缀不允许通过",
                IpUtils.isIPLegal("http://1.1.1.1:8080"));
        Assert.assertFalse("尾部多余字符不允许通过",
                IpUtils.isIPLegal("http://1.1.1.1:8080xxx"));
        Assert.assertTrue("标准 ip:port 应通过",
                IpUtils.isIPLegal("1.1.1.1:8080"));
        Assert.assertFalse("非法端口不应通过",
                IpUtils.isIPLegal("1.1.1.1:99999"));
    }

    @Test
    public void testParseIpPortRejectsInvalidAddressWithFriendlyError() {
        try {
            IpUtils.parseIpPort("1.1.1.1:abc");
            Assert.fail("非法地址应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("IP:端口格式非法，正确格式如 192.168.1.100:8080",
                    expected.getMessage());
        }
    }

    @Test
    public void testUpdateTaskInvalidTimeReturnsBusinessError() throws Exception {
        String uniqueKey = "invalid-time-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO retry_task(gmt_create, gmt_modified, sharding_key, task_code, status, " +
                        "retry_num, origin_retry_num, next_plan_time, unique_key) " +
                        "VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0, 1, 1, CURRENT_TIMESTAMP, ?)",
                ShardingContextHolder.getRandomShardingIndex(), TASK_CODE, uniqueKey);
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM retry_task WHERE unique_key = ?", Long.class, uniqueKey);

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setId(taskId);
        request.setNextPlanTime("2025/12/29 12:00:00");

        try {
            retryTaskService.updateTask(request);
            Assert.fail("非法时间格式应抛出业务异常");
        } catch (BusinessException expected) {
            Assert.assertEquals(Integer.valueOf(400), expected.getCode());
            Assert.assertEquals("时间格式必须为 yyyy-MM-dd HH:mm:ss", expected.getMessage());
        }
    }

    @Test
    public void testRuntimeExceptionMessageIsNotExposedToClient() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Result<Void> result = handler.handleRuntimeException(
                new RuntimeException("select secret from db"));

        Assert.assertEquals(Integer.valueOf(500), result.getCode());
        Assert.assertEquals("系统异常，请联系管理员", result.getMessage());
    }

    @Test
    public void testWebCreatedTaskEntersSchedulerImmediately() throws Exception {
        Assert.assertNotNull("Web 模块必须能注入核心调度器",
                retryTaskEnqueuerProvider.getIfAvailable());

        String runId = "web-enqueue-" + System.nanoTime();
        ClassRetryFlowNotify.reset();
        classRetryFlowListener.reset();

        TaskCreateRequest request = new TaskCreateRequest();
        request.setTaskCode(TASK_CODE);
        request.setTaskDesc("Web创建任务立即调度测试");
        request.setRetryNum(3);
        request.setDelaySecond(1);
        request.setIntervalSecond(2);
        request.setParam("{\"value\":\"" + runId + "\",\"index\":1}");
        request.setShardingKey(ShardingContextHolder.getRandomShardingIndex());
        request.setNextPlanTimeStrategy(NextPlanTimeStrategyEnum.FIXED.getCode());

        Long taskId = retryTaskService.createTask(request);
        Assert.assertNotNull(taskId);
        Assert.assertTrue(taskId > 0);

        Assert.assertTrue("Web创建任务不应等待Producer周期才执行",
                ClassRetryFlowNotify.awaitCompletion(10, TimeUnit.SECONDS));
        Assert.assertEquals("监听器应执行3次", 3,
                classRetryFlowListener.getExecuteCount(runId));
        RetryTask task = ClassRetryFlowNotify.getTask();
        Assert.assertNotNull(task);
        Assert.assertEquals(RetryTaskStatus.SUCCESS.getCode(), task.getStatus());
    }

    @Test
    public void testDeleteShardingIsRejectedWhileActiveTaskExists() {
        String uniqueKey = "atomic-delete-" + System.nanoTime();
        Long shardingId = null;
        try {
            jdbcTemplate.update(
                    "INSERT INTO retry_sharding(gmt_create, status, creator_id, instance_id, last_heartbeat) " +
                            "VALUES (CURRENT_TIMESTAMP, 1, 'atomic-delete-test', '192.0.2.1:8080', CURRENT_TIMESTAMP)",
                    new Object[]{});
            shardingId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbcTemplate.update(
                    "INSERT INTO retry_task(gmt_create, gmt_modified, sharding_key, task_code, status, " +
                            "retry_num, origin_retry_num, next_plan_time, unique_key) " +
                            "VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, 1, 1, CURRENT_TIMESTAMP, ?)",
                    shardingId, TASK_CODE, RetryTaskStatus.WAITING.getCode(), uniqueKey);

            Assert.assertEquals("活跃任务存在时删除必须被数据库守卫拦截", 0,
                    webRetryShardingDao.deleteByIdWhenNoUndeletableTasks(shardingId));

            jdbcTemplate.update("UPDATE retry_task SET status = ? WHERE unique_key = ?",
                    RetryTaskStatus.SUCCESS.getCode(), uniqueKey);
            Assert.assertEquals("任务终态后允许删除实例", 1,
                    webRetryShardingDao.deleteByIdWhenNoUndeletableTasks(shardingId));
            shardingId = null;
        } finally {
            jdbcTemplate.update("DELETE FROM retry_task WHERE unique_key = ?", uniqueKey);
            if (shardingId != null) {
                jdbcTemplate.update("DELETE FROM retry_sharding WHERE id = ?", shardingId);
            }
        }
    }

    @Test
    public void testUpdateTaskParsesCalendarYearAtYearBoundary() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO retry_task(gmt_create, gmt_modified, sharding_key, task_code, status, " +
                        "retry_num, origin_retry_num, next_plan_time, unique_key) " +
                        "VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0, 1, 1, CURRENT_TIMESTAMP, ?)",
                ShardingContextHolder.getRandomShardingIndex(), TASK_CODE,
                "date-parse-" + System.nanoTime());
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM retry_task WHERE task_code = ? ORDER BY id DESC LIMIT 1",
                Long.class, TASK_CODE);

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setId(taskId);
        request.setNextPlanTime("2025-12-29 12:00:00");
        retryTaskService.updateTask(request);

        String nextPlanTime = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(next_plan_time, '%Y-%m-%d %H:%i:%s') FROM retry_task WHERE id = ?",
                String.class, taskId);
        Assert.assertEquals("2025-12-29 12:00:00", nextPlanTime);
    }

    @Test
    public void testDeleteRunningTaskIsRejectedByStatusGuard() {
        String uniqueKey = "delete-running-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO retry_task(gmt_create, gmt_modified, sharding_key, task_code, status, " +
                        "retry_num, origin_retry_num, next_plan_time, unique_key) " +
                        "VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, 1, 1, CURRENT_TIMESTAMP, ?)",
                ShardingContextHolder.getRandomShardingIndex(), TASK_CODE,
                RetryTaskStatus.RUNNING.getCode(), uniqueKey);
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM retry_task WHERE unique_key = ?", Long.class, uniqueKey);

        Assert.assertEquals("RUNNING 任务不允许单条删除", 0, webRetryTaskDao.deleteById(taskId));
        Assert.assertEquals("RUNNING 任务不允许批量删除", 0,
                webRetryTaskDao.batchDeleteByIds(Collections.singletonList(taskId)));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM retry_task WHERE id = ?", Integer.class, taskId);
        Assert.assertEquals(Integer.valueOf(1), count);
    }

    @Test
    public void testUpdateTaskRejectsConcurrentRunningTransition() {
        WebRetryTaskDao taskDao = Mockito.mock(WebRetryTaskDao.class);
        RetryTaskService service = newService(taskDao);
        RetryTaskDO task = new RetryTaskDO();
        task.setId(1L);
        task.setStatus(RetryTaskStatus.WAITING.getCode());
        Mockito.when(taskDao.selectById(1L)).thenReturn(task);
        Mockito.when(taskDao.update(task)).thenReturn(0);

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setId(1L);
        request.setRetryNum(1);

        try {
            service.updateTask(request);
            Assert.fail("并发进入执行中时更新应返回业务失败");
        } catch (BusinessException expected) {
            Assert.assertEquals("任务状态已变化，更新失败", expected.getMessage());
        }
    }

    @Test
    public void testUpdateTaskRejectsConcurrentDelete() {
        WebRetryTaskDao taskDao = Mockito.mock(WebRetryTaskDao.class);
        RetryTaskService service = newService(taskDao);
        RetryTaskDO task = new RetryTaskDO();
        task.setId(1L);
        task.setStatus(RetryTaskStatus.WAITING.getCode());
        Mockito.when(taskDao.selectById(1L)).thenReturn(task).thenReturn(null);
        Mockito.when(taskDao.update(task)).thenReturn(0);

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setId(1L);
        request.setRetryNum(1);

        try {
            service.updateTask(request);
            Assert.fail("任务被并发删除时更新应返回业务失败");
        } catch (BusinessException expected) {
            Assert.assertEquals("任务状态已变化，更新失败", expected.getMessage());
        }
    }

    @Test
    public void testDeleteTaskRejectsConcurrentRunningTransition() {
        WebRetryTaskDao taskDao = Mockito.mock(WebRetryTaskDao.class);
        RetryTaskService service = newService(taskDao);
        RetryTaskDO task = new RetryTaskDO();
        task.setId(1L);
        task.setStatus(RetryTaskStatus.WAITING.getCode());
        Mockito.when(taskDao.selectById(1L)).thenReturn(task);
        Mockito.when(taskDao.deleteById(1L)).thenReturn(0);

        try {
            service.deleteTask(1L);
            Assert.fail("并发进入执行中时删除应返回业务失败");
        } catch (BusinessException expected) {
            Assert.assertEquals("任务状态已变化，删除失败", expected.getMessage());
        }
    }

    @Test
    public void testBatchDeleteTasksRejectsConcurrentPartialDelete() {
        WebRetryTaskDao taskDao = Mockito.mock(WebRetryTaskDao.class);
        RetryTaskService service = newService(taskDao);
        List<Long> ids = java.util.Arrays.asList(1L, 2L);
        Mockito.when(taskDao.selectById(1L)).thenReturn(null);
        Mockito.when(taskDao.selectById(2L)).thenReturn(null);
        Mockito.when(taskDao.batchDeleteByIds(ids)).thenReturn(1);

        try {
            service.batchDeleteTasks(ids);
            Assert.fail("批量删除发生部分失败时应返回业务失败");
        } catch (BusinessException expected) {
            Assert.assertEquals("部分任务状态已变化，删除失败", expected.getMessage());
        }
    }

    private RetryTaskService newService(WebRetryTaskDao taskDao) {
        WebRetryShardingDao shardingDao = Mockito.mock(WebRetryShardingDao.class);
        ObjectProvider<RetryTaskEnqueuer> enqueuerProvider = Mockito.mock(ObjectProvider.class);
        return new RetryTaskService(taskDao, shardingDao, enqueuerProvider);
    }

    private long saveTask(String uniqueKey) {
        RetryTask task = new RetryTask();
        task.setTaskCode(TASK_CODE);
        task.setUniqueKey(uniqueKey);
        task.setRetryNum(1);
        task.setOriginRetryNum(1);
        task.setDelaySecond(60);
        task.setIntervalSecond(1);
        task.setStatus(RetryTaskStatus.WAITING.getCode());
        task.setParameters("{}");
        task.setShardingKey(ShardingContextHolder.getRandomShardingIndex());
        return retryTaskAccess.saveRetryTask(task);
    }
}
