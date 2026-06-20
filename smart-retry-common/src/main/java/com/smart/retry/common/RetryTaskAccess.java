package com.smart.retry.common;

import com.smart.retry.common.model.RetryTask;

import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryTaskAcess.java, v 0.1 2025年02月12日 15:34 xiaoqiang
 * @Description: TODO
 */
public interface RetryTaskAccess {


    /**
     * 获取所有死任务 执行时间超过设置的最大的执行时间
     * @return
     */
    List<RetryTask> listDeadTask(int maxExecuteTime);
    /**
     * 获取所有待重试任务
     * @return
     */
    List<RetryTask> listRetryTask();

    /**
     * 获取待重试任务（支持预加载窗口和数量限制）
     * @param maxNextPlanTime 最大下次执行时间，null 则默认 now()
     * @param limit 每次拉取的最大数量
     * @return 待重试任务列表
     */
    List<RetryTask> listRetryTask(java.util.Date maxNextPlanTime, int limit);


    RetryTask getRetryTask(long taskId);

    /**
     * 保存重试任务
     * @param retryTask
     * @return
     */
    long saveRetryTask(RetryTask retryTask);

    /**
     * 更新重试任务
     * @param retryTask
     */
    void updateRetryTask(RetryTask retryTask);


    /**
     * 删除重试任务
     * @param taskId
     */
    void deleteRetryTask(long taskId);


    /**
     * 停止重试任务
     * @param taskId
     */
    void stopRetryTask(long taskId);

    /**
     * 删除历史的重试任务
     * @param clearBeforeDays 多少天之前的任务
     * @param limitRows 每次限制删除的条数
     * @return
     */

    int  deleteHistoryRetryTask(int clearBeforeDays, int limitRows);

    /**
     * 后门能力：把失败任务重置为待执行（{@code status=0}），保留 originalRetryNum 与
     * {@code currentLogId} 现场。仅当具体实现支持时生效（如 MyBatis 实现）。
     *
     * @param taskId          任务 ID
     * @param targetRetryNum  重置后剩余可重试次数
     * @param nextPlanTime    下次执行时间
     * @return 受影响行数
     * @throws UnsupportedOperationException 不支持该能力时抛出
     */
    default int restartRetryTask(long taskId, int targetRetryNum, java.util.Date nextPlanTime) {
        throw new UnsupportedOperationException("current RetryTaskAccess does not support restartRetryTask");
    }

}
