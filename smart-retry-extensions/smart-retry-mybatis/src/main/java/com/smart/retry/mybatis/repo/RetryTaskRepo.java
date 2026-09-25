package com.smart.retry.mybatis.repo;

import com.smart.retry.mybatis.entity.RetryTaskDO;
import com.smart.retry.mybatis.entity.query.RetryTaskQuery;

import java.util.Date;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryTaskRepo.java, v 0.1 2025年02月16日 21:03 xiaoqiang
 * @Description: 重试任务仓库接口。隔离核心引擎与 MyBatis 实现，提供任务持久化、
 * 原子认领、终态写入、死信复活、停止重启和分批清理能力。
 */
public interface RetryTaskRepo {

    /**
     * 保存重试任务，并在活跃任务已经存在时跳过重复插入。
     *
     * @param retryTask 待保存任务
     * @return 新任务 ID；重复任务返回 -1
     */
    long saveRetryTask(RetryTaskDO retryTask);



    /**
     * 按主键更新任务。适合管理端修改；执行链路必须使用 CAS 方法。
     *
     * @param retryTask 待更新任务
     * @return 受影响行数
     */
    int updateRetryTask(RetryTaskDO retryTask);

    /**
     * 原子认领任务（乐观锁 CAS）。
     *
     * <p><b>注意：与 {@link #updateRetryTask}（先 selectById 再 update）不同，
     * 本方法必须直接透传单条原子 UPDATE，禁止先查后改</b>，否则会在
     * "读取状态 -> 更新"之间重新引入 TOCTOU 竞态窗口，导致业务方法被重复执行。
     *
     * @param retryTask 仅需设置 id / executor / nextPlanTime / shardingKey 四个字段
     * @return 受影响行数：1=认领成功，0=已被他人认领/状态不满足
     */
    int claimRetryTask(RetryTaskDO retryTask);

    /**
     * 条件化写入终态（乐观锁 CAS 守卫）。
     *
     * <p><b>注意：与 {@link #updateRetryTask}（先 selectById 再 update）不同，
     * 本方法必须直接透传单条原子 UPDATE，禁止先查后改</b>，否则会重新引入 TOCTOU 竞态。
     * 守卫：DB {@code executor} == 传入值 且 {@code retry_num} == 传入值（认领后扣减值）。
     *
     * @param retryTask 需设置 id / status / executor / retryNum(扣减后) / nextPlanTime / attribute
     * @return 受影响行数：1=写入成功，0=租约已失效/状态漂移
     */
    int markRetryTaskTerminal(RetryTaskDO retryTask);

    /**
     * 条件化标记"未注册 taskCode"任务为失败（乐观锁 CAS 守卫）。
     *
     * <p>单条原子 UPDATE，禁止先查后改。守卫：status IN (0,3) 且 retry_num == 传入值（扣减前）。
     *
     * @param retryTask 需设置 id / executor / retryNum(扣减前) / attribute
     * @return 受影响行数：1=写入成功，0=任务已被认领/状态已变化
     */
    int markNullTaskObjectFail(RetryTaskDO retryTask);

    /**
     * 续期执行租约。
     *
     * @param id       任务 ID
     * @param executor 当前执行租约
     * @return 受影响行数：1=续约成功，0=任务已终态或租约失效
     */
    int renewExecutionLease(Long id, String executor);

    /**
     * 条件化复活死信任务（乐观锁 CAS 守卫）。
     *
     * <p>单条原子 UPDATE，禁止先查后改。守卫：status = 1 且 gmt_modified < deadTaskTime。
     *
     * @param id           任务 ID
     * @param deadTaskTime 超时判定时间点
     * @return 受影响行数：1=复活成功，0=任务已终态/已被其他实例复活
     */
    int reviveDeadRetryTask(Long id, Date deadTaskTime);

    /**
     * 按主键查询任务。
     *
     * @param id 任务 ID
     * @return 任务实体；不存在时返回 null
     */
    RetryTaskDO getRetryTask(long id);

    /**
     * 查询当前实例分片中所有可执行任务。
     *
     * @return 可执行任务列表
     */
    List<RetryTaskDO> listAllWaitingRetryTask();

    /**
     * 查询预加载窗口内到期的可执行任务。
     *
     * @param maxNextPlanTime 执行时间上界；为空时按当前时间处理
     * @param limit           查询上限
     * @return 可执行任务列表
     */
    List<RetryTaskDO> listAllWaitingRetryTask(Date maxNextPlanTime, int limit);

    /**
     * 查询运行时间超过阈值且疑似死信的任务。
     *
     * @param deadTaskTime 死信判定时间点
     * @return 疑似死信任务列表
     */
    List<RetryTaskDO> listAllDeadTask(Date deadTaskTime);


    /**
     * 删除任务。
     *
     * @param taskId 任务 ID
     * @return 受影响行数
     */
    int deleteRetryTask(long taskId);



    /**
     * 把失败的任务重置为待执行（status=0），保留原 {@code current_log_id} 现场。
     *
     * @param taskId          任务 ID
     * @param targetRetryNum  重置后剩余可重试次数
     * @param nextPlanTime    下次执行时间
     * @return 受影响行数
     */
    int restartRetryTask(long taskId, int targetRetryNum, Date nextPlanTime);

    /**
     * 原子停止重试任务。
     *
     * @param taskId 任务 ID
     * @return 受影响行数：1=停止成功，0=任务已终态或不存在
     */
    int stopRetryTask(long taskId);

    /**
     * 分批删除指定时间前、指定状态的历史任务，直到清空本轮可删数据。
     *
     * @param gmtCreate 创建时间上界
     * @param limitRows 单批删除上限
     * @param status    只允许清理的最终状态
     * @return 实际删除总数
     */
    int deleteByGmtCreate(Date gmtCreate, int limitRows, int status);
}
