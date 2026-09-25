package com.smart.retry.common;

import com.smart.retry.common.model.RetryTask;

/**
 * 重试任务内存调度入队接口。
 * 用于让管理端在保持模块边界的前提下复用核心调度器。
 */
public interface RetryTaskEnqueuer {

    /**
     * 在事务提交后将任务加入内存调度队列；无事务时立即入队。
     *
     * @param retryTask 已落库并回填ID的任务
     */
    void enqueueAfterCommit(RetryTask retryTask);
}
