package com.smart.retry.common;

/**
 * @Author xiaoqiang
 * @Version RetryTaskHeart.java, v 0.1 2025年02月15日 22:16 xiaoqiang
 * @Description: 分片心跳 SPI。负责实例心跳上报、死分片识别和故障转移，
 * 保证多实例部署时任务分片能够被存活实例接管。
 */
public interface RetryTaskHeart {

    /**
     * 初始化心跳
     */
    default void initHeart()     {
        // do nothing
    }

    /**
     * 心跳通知
     */
    default void heartBeat() {
        // do nothing
    }


    /**
     * 获取已经停止的分片信息
     */
    default void scrambleDeadSharding() {

    }

    /**
     * 停止心跳与死分片扫描线程。
     */
    default void stop() {
        // do nothing
    }
}
