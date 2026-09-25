package com.smart.retry.mybatis.dao;

import com.smart.retry.mybatis.entity.RetryTaskDO;
import com.smart.retry.mybatis.entity.query.RetryTaskQuery;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryTaskDao.java, v 0.1 2025年02月16日 20:11 xiaoqiang
 * @Description: 重试任务 MyBatis 数据访问接口。封装任务写入、查询、历史清理，以及
 * 带乐观锁守卫的认领、终态写入、死信复活、停止和重启操作。
 */
public interface RetryTaskDao {

    /**
     * 插入一条重试任务。
     *
     * @param retryTaskDO 任务实体，插入成功后通常会回填数据库生成的主键
     * @return 受影响行数
     */
    long insert(RetryTaskDO retryTaskDO);


    /**
     * 按主键更新任务字段。该语句本身不带执行租约守卫，
     * 仅供管理端等非并发路径使用；执行链路必须使用带 CAS 守卫的方法。
     *
     * @param retryTaskDO 任务实体，必须携带 id
     * @return 受影响行数
     */
    int update(RetryTaskDO retryTaskDO);

    /**
     * 原子认领任务（乐观锁 CAS）。
     * 单参数，XML 直接引用 {@code #{id}} / {@code #{executor}} / {@code #{nextPlanTime}} / {@code #{shardingKey}}。
     *
     * @param retryTaskDO 仅需设置 id / executor / nextPlanTime / shardingKey 四个字段
     * @return 受影响行数：1=认领成功，0=已被他人认领/状态不满足
     */
    int claimTask(RetryTaskDO retryTaskDO);

    /**
     * 条件化写入终态（乐观锁 CAS 守卫）。
     * 单参数，XML 引用 {@code #{id}} / {@code #{status}} / {@code #{executor}} / {@code #{retryNum}} / {@code #{nextPlanTime}} / {@code #{attribute}}。
     * 守卫：DB executor == 传入值 且 retry_num == 传入值。
     *
     * @param retryTaskDO 需设置 id / status / executor / retryNum(扣减后) / nextPlanTime / attribute
     * @return 受影响行数：1=写入成功，0=租约已失效/状态漂移
     */
    int markTerminal(RetryTaskDO retryTaskDO);

    /**
     * 条件化标记"未注册 taskCode"任务为失败（乐观锁 CAS 守卫）。
     * 单参数，XML 引用 {@code #{id}} / {@code #{executor}} / {@code #{retryNum}} / {@code #{attribute}}。
     * 守卫：status IN (0,3) 且 retry_num == 传入值（扣减前）。
     *
     * @param retryTaskDO 需设置 id / executor / retryNum(扣减前) / attribute
     * @return 受影响行数：1=写入成功，0=任务已被认领/状态已变化
     */
    int markNullTaskObjectFail(RetryTaskDO retryTaskDO);

    /**
     * 条件化复活死信任务（乐观锁 CAS 守卫）。
     * 守卫：status = 1 且 gmt_modified < #{deadTaskTime}。
     *
     * @param id           任务 ID
     * @param deadTaskTime 超时判定时间点
     * @return 受影响行数：1=复活成功，0=任务已终态/已被其他实例复活
     */
    int reviveTask(@Param("id") Long id, @Param("deadTaskTime") Date deadTaskTime);


    /**
     * 按查询条件统计任务数量。
     *
     * @param retryTaskQuery 查询条件
     * @return 满足条件的任务数
     */
    int countByQuery(RetryTaskQuery retryTaskQuery);


    /**
     * 按查询条件分页查询任务列表。
     *
     * @param retryTaskQuery 查询条件，包含分页与排序所需字段
     * @return 任务实体列表
     */
    List<RetryTaskDO> selectByQuery(RetryTaskQuery retryTaskQuery);


    /**
     * 按主键查询任务。
     *
     * @param id 任务 ID
     * @return 任务实体；不存在时返回 null
     */
    RetryTaskDO selectById(Long id);


    /**
     * 按主键删除任务。
     *
     * @param id 任务 ID
     * @return 受影响行数
     */
    int deleteById(Long id);

    /**
     * 分批删除指定创建时间之前、指定状态且属于当前分片的任务。
     *
     * @param gmtCreate       创建时间上界
     * @param limitRows       单批删除上限，用于控制锁范围与事务大小
     * @param shardingKeyList 当前实例负责的分片
     * @param status          只允许清理的最终状态
     * @return 本批受影响行数
     */
    int deleteByGmtCreate(@Param("gmtCreate") Date  gmtCreate,
                          @Param("limitRows") int limitRows,
                          @Param("shardingKeyList") List<Long> shardingKeyList,
                          @Param("status") int status);

    /**
     * 批量删除指定 ID 的任务。
     *
     * @param ids 任务 ID 列表
     * @return 受影响行数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 把失败的任务重置为待执行（status=0），保留原 {@code current_log_id} 现场。
     *
     * @param id              任务 ID
     * @param targetRetryNum  重置后剩余可重试次数
     * @param nextPlanTime    下次执行时间
     * @return 受影响行数
     */
    int restartRetryTask(@Param("id") Long id,
                         @Param("targetRetryNum") int targetRetryNum,
                         @Param("nextPlanTime") Date nextPlanTime);

    /**
     * 原子停止任务：仅 WAITING/RUNNING/FAIL 可停止，成功后清空执行租约并耗尽重试次数。
     *
     * @param id 任务 ID
     * @return 受影响行数
     */
    int stopTask(@Param("id") Long id);
}
