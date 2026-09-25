package com.smart.retry.common.retry;

/**
 * @Author xiaoqiang
 * @Version IRetryer.java, v 0.1 2025年02月12日 16:27 xiaoqiang
 * @Description: 重试执行接口。由具体实现完成一次任务执行、
 * 状态更新和后续调度安排。
 */
public interface IRetryer<R> {

    R retry()throws Throwable;
}
