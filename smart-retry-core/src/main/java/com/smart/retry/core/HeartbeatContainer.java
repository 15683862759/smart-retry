package com.smart.retry.core;

import com.smart.retry.common.RetryContainer;
import com.smart.retry.common.RetryTaskHeart;
/**
 * @Author xiaoqiang
 * @Version HeartbeatContainer.java, v 0.1 2025年02月16日 10:50 xiaoqiang
 * @Description: 心跳容器。将实例初始化、心跳上报和死分片接管流程
 * 纳入 RetryContainer 统一生命周期。
 */
public class HeartbeatContainer implements RetryContainer {


    private RetryTaskHeart heart;


    /**
     * 注入具体心跳实现。
     *
     * @param heart 分片心跳 SPI 实现
     */
    public HeartbeatContainer(RetryTaskHeart heart) {
        this.heart = heart;
    }

    /**
     * 初始化分片注册并立即执行一次心跳和死分片扫描。
     */
    @Override
    public void start() {
        heart.initHeart();
        heart.heartBeat();
        heart.scrambleDeadSharding();
    }

    /**
     * 停止心跳线程和死分片扫描线程。
     */
    @Override
    public void destroy() {
        heart.stop();
    }
}
