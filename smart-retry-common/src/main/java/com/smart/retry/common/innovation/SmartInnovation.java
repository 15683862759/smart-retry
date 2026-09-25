package com.smart.retry.common.innovation;

/**
 * @Author xiaoqiang
 * @Version SmartMethodInnovation.java, v 0.1 2025年02月18日 13:18 xiaoqiang
 * @Description: 任务执行抽象接口。调度器认领任务后通过该接口回调具体执行器，
 * 由实现决定反射调用监听器还是业务方法。
 */
public interface SmartInnovation {

    /**
     * 执行一次重试任务。
     *
     * @return 目标方法或监听器的返回值；监听器模式通常返回执行状态
     * @throws Throwable 目标方法抛出的原始异常，由调用方负责转换任务状态
     */
    Object invoke() throws Throwable;
}
