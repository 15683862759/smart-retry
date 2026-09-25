package com.smart.retry.mybatis.repo;

import com.smart.retry.mybatis.entity.RetryShardingDO;

import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryShardingRepo.java, v 0.1 2025年02月15日 23:55 xiaoqiang
 * @Description: 重试分片仓库接口。为心跳和故障转移流程提供实例注册、
 * 心跳刷新、死分片抢占和分片查询能力。
 */
public interface RetryShardingRepo {

    /**
     * 保存新的分片记录。
     *
     * @param retrySharding 分片实体
     * @return 受影响行数
     */
    long saveRetrySharding(RetryShardingDO retrySharding);

    /**
     * 刷新实例名下所有分片的心跳时间。
     *
     * @param instanceId 实例 ID
     * @param status     分片状态
     * @return 受影响行数
     */
    int updateLastHeartbeat(String instanceId,int status);


    /**
     * 抢占心跳超时的死分片。
     *
     * @param instanceId 当前实例 ID
     * @param status     抢占后写入的状态
     * @param timeout    心跳超时秒数
     * @return 抢占成功的分片数量
     */
    int scrambleDeadSharding(String instanceId,int status,int timeout);

    /**
     * 查询实例名下全部分片。
     *
     * @param instanceId 实例 ID
     * @return 分片实体列表
     */
    List<RetryShardingDO> selectByInstanceId(String instanceId);
}
