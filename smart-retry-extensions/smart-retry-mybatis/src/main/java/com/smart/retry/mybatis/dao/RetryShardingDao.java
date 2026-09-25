package com.smart.retry.mybatis.dao;

import com.smart.retry.mybatis.entity.RetryShardingDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryShardingDao.java, v 0.1 2025年02月15日 23:56 xiaoqiang
 * @Description: 重试分片 MyBatis 数据访问接口。维护实例注册、心跳刷新、
 * 死分片抢占和分片查询，是多实例故障转移的数据基础。
 */
public interface RetryShardingDao {

    /**
     * 插入新的分片记录。
     *
     * @param retryShardingDO 分片实体，包含实例标识与初始心跳
     * @return 受影响行数
     */
    long insert(RetryShardingDO  retryShardingDO);


    /**
     * 刷新实例名下所有分片的最后心跳时间。
     *
     * @param instanceId 实例 ID
     * @param status     分片状态
     * @return 受影响行数
     */
    int updateLastHeartbeat(@Param("instanceId") String instanceId, @Param("status") int status);


    /**
     * 抢占心跳超时的死分片，并把其归属更新为当前实例。
     *
     * @param instanceId 当前实例 ID
     * @param status     抢占后写入的分片状态
     * @param timeout    心跳超时秒数
     * @return 抢占成功的分片数量
     */
    int scrambleDeadSharding(@Param("instanceId") String instanceId,
                             @Param("status") int status,@Param("timeout") int timeout);

    /**
     * 查询指定实例名下的全部分片。
     *
     * @param instanceId 实例 ID
     * @return 分片实体列表
     */
    List<RetryShardingDO> selectByInstanceId(String instanceId);

    /**
     * 管理端分页查询分片数据，可按创建实例和运行实例过滤。
     *
     * @param offset     偏移量
     * @param limit      每页数量
     * @param creatorId  创建者实例 ID，可为空
     * @param instanceId 当前归属实例 ID，可为空
     * @return 分片实体列表
     */
    List<RetryShardingDO> selectAllWithPage(@Param("offset") int offset, @Param("limit") int limit,
                                            @Param("creatorId") String creatorId,
                                            @Param("instanceId") String instanceId);

    /**
     * 管理端统计分片总数，可按创建实例和运行实例过滤。
     *
     * @param creatorId  创建者实例 ID，可为空
     * @param instanceId 当前归属实例 ID，可为空
     * @return 分片总数
     */
    long countAll(@Param("creatorId") String creatorId, @Param("instanceId") String instanceId);

    /**
     * 按主键查询单个分片。
     *
     * @param id 分片 ID
     * @return 分片实体；不存在时返回 null
     */
    RetryShardingDO selectById(Long id);
}
