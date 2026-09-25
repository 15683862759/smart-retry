package com.smart.retry.common;

/**
 * @Author xiaoqiang
 * @Version RetryLifecycle.java, v 0.1 2025年02月13日 18:35 xiaoqiang
 * @Description: 框架生命周期 SPI，由 Spring 生命周期回调触发。
 */
public interface RetryLifecycle {

    /**
     * 启动调度器、队列和心跳等后台资源。
     */
    void start();

    /**
     * 停止后台线程并释放队列等资源，应尽量保证已认领任务状态可恢复。
     */
    void destroy();
}
