package com.smart.retry.mybatis.repo.impl;

import com.smart.retry.mybatis.dao.RetryShardingDao;
import com.smart.retry.mybatis.entity.RetryShardingDO;
import com.smart.retry.mybatis.repo.RetryShardingRepo;

import java.util.Collections;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryShardingRepoImpl.java, v 0.1 2025年02月16日 10:48 xiaoqiang
 * @Description: 重试分片仓库默认实现。将实例注册、心跳刷新、死分片抢占和
 * 分片查询委托给 MyBatis DAO。
 */
public class RetryShardingRepoImpl implements RetryShardingRepo {

    private RetryShardingDao retryShardingDao;

    public RetryShardingRepoImpl(RetryShardingDao retryShardingDao) {
        this.retryShardingDao = retryShardingDao;
    }

    @Override
    /**
     * 保存分片记录。
     *
     * @param retrySharding 分片实体
     * @return 受影响行数
     */
    public long saveRetrySharding(RetryShardingDO retrySharding) {
        return retryShardingDao.insert(retrySharding);
    }

    @Override
    /**
     * 刷新实例名下分片的心跳时间。
     *
     * @param instanceId 实例 ID
     * @param status     分片状态
     * @return 受影响行数
     */
    public int updateLastHeartbeat(String instanceId, int status) {
        return retryShardingDao.updateLastHeartbeat(instanceId, status);
    }

    @Override
    /**
     * 抢占心跳超时的死分片。
     *
     * @param instanceId 当前实例 ID
     * @param status     抢占后写入的状态
     * @param timeout    心跳超时秒数
     * @return 抢占成功的分片数量
     */
    public int scrambleDeadSharding(String instanceId, int status,int timeout) {



        return retryShardingDao.scrambleDeadSharding(instanceId, status,timeout);
    }

    @Override
    /**
     * 查询实例名下全部分片。
     *
     * @param instanceId 实例 ID
     * @return 分片实体列表
     */
    public List<RetryShardingDO> selectByInstanceId(String instanceId) {
        return retryShardingDao.selectByInstanceId(instanceId);
    }
}
