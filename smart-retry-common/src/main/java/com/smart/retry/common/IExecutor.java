package com.smart.retry.common;

import com.smart.retry.common.model.RetryTask;

/**
 * @Author xiaoqiang
 * @Version IExecutor.java, v 0.1 2025年02月11日 20:53 xiaoqiang
 * @Description: 重试任务执行器接口，定义任务发布和取消两个调度入口。
 */
public interface IExecutor {

    /**
     * 任务发布
     * 由调度器周期调用，将数据库中到期任务发布到内存队列或线程池。
     */
    void publishRetry();

    /**
     * 任务取消
     * 用于停机或手动停止任务，实现应保证不会删除仍在执行的数据库状态。
     *
     * @param retryTask
     */
    void killRetry(RetryTask retryTask);




}
