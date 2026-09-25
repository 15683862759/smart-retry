package com.smart.retry.core;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryContainer;
import com.smart.retry.common.RetryTaskEnqueuer;
import com.smart.retry.common.SmartRetryExit;
import com.smart.retry.common.SmartRetryRunFlag;
import com.smart.retry.common.constant.ExecuteResultStatus;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.utils.GsonTool;
import com.smart.retry.common.utils.LogIdUtils;
import com.smart.retry.common.exception.RetryTaskClaimedException;
import com.smart.retry.common.model.TaskExecutionResult;
import com.smart.retry.core.cache.RetryCache;
import com.smart.retry.core.config.SmartExecutorConfigure;
import com.smart.retry.core.innovation.DefaultInnovation;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @Author xiaoqiang
 * @Version SimpleContainer.java, v 0.1 2025年02月18日 00:24 xiaoqiang
 * @Description: 重试调度容器。维护数据库兜底扫描、DelayQueue 精准调度、
 * 线程池消费、死信检测和历史清理，是多实例并发下任务执行的核心引擎。
 */
public class SimpleContainer implements RetryContainer, RetryTaskEnqueuer {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SimpleContainer.class);

    /** 每个 RetryConfiguration 只对应一个容器实例，供 Operator/Retryer 查找自己的调度上下文。 */
    private static final Map<RetryConfiguration, SimpleContainer> CONTAINERS =
            new ConcurrentHashMap<>();

    // ========== DelayQueue 精准调度相关字段 ==========

    /**
     * 内存精准调度队列
     */
    private final DelayQueue<ScheduledTask> delayQueue = new DelayQueue<>();

    /**
     * 调度线程
     */
    private Thread schedulerThread;

    private Thread producerThread;

    private Thread deadLetterThread;

    private volatile boolean containerRunning;

    /**
     * 预加载窗口毫秒数
     */
    private volatile long preloadWindowMs;

    private final RetryConfiguration retryConfiguration;

    private final SmartExecutorConfigure smartConfigure;

    private ThreadPoolExecutor consumerExecutor;

    private BlockingQueue<Runnable> consumerQueue;

    private ThreadPoolTaskScheduler taskScheduler;

    public SimpleContainer(RetryConfiguration retryConfiguration, SmartExecutorConfigure smartExecutorConfigure) {
        this.retryConfiguration = retryConfiguration;
        this.smartConfigure = smartExecutorConfigure;
        CONTAINERS.put(retryConfiguration, this);
    }


    /**
     * JVM 退出钩子。强制中断调度相关线程并关闭线程池，尽量减少退出时的任务悬挂；
     * 已写入数据库的任务仍会在实例恢复后由 Producer 兜底扫描。
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (SimpleContainer container : CONTAINERS.values()) {
                container.shutdownNow();
            }
        }));
    }

    /**
     * 获取指定配置绑定的容器。
     *
     * @param configuration 重试配置
     * @return 绑定容器；未注册时抛出 IllegalStateException
     */
    public static SimpleContainer getContainer(RetryConfiguration configuration) {
        SimpleContainer container = CONTAINERS.get(configuration);
        if (container == null) {
            throw new IllegalStateException("No SimpleContainer bound to RetryConfiguration");
        }
        return container;
    }

    /**
     * 仅供 JVM 退出钩子使用的快速停机：中断常驻线程并请求线程池关闭，不等待任务完成。
     */
    private void shutdownNow() {
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        if (producerThread != null) {
            producerThread.interrupt();
        }
        if (deadLetterThread != null) {
            deadLetterThread.interrupt();
        }
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }


    @Override
    /**
     * 启动调度容器。
     *
     * <p>实现过程：
     * 1. 启动调度线程，但等待扫描器完成注册后才拉取任务；
     * 2. 初始化消费线程池和预加载窗口；
     * 3. 启动 Producer 兜底扫描和 DelayQueue 调度线程；
     * 4. 按配置启动死信检测与历史清理任务。
     * 方法可重复调用，容器已运行时直接返回。
     */
    public void start() {
        synchronized (this) {
            if (containerRunning) {
                return;
            }
            initTaskExecutor(smartConfigure);

            // 初始化预加载窗口
            preloadWindowMs = (long) smartConfigure.getTaskFindInterval() * smartConfigure.getScanPreloadMultiplier() * 1000L;
            containerRunning = true;

            // Producer 兜底扫描线程（低频，仅加载到 DelayQueue）
            producerThread = new Thread(new ProducerTask(), "smart-retry-producer");
            producerThread.start();

            // SchedulerThread 调度线程（从 DelayQueue 消费，精准触发）
            schedulerThread = new Thread(new SchedulerThread(), "smart-retry-scheduler");
            schedulerThread.start();

            if (smartConfigure.getDeadTask().getDeadTaskCheck()) {
                deadLetterThread = new Thread(new DeadLetterTask());
                deadLetterThread.setDaemon(true);
                deadLetterThread.start();
            }

            if (smartConfigure.getClearTask().getEnabled()) {
                initTaskScheduler();
                CronTrigger trigger = new CronTrigger(smartConfigure.getClearTask().getCron());
                taskScheduler.schedule(new ClearTask(), trigger);
            }
        }
    }


    /**
     * 初始化消费线程池。
     *
     * <p>队列容量在配置的 maxInMemory 基础上预留 100 个缓冲位；
     * 队列满时使用 CallerRunsPolicy，让 Producer 线程执行被拒绝任务，从而天然降低扫描速度。
     */
    private synchronized void initTaskExecutor(SmartExecutorConfigure smartConfigure) {

        if (consumerExecutor != null) {
            return;
        }

        int corePoolSize = smartConfigure.getExecutor().getCorePoolSize();
        int maxPoolSize = smartConfigure.getExecutor().getMaxPoolSize();
        long keepAliveSeconds = smartConfigure.getExecutor().getKeepAliveSeconds();
        int queueSize = smartConfigure.getMaxInMemory();
        String name = smartConfigure.getExecutor().getName();
        consumerQueue = new ArrayBlockingQueue<>(queueSize+100);


        consumerExecutor = new ThreadPoolExecutor(corePoolSize,
                maxPoolSize,
                keepAliveSeconds, TimeUnit.SECONDS,
                consumerQueue,
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, name);
                    }
                },
                //采用拒绝策略为callerRunsPolicy，即当线程池队列满时，直接在调用者线程中运行被拒绝的任务
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 计算执行租约续期间隔。
     *
     * <p>取死信超时时间的 1/3，并限制在 1 到 60 秒之间：
     * 下限保证极短超时配置下心跳仍先于死信扫描生效，
     * 上限避免长任务配置带来过高数据库心跳压力。
     *
     * @return 续期间隔，单位秒
     */
    private long getLeaseRenewalSeconds() {
        int maxExecuteTimeout = smartConfigure.getDeadTask().getTaskMaxExecuteTimeout();
        return Math.max(1L, Math.min(60L, maxExecuteTimeout / 3L));
    }

    /**
     * 初始化历史清理调度器。单线程即可满足清理任务低频执行的需求。
     */
    private void initTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("clear-task-scheduler-");
        scheduler.initialize();
        taskScheduler = scheduler;
    }


    @Override
    /**
     * 停止容器并释放调度资源。
     *
     * <p>实现过程：
     * 1. 关闭运行标记并中断调度、兜底扫描、死信扫描线程；
     * 2. 关闭消费线程池和清理调度器，清空内存队列；
     * 3. 从容器注册表移除当前实例；
     * 4. 若最后一个容器已销毁，则清空任务注册与内存去重缓存，并同步全局运行状态。
     */
    public void destroy() {
        boolean wasRunning;
        synchronized (this) {
            wasRunning = containerRunning;
            if (wasRunning) {
                containerRunning = false;

                if (schedulerThread != null) {
                    schedulerThread.interrupt();
                }
                if (producerThread != null) {
                    producerThread.interrupt();
                }
                if (deadLetterThread != null) {
                    deadLetterThread.interrupt();
                }
                if (consumerExecutor != null) {
                    consumerExecutor.shutdownNow();
                }
                if (taskScheduler != null) {
                    taskScheduler.shutdown();
                }

                delayQueue.clear();

                schedulerThread = null;
                producerThread = null;
                deadLetterThread = null;
                consumerExecutor = null;
                consumerQueue = null;
                taskScheduler = null;
            }
        }
        synchronized (SimpleContainer.class) {
            CONTAINERS.remove(retryConfiguration, this);
            boolean hasOtherContainer = !CONTAINERS.isEmpty();
            if (wasRunning && !hasOtherContainer) {
                RetryCache.clear();
                RetryTaskCache.clear();
            }
            // SmartRetryRunFlag 表示"任务定义已注册且容器可调度"。
            // 销毁一个容器时不能把未完成扫描的全局开关打开，因此只在当前开关已打开
            // 且仍有其他容器运行时保持 true。
            SmartRetryRunFlag.setFlag(hasRunningContainer() && SmartRetryRunFlag.getFlag());
        }
    }

    /**
     * 检查是否仍有任意容器处于运行状态。
     *
     * @return true=至少一个容器在运行
     */
    private static boolean hasRunningContainer() {
        for (SimpleContainer container : CONTAINERS.values()) {
            if (container.containerRunning) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成内存去重键，组合 taskCode 与业务 uniqueKey。
     *
     * @param retryTask 重试任务
     * @return 内存去重键
     */
    static String getUniqueKey(RetryTask retryTask) {
        return retryTask.getTaskCode() + "-" + retryTask.getUniqueKey();
    }

    /**
     * DelayQueue 元素，按 next_plan_time 排序
     */
    static class ScheduledTask implements Delayed {
        private final RetryTask task;
        private final long executeTimeMillis;

        ScheduledTask(RetryTask task) {
            this.task = task;
            this.executeTimeMillis = task.getNextPlanTime().getTime();
        }

        /**
         * 获取被包装的重试任务。
         *
         * @return 重试任务领域对象
         */
        RetryTask getTask() {
            return task;
        }

        @Override
        /**
         * 获取距离执行时间还剩多少时间。
         *
         * @param unit 时间单位
         * @return 剩余时间；到期后返回 0
         */
        public long getDelay(TimeUnit unit) {
            return unit.convert(
                    executeTimeMillis - System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS);
        }

        @Override
        /**
         * 按执行时间升序排序，保证 DelayQueue 先弹出最早到期的任务。
         */
        public int compareTo(Delayed o) {
            return Long.compare(this.executeTimeMillis,
                    ((ScheduledTask) o).executeTimeMillis);
        }
    }

    /**
     * 将任务加入 DelayQueue，自动去重。
     * 内存上限（maxInMemory）在此处精确校验：size 检查与去重标记在同一把锁内原子完成。
     *
     * @param task 重试任务
     * @return true=入队成功，false=未入队（已在内存中或已达内存上限）
     */
    public synchronized boolean enqueue(RetryTask task) {
        if (!containerRunning) {
            return false;
        }
        String key = getUniqueKey(task);
        // 内存上限精确控制 + 去重：两者在同一把锁内原子完成，并发下内存任务数不会超过 maxInMemory
        if (!RetryTaskCache.tryMarkIfBelowLimit(key, smartConfigure.getMaxInMemory())) {
            return false;
        }
        delayQueue.put(new ScheduledTask(task));
        return true;
    }

    /**
     * 静态方法：任务写入 DB 后调用，窗口内则入队
     * 供 RemoteRetryer 和 SimpleRetryTaskOperator 使用
     *
     * @param task 重试任务
     */
    public void enqueueIfInWindow(RetryTask task) {
        if (task == null || task.getNextPlanTime() == null) {
            return;
        }

        // 内存上限快速失败：超过 maxInMemory 时拒绝入队，任务留在 DB 由 Producer 兜底扫描。
        // enqueue() 内还会在 synchronized 锁中做精确校验，保证并发下不超限。
        int currentSize = RetryTaskCache.size();
        int maxInMemory = smartConfigure.getMaxInMemory();
        if (currentSize >= maxInMemory) {
            LOGGER.warn("[SimpleContainer#enqueueIfInWindow] 内存任务数已达到上限，"
                            + "任务留在 DB 等待 Producer 兜底扫描。"
                            + "taskId={}, currentSize={}, maxInMemory={}",
                    task.getId(), currentSize, maxInMemory);
            return;
        }

        long effectiveWindowMs = preloadWindowMs;

        long nextPlanTime = task.getNextPlanTime().getTime();
        long windowEnd = System.currentTimeMillis() + effectiveWindowMs;
        if (nextPlanTime <= windowEnd) {
            enqueue(task);
            return;
        }
        if (smartConfigure.shouldLogInfo()) {
            LOGGER.info("[SimpleContainer#enqueueIfInWindow] 任务不在预加载窗口内，等待 Producer 兜底。"
                            + "taskId={}, nextPlanTime={}, windowEnd={}, preloadWindowMs={}",
                    task.getId(), task.getNextPlanTime(), windowEnd, effectiveWindowMs);
        }
    }

    /**
     * 事务提交后入队（若当前在活跃事务中）；否则立即入队。
     *
     * <p>用于"业务数据 + 重试任务同事务持久化"的创建入口（如 createTask）：
     * 内存入队属于非事务副作用，若在事务内直接入队，调用方事务回滚时会产生
     * <ul>
     *     <li><b>幽灵任务</b>：DB 无记录但 delayQueue 已入队，消费时认领失败被跳过，产生无谓调度；</li>
     *     <li><b>脏去重 key</b>：{@link RetryTaskCache#tryMark(String)} 预标记的 key 无事务回调释放，
     *     永久残留在内存去重集合，后续同 uniqueKey 的 createTask 被拦截无法入队，只能靠 Producer 兜底。</li>
     * </ul>
     * 通过 {@link TransactionSynchronization#afterCommit()} 将入队推迟到提交后，回滚时自然跳过。
     * 无活跃事务（代理未生效 / 直接调用）时立即入队，保持兼容。
     *
     * @param task 已写入 DB 并回填 id 的重试任务
     */
    public void enqueueAfterCommit(RetryTask task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueueIfInWindow(task);
                }
            });
        } else {
            enqueueIfInWindow(task);
        }
    }

    /**
     * 单次执行结束后的内存状态处理。
     *
     * <p>实现过程：
     * 1. 任务成功或剩余重试次数耗尽时释放去重键；
     * 2. taskCode 已卸载时释放去重键，交给 Producer 后续重新加载；
     * 3. 下次执行时间超出预加载窗口、或容器已停止时释放去重键；
     * 4. 仍需重试的任务保留去重键作为占位锁，并重新放入 DelayQueue。
     *
     * @param task 已完成本次执行的任务
     * @return true=任务已重新入队，false=任务到达终态或暂不需要内存调度
     */
    boolean afterExecute(RetryTask task) {

        String key = getUniqueKey(task);
        Integer status = task.getStatus();
        // 成功或重试次数耗尽 → 移除占位，结束
        if (RetryTaskStatus.SUCCESS.getCode().equals(status)) {
            RetryTaskCache.unmark(key);
            return false;
        }
        Integer retryNum = task.getRetryNum();
        if (retryNum <= 0) {
            RetryTaskCache.unmark(key);
            return false;
        }
        // 如果taskCode在RetryCache中不存在，说明无法执行，不重新入队
        // 由Producer兜底扫描后续处理
        if (RetryCache.get(task.getTaskCode()) == null) {
            RetryTaskCache.unmark(key);
            return false;
        }
        Date nextPlanTime = task.getNextPlanTime();
        // 使用与 enqueueIfInWindow 一致的防御逻辑
        boolean inWindow = isInWindow(nextPlanTime);
        if (!inWindow) {
            RetryTaskCache.unmark(key);
            return false;
        }
        if (!containerRunning) {
            RetryTaskCache.unmark(key);
            return false;
        }

        // 失败且未到终态：保留占位锁，放入 delayQueue 等待异步调度重试
        delayQueue.put(new ScheduledTask(task));
        return true;
    }

    /**
     * 判断下次执行时间是否落在当前预加载窗口内。
     *
     * @param nextPlanTime 下次执行时间
     * @return true=可以进入 DelayQueue 精准调度
     */
    private boolean isInWindow(Date nextPlanTime) {
        long effectiveWindowMs = preloadWindowMs;
        // 防御：容器未启动时 preloadWindowMs 为 0，回退到配置值计算
        if (effectiveWindowMs <= 0) {
            effectiveWindowMs = (long) smartConfigure.getTaskFindInterval()
                    * smartConfigure.getScanPreloadMultiplier() * 1000L;
        }
        boolean inWindow = nextPlanTime.getTime()
                <= System.currentTimeMillis() + effectiveWindowMs;
        return inWindow;
    }

    /**
     * 执行前校验 DB 状态，防止无效执行
     *
     * @param task 待执行任务
     * @return true=可以执行，false=跳过该任务
     */
    boolean validateTaskInDB(RetryTask task) {
        try {
            RetryTask dbTask = retryConfiguration.getRetryTaskAcess().getRetryTask(task.getId());
            if (dbTask == null) {
                return false;
            }
            Integer status = dbTask.getStatus();
            if (!RetryTaskStatus.WAITING.getCode().equals(status)
                    && !RetryTaskStatus.FAIL.getCode().equals(status)) {
                return false;
            }
            if (dbTask.getRetryNum() == null || dbTask.getRetryNum() <= 0) {
                return false;
            }
            List<Long> shardingIndexList = ShardingContextHolder.shardingIndex();
            if (shardingIndexList == null || !shardingIndexList.contains(dbTask.getShardingKey())) {
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("[validateTaskInDB#validateTaskInDB] check failed for task:{}", task.getId(), e);
            return false;
        }
    }

    /**
     * 调度线程：从 DelayQueue 中 take() 到期任务，仅做内存级分片校验后分发。
     *
     * <p>不做 DB 查询，保持调度路径轻量。完整 DB 校验下沉到 ConsumerTask 工作线程中。
     */
    class SchedulerThread implements Runnable {
        @Override
        public void run() {
            while (SmartRetryExit.isExit() && containerRunning) {
                try {
                    ScheduledTask scheduled = delayQueue.take();  // 阻塞取第一个
                    List<ScheduledTask> batch = new ArrayList<>(101);
                    batch.add(scheduled);
                    delayQueue.drainTo(batch, 100);  // 非阻塞取更多到期任务

                    for (ScheduledTask scheduledTask : batch) {
                        RetryTask task = scheduledTask.getTask();

                        // 仅做内存级分片检查，避免 DB 查询阻塞调度线程
                        if (!checkShardingInMemory(task)) {
                            RetryTaskCache.unmark(getUniqueKey(task));
                            continue;
                        }

                        CompletableFuture.runAsync(
                                new ConsumerTask(task, retryConfiguration),
                                consumerExecutor
                        );
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("[SchedulerThread] error", e);
                }
            }
        }
    }

    /**
     * 内存级分片归属检查：当前实例是否负责该任务的分片。
     *
     * <p>仅使用 ShardingContextHolder 的内存数据，不查询 DB，
     * 保证调度路径零 DB 开销。
     *
     * @param task 待执行任务
     * @return true=当前实例负责该分片
     */
    private boolean checkShardingInMemory(RetryTask task) {
        List<Long> shardingIndexList = ShardingContextHolder.shardingIndex();
        // shardingIndex() 永远返回非 null 的 List，为空表示分片未初始化
        if (shardingIndexList.isEmpty()) {
            // 分片未初始化，放行（由 ConsumerTask 的 validateTaskInDB 兜底）
            return true;
        }
        return shardingIndexList.contains(task.getShardingKey());
    }

    /**
     * 历史清理任务。只删除配置保留期之前且已成功的任务，避免误删仍需重试的数据。
     */
    class ClearTask implements Runnable {

        @Override
        /**
         * 执行一次历史清理。清理失败只记录日志，等待下一次 Cron 触发。
         */
        public void run() {
            try {
                int deleteCount = retryConfiguration.getRetryTaskAcess().deleteHistoryRetryTask(smartConfigure.getClearTask().getBeforeDays(), smartConfigure.getClearTask().getLimitRows());
                if (smartConfigure.shouldLogInfo()) {
                    LOGGER.info("[ClearTask#run] delete expired retry task count {},expire days {},limit rows {} ", deleteCount, smartConfigure.getClearTask().getBeforeDays(), smartConfigure.getClearTask().getLimitRows());
                }
            } catch (Exception e) {
                LOGGER.error("[ClearTask#run] error ", e);
            }
        }


    }

    /**
     * 死信处理任务
     * 死信任务：当任务已经变更状态为执行中，20分钟后，任务状态没有变更，则认为任务执行失败，进行死信处理
     * 处理逻辑是：将任务状态设置为失败，并记录失败原因，通知相关人员进行处理
     */
    class DeadLetterTask implements Runnable {
        @Override
        public void run() {

            while (SmartRetryExit.isExit() && containerRunning) {
                if (!containerRunning) {
                    return;
                }
                try {
                    TimeUnit.SECONDS.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!smartConfigure.getDeadTask().getDeadTaskCheck()) {
                    continue;
                }
                try {

                    List<RetryTask> allRetryTask = retryConfiguration.getRetryTaskAcess().listDeadTask(smartConfigure.getDeadTask().getTaskMaxExecuteTimeout());
                    if (CollectionUtils.isEmpty(allRetryTask)) {
                        continue;
                    }
                    //将任务重新设置为待执行状态。原执行方的租约续期会因状态变更失败而中断工作线程，
                    //避免失去执行权的旧线程继续执行业务并产生重复副作用。
                    // 统一超时判定时间点，避免循环处理期间时间漂移
                    Date deadTaskTime = new Date(System.currentTimeMillis()
                            - smartConfigure.getDeadTask().getTaskMaxExecuteTimeout() * 1000L);
                    for (RetryTask retryTask : allRetryTask) {

                        // 条件化复活（乐观锁 CAS 守卫）：仅当任务仍为 RUNNING(1) 且
                        // gmt_modified < deadTaskTime（确认超时）时才被复活，
                        // 防止复活已终态任务，或覆盖认领方在超时窗口内刚写入的终态。
                        int revived = retryConfiguration.getRetryTaskAcess()
                                .reviveDeadRetryTask(retryTask.getId(), deadTaskTime);
                        if (revived != 1) {
                            // 已被处理（认领方已写终态 / 已被其他实例复活），跳过
                            if (smartConfigure.shouldLogInfo()) {
                                LOGGER.info("[DeadLetterTask] revive skipped, task:{}", retryTask.getId());
                            }
                        }

                    }

                } catch (Exception e) {
                    LOGGER.error("[SimpleContainer#DeadLetterTask]run error,error msg {} ", e.getMessage(), e);

                }
            }

        }
    }

    /**
     * 兜底扫描线程：低频扫描 DB，将遗漏任务加入 DelayQueue
     * 不再直接提交任务到 executor
     */
    class ProducerTask implements Runnable {
        private long sleepBaseTimeMilliseconds;

        ProducerTask() {
            this.sleepBaseTimeMilliseconds = smartConfigure.getTaskFindInterval() * 1000L;
        }

        /**
         * 扫描疑似死信并逐个尝试原子复活。
         * 同时周期扫描数据库中可执行任务，只负责把任务放入 DelayQueue，
         * 不直接提交业务执行，保证所有任务经过统一的调度和校验链路。
         *
         * <p>复活失败通常表示原执行方刚写入终态或另一实例已接管；
         * 该情况只记录日志，不能把任务强制改回可执行状态。
         */
        @Override
        public void run() {

            LOGGER.info("[ProducerTask#run] start run producer task,sleepBaseTimeMilliseconds {}", sleepBaseTimeMilliseconds);
            while (SmartRetryExit.isExit() && containerRunning) {
                if (!containerRunning) {
                    sleepOneInterval();
                    continue;
                }
                if (!SmartRetryRunFlag.getFlag()) {
                    sleepOneInterval();
                    continue;
                }

                try {
                    int currentSize = RetryTaskCache.size();
                    int availableSlots = smartConfigure.getMaxInMemory() - currentSize;
                    if (availableSlots <= 0) {
                        LOGGER.warn("[ProducerTask#run] 内存任务数达到上限 {}, 跳过本轮扫描,当前任务{}",
                                smartConfigure.getMaxInMemory(), currentSize);
                        sleepOneInterval();
                        continue;
                    }

                    // 防御：容器未完全初始化时 preloadWindowMs 可能为 0，使用配置值兜底
                    long effectiveWindowMs = preloadWindowMs;
                    if (effectiveWindowMs <= 0) {
                        effectiveWindowMs = (long) smartConfigure.getTaskFindInterval()
                                * smartConfigure.getScanPreloadMultiplier() * 1000L;
                    }
                    Date maxNextPlanTime = new Date(
                            System.currentTimeMillis() + effectiveWindowMs);
                    List<RetryTask> allRetryTask = retryConfiguration
                            .getRetryTaskAcess()
                            .listRetryTask(maxNextPlanTime, Math.min(availableSlots, 500));

                    if (CollectionUtils.isEmpty(allRetryTask)) {
                        sleepOneInterval();
                        continue;
                    }

                    int enqueued = 0;
                    for (RetryTask retryTask : allRetryTask) {
                        if (enqueue(retryTask)) {
                            enqueued++;
                        }
                    }

                    if (smartConfigure.shouldLogInfo() && enqueued > 0) {
                        LOGGER.info("[ProducerTask#run] 兜底扫描加载 {} 个任务到 DelayQueue, 内存中任务数: {}",
                                enqueued, RetryTaskCache.size());
                    }
                    sleepOneInterval();
                } catch (Exception e) {
                    LOGGER.error("[ProducerTask#run] producer task exception errMsg,{}", e.getMessage(), e);
                    sleepOneInterval();
                }
            }
        }

        /**
         * 休眠一个扫描周期。线程被中断时恢复中断标记，让外层循环退出。
         */
        private void sleepOneInterval() {
            try {
                TimeUnit.MILLISECONDS.sleep(sleepBaseTimeMilliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ========== 监控方法（测试 & 运维） ==========


    // ========== 原有方法 ==========

    /**
     * 提交一个任务到消费线程池。先在内存中标记执行权，避免同一任务被重复提交。
     *
     * @param retryTask          待执行任务
     * @param retryConfiguration 重试配置
     */
    private void doProduceTask(RetryTask retryTask, RetryConfiguration retryConfiguration) {
        //任务存在则不处理，避免重复处理
        if (checkTaskExists(retryTask)) {
            if (smartConfigure.shouldLogInfo()) {
                LOGGER.info("[SimpleContainer#doProduceTask]task exists,taskId:{}", retryTask.getId());
            }
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(new ConsumerTask(retryTask, retryConfiguration), consumerExecutor);
    }

    /**
     * 懒加载消费线程池，供手动触发等非 start() 路径使用。
     *
     * @param smartConfigure 执行器配置
     */
    private void initTaskConsumerExecutor(SmartExecutorConfigure smartConfigure) {
        if (consumerExecutor != null) {
            return;
        }
        initTaskExecutor(smartConfigure);
    }

    /**
     * 异步手动触发一次任务。释放自动入队占位后，把任务提交到消费线程池。
     *
     * @param retryTask          待执行任务
     * @param retryConfiguration 重试配置
     */
    void invokeTaskAsync(RetryTask retryTask,
                        RetryConfiguration retryConfiguration) {
        initTaskConsumerExecutor(smartConfigure);
        // 释放 createTask 中 enqueueIfInWindow 预标记的去重 key
        releaseAutoEnqueueMark(retryTask);
        doProduceTask(retryTask, retryConfiguration);
    }

    /**
     * 同步执行一次任务（不循环重试）。
     * 在当前线程直接执行，仅执行一次。失败后的继续重试由 Producer/调度器异步推进。
     *
     * @param retryTask          待执行的任务
     * @param retryConfiguration 重试配置
     * @return 本次执行结果；null=未执行（被去重拦截 / getTriggerableTask 拒绝）
     */
    TaskExecutionResult invokeTaskOnceSync(RetryTask retryTask,
                                          RetryConfiguration retryConfiguration) {
        // 释放 createTask 中 enqueueIfInWindow 预标记的去重 key，
        // 确保手动触发能获取执行权（避免被 auto-enqueue 的 tryMark 拦截）
        releaseAutoEnqueueMark(retryTask);

        if (checkTaskExists(retryTask)) {
            if (smartConfigure.shouldLogInfo()) {
                LOGGER.info("[SimpleContainer#invokeTaskOnceSync] task already executing, taskId:{}",
                        retryTask.getId());
            }
            return null;
        }
        // 仅执行一次，后续重试由 afterExecute 放入 delayQueue 异步推进
        ConsumerTask task = new ConsumerTask(retryTask, retryConfiguration);
        task.run();
        return task.getResult();
    }

    /**
     * 释放 createTask 中 enqueueIfInWindow 预标记的去重 key。
     * <p>createTask 通过 enqueueIfInWindow → enqueue → tryMark 预先标记任务，
     * 导致紧跟其后的手动触发（invokeTaskOnceSync/Async）被去重拦截。
     * 手动触发前先释放该标记，让 checkTaskExists 能够重新获取执行权。
     *
     * <p>注意：不删除 delayQueue 中的 ScheduledTask（太昂贵），
     * SchedulerThread 取出后会因 tryMark 失败或 validateTaskInDB 失败而跳过。
     */
    private static void releaseAutoEnqueueMark(RetryTask retryTask) {
        String key = getUniqueKey(retryTask);
        RetryTaskCache.unmark(key);
    }

    private static boolean checkTaskExists(RetryTask retryTask) {
        String uniqueKey = getUniqueKey(retryTask);
        // tryMarkInMemory 返回 true = 标记成功（任务不存在），false = 已存在
        return !RetryTaskCache.tryMark(uniqueKey);
    }

    /**
     * 单次执行任务消费者。
     *
     * <p>只执行一轮：DB 校验 → 反射调用 → 回调 {@link SimpleContainer#afterExecute}。
     * 执行失败且未到终态时，由 {@code afterExecute} 将任务重新放入 DelayQueue，
     * 后续重试交给 SchedulerThread/Producer 异步调度，不再在当前线程循环。
     *
     * <p>同步/异步的区别仅在调用方线程（当前线程 {@code run()} vs 提交线程池），
     * 本类不感知调用方，始终保持"单次执行"语义。
     *
     * @Author xiaoqiang
     * @Version ConsumerTask.java, v 0.1 2025年08月27日 xiaoqiang
     * @Description: 单次任务消费者，负责数据库校验、反射执行、终态写入和后续调度衔接。
     */
    class ConsumerTask implements Runnable {

        private final Logger LOGGER = LoggerFactory.getLogger(ConsumerTask.class);

        private final RetryTask retryTask;
        private final RetryConfiguration retryConfiguration;

        /** run() 执行完毕后的结果；未执行（被校验/去重拒绝）时为 null */
        private volatile TaskExecutionResult result;

        ConsumerTask(RetryTask retryTask, RetryConfiguration retryConfiguration) {
            this.retryTask = retryTask;
            this.retryConfiguration = retryConfiguration;
        }

        @Override
        public void run() {
            // 1. DB 校验：只放行 WAITING/FAIL + retryNum>0 + 分片归属
            if (!validateTaskInDB(retryTask)) {
                RetryTaskCache.unmark(getUniqueKey(retryTask));
                this.result = null;
                return;
            }
            // 2. 反射调用：捕获业务返回值；异常时业务结果置 null
            Object businessResult = null;
            try {
                businessResult = new DefaultInnovation(retryTask, retryConfiguration,
                        getLeaseRenewalSeconds()).invoke();
            } catch (RetryTaskClaimedException e) {
                // 认领竞争失败：任务已由其他执行方接管（同 JVM 残留 delayQueue/手动触发，
                // 或跨实例的 Producer），本实例不执行业务。
                // 不调用 afterExecute，避免给胜者再制造一次冗余调度。
                LOGGER.info("[ConsumerTask#run] retry task already claimed by other executor, skip, taskId:{}",
                        retryTask.getId());
                RetryTaskCache.unmark(getUniqueKey(retryTask));
                this.result = null;
                return;
            } catch (Throwable e) {
                LOGGER.error("[ConsumerTask#run] consumer error, taskId:{}", retryTask.getId(), e);
            }
            // 3. 终态→unmark；非终态→入队 delayQueue（保留占位锁）由调度器异步重试
            afterExecute(retryTask);
            // 4. 记录本次结果（状态以 DefaultInnovation 写入内存 task 的 status 为准）
            this.result = new TaskExecutionResult(resolveStatus(retryTask), businessResult);
        }

        /**
         * 获取本次执行结果。
         *
         * <p>必须在 run() 结束后调用；被校验或去重拒绝时返回 null。
         *
         * @return 执行结果
         */
        public TaskExecutionResult getResult() {
            return result;
        }

        /**
         * 将任务状态映射为对调用方暴露的执行结果。
         *
         * @param task 已执行任务
         * @return SUCCESS=任务终态成功，否则 FAIL
         */
        private ExecuteResultStatus resolveStatus(RetryTask task) {
            return RetryTaskStatus.SUCCESS.getCode().equals(task.getStatus())
                    ? ExecuteResultStatus.SUCCESS
                    : ExecuteResultStatus.FAIL;
        }
    }
}
