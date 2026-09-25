package com.smart.retry.mybatis.repo.impl;

import com.google.common.collect.Lists;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.utils.IpUtils;
import com.smart.retry.common.utils.LogIdUtils;
import com.smart.retry.core.ShardingContextHolder;
import com.smart.retry.mybatis.dao.RetryTaskDao;
import com.smart.retry.mybatis.entity.RetryTaskDO;
import com.smart.retry.mybatis.entity.query.RetryTaskQuery;
import com.smart.retry.mybatis.repo.RetryTaskRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryTaskRepoImpl.java, v 0.1 2025年02月16日 21:09 xiaoqiang
 * @Description: 重试任务仓库默认实现。实现任务保存去重、查询、分批删除，
 * 并把带乐观锁守卫的原子更新直接透传给 MyBatis DAO。
 */
public class RetryTaskRepoImpl implements RetryTaskRepo {

    private final Logger logger = LoggerFactory.getLogger(RetryTaskRepoImpl.class);

    private RetryTaskDao retryTaskDao;

    public RetryTaskRepoImpl(RetryTaskDao retryTaskDao) {
        this.retryTaskDao = retryTaskDao;
    }

    @Override
    /**
     * 保存任务前先按 taskCode + uniqueKey 查询活跃任务，减少重复写入；
     * 并依赖数据库唯一索引兜底处理并发插入竞态。
     *
     * @param retryTask 待保存任务
     * @return 新任务 ID；已存在活跃任务时返回 -1
     */
    public long saveRetryTask(RetryTaskDO retryTask) {
        String uniqueKey = retryTask.getUniqueKey();
        RetryTaskQuery retryTaskQuery = new RetryTaskQuery();
        retryTaskQuery.setUniqueKey(uniqueKey);
        retryTask.setShardingKey(ShardingContextHolder.getRandomShardingIndex());
        retryTaskQuery.setTaskCode(retryTask.getTaskCode());
        retryTaskQuery.setStatusList(Lists.newArrayList(RetryTaskStatus.WAITING.getCode(),
                RetryTaskStatus.RUNNING.getCode(), RetryTaskStatus.FAIL.getCode()));
        retryTaskQuery.setMinRetryNum(1);
        List<RetryTaskDO> retryTaskList = retryTaskDao.selectByQuery(retryTaskQuery);
        if (retryTaskList.size() > 0) {
            logger.warn("[RetryTaskRepoImpl-saveRetryTask]uniqueKey:{} already exists, skip insert", uniqueKey);
            return -1;
        }
        //long nextTime = System.currentTimeMillis() + retryTask.getDelaySecond() * 1000;
        //retryTask.setNextPlanTime(new Date(nextTime));
        retryTask.setOriginRetryNum(retryTask.getRetryNum());
        retryTask.setCreator(IpUtils.getIp());
        // 若调用方未写入 traceId（如 RetryTaskBuilder 路径），在此兜底写一次
        if (retryTask.getCurrentLogId() == null) {
            LogIdUtils.LogIdLookup lookup = LogIdUtils.getCurrentLogIdAndKey();
            retryTask.setCurrentLogId(lookup.isPresent()
                    ? LogIdUtils.encode(lookup.getKey(), lookup.getValue())
                    : LogIdUtils.encode(null, LogIdUtils.getCurrentLogId()));
        }
        try {
            retryTaskDao.insert(retryTask);
        } catch (DuplicateKeyException e) {
            logger.warn("[RetryTaskRepoImpl-saveRetryTask]uniqueKey:{} already exists, skip insert", uniqueKey);
            return -1;
        }
        return retryTask.getId();
    }

    @Override
    /**
     * 管理端按主键更新任务。先确认任务存在，便于返回清晰日志；
     * 该方法不带执行租约守卫，不能用于执行链路状态迁移。
     *
     * @param retryTask 待更新任务
     * @return 受影响行数；任务不存在时返回 0
     */
    public int updateRetryTask(RetryTaskDO retryTask) {

        long taskId = retryTask.getId();
        RetryTaskDO oldTask = retryTaskDao.selectById(taskId);
        if (oldTask == null) {
            logger.warn("[RetryTaskRepoImpl-updateRetryTask]retryTask not exists, id:{}", taskId);
            return 0;
        }

        return retryTaskDao.update(retryTask);
    }

    @Override
    public int claimRetryTask(RetryTaskDO retryTask) {
        // 关键：单条原子 UPDATE 直接透传，禁止先 selectById 再 update，
        // 否则在"读取状态 -> 更新"之间会引入 TOCTOU 竞态窗口。
        return retryTaskDao.claimTask(retryTask);
    }

    @Override
    public int markRetryTaskTerminal(RetryTaskDO retryTask) {
        // 关键：单条原子 UPDATE 直接透传，禁止先查后改，
        // 守卫 executor + retry_num，防止 stale 副本覆盖复活状态/新租约。
        return retryTaskDao.markTerminal(retryTask);
    }

    @Override
    public int markNullTaskObjectFail(RetryTaskDO retryTask) {
        // 关键：单条原子 UPDATE 直接透传，禁止先查后改，
        // 守卫 status IN (0,3) + retry_num，防止覆盖他方已认领的 RUNNING。
        return retryTaskDao.markNullTaskObjectFail(retryTask);
    }

    @Override
    public int reviveDeadRetryTask(Long id, Date deadTaskTime) {
        // 关键：单条原子 UPDATE 直接透传，禁止先查后改，
        // 守卫 status = 1 + gmt_modified，防止复活已终态任务或覆盖刚写入的终态。
        return retryTaskDao.reviveTask(id, deadTaskTime);
    }

    @Override
    /**
     * 按主键查询任务。
     *
     * @param id 任务 ID
     * @return 任务实体；不存在时返回 null
     */
    public RetryTaskDO getRetryTask(long id) {
        return retryTaskDao.selectById(id);
    }

    /**
     * 查询当前实例分片中处于 RUNNING 且执行时间超过阈值的疑似死信任务。
     *
     * @param deadTaskTime 死信判定时间点
     * @return 疑似死信任务列表；当前实例没有分片时返回空列表
     */
    @Override
    public List<RetryTaskDO> listAllDeadTask(Date deadTaskTime) {
        RetryTaskQuery query = new RetryTaskQuery();
        //如果获取不到分区，则返回空列表，不执行任何重试任务
        List<Long> shardingKeyList = ShardingContextHolder.shardingIndex();
        if (CollectionUtils.isEmpty(shardingKeyList)) {
            return Lists.newArrayList();
        }
        long currentTime = System.currentTimeMillis();
        //Date deadTaskTime = new Date(currentTime - maxExecuteTime * 1000);
        query.setDeadTaskTime(deadTaskTime);
        query.setShardingKeyList(shardingKeyList);
        query.setStatusList(Lists.newArrayList(RetryTaskStatus.RUNNING.getCode()));
        query.setOffset(0);
        query.setLimit(1000);

        return retryTaskDao.selectByQuery(query);


    }

    @Override
    public int deleteRetryTask(long taskId) {
        return retryTaskDao.deleteById(taskId);
    }

    @Override
    /**
     * 查询当前实例分片中可执行任务，默认最多 500 条。
     *
     * @return 可执行任务列表；当前实例没有分片时返回空列表
     */
    public List<RetryTaskDO> listAllWaitingRetryTask() {
        RetryTaskQuery query = new RetryTaskQuery();
        //如果获取不到分区，则返回空列表，不执行任何重试任务
        List<Long> shardingKeyList = ShardingContextHolder.shardingIndex();
        if (CollectionUtils.isEmpty(shardingKeyList)) {
            return Lists.newArrayList();
        }
        query.setShardingKeyList(ShardingContextHolder.shardingIndex());
        query.setStatusList(Lists.newArrayList(RetryTaskStatus.WAITING.getCode(), RetryTaskStatus.FAIL.getCode()));
        query.setMinRetryNum(1);
        query.setMaxNextPlanTime(new Date());
        //默认查询1000条数据
        query.setOffset(0);
        query.setLimit(500);
        return retryTaskDao.selectByQuery(query);
    }

    @Override
    /**
     * 查询预加载窗口内到期的可执行任务。
     *
     * @param maxNextPlanTime 执行时间上界；为空时按当前时间处理
     * @param limit           查询上限
     * @return 可执行任务列表；当前实例没有分片时返回空列表
     */
    public List<RetryTaskDO> listAllWaitingRetryTask(Date maxNextPlanTime, int limit) {
        RetryTaskQuery query = new RetryTaskQuery();
        //如果获取不到分区，则返回空列表，不执行任何重试任务
        List<Long> shardingKeyList = ShardingContextHolder.shardingIndex();
        if (CollectionUtils.isEmpty(shardingKeyList)) {
            return Lists.newArrayList();
        }
        query.setShardingKeyList(shardingKeyList);
        query.setStatusList(Lists.newArrayList(RetryTaskStatus.WAITING.getCode(), RetryTaskStatus.FAIL.getCode()));
        query.setMinRetryNum(1);
        query.setMaxNextPlanTime(maxNextPlanTime != null ? maxNextPlanTime : new Date());
        query.setOffset(0);
        query.setLimit(limit);
        return retryTaskDao.selectByQuery(query);
    }

    @Override
    /**
     * 按批删除历史任务，直到某批删除数量小于批量上限，避免单条 SQL 锁过多数据。
     *
     * @param gmtCreate 创建时间上界
     * @param limitRows 单批删除上限
     * @param status    只允许清理的最终状态
     * @return 实际删除总数
     */
    public int deleteByGmtCreate(Date gmtCreate, int limitRows, int status) {
        List<Long> shardingKeyList = ShardingContextHolder.shardingIndex();
        if (CollectionUtils.isEmpty(shardingKeyList)) {
            return 0;
        }
        int deleteCount = 0;
        while (true) {
            int deleteRows = retryTaskDao.deleteByGmtCreate(gmtCreate,
                    limitRows,
                    shardingKeyList,
                    status);
            deleteCount += deleteRows;
            if (deleteRows < limitRows) {
                break;
            }
        }
        return deleteCount;
    }

    @Override
    public int restartRetryTask(long taskId, int targetRetryNum, Date nextPlanTime) {
        return retryTaskDao.restartRetryTask(taskId, targetRetryNum, nextPlanTime);
    }

    @Override
    public int stopRetryTask(long taskId) {
        return retryTaskDao.stopTask(taskId);
    }
}
