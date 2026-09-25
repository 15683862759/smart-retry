package com.smart.retry.common;

import java.io.Serializable;

/**
 * @Author xiaoqiang
 * @Version RetryContainer.java, v 0.1 2025年02月13日 18:36 xiaoqiang
 * @Description: 重试容器 SPI。负责调度线程、任务队列和心跳资源的启动与销毁，
 * 是应用生命周期和框架调度生命周期的边界。
 */
public interface RetryContainer extends RetryLifecycle {
}
