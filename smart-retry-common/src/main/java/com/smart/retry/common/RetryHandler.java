package com.smart.retry.common;

/**
 * @Author xiaoqiang
 * @Version RetryHandler.java, v 0.1 2025年02月14日 19:05 xiaoqiang
 * @Description: AOP 方法执行处理器接口。拦截 @RetryOnMethod 方法，
 * 完成原方法调用、重试条件判断和任务注册。
 */
public interface RetryHandler {
    /**
     * 重试方法的处理
     * 1、如果方法上没有注解 有 {@link com.smart.retry.common.annotation.RetryOnMethod} ，说明不是重试的方法，则直接执行对应的方法
     * 2、如果方法有注解 {@link com.smart.retry.common.annotation.RetryOnMethod}，先执行方法，如果执行成功，不进行重试，如果抛出指定的异常
     * 再按 include/exclude 和 occurType 判断是否注册重试任务。
     *
     * @return 原方法返回值；需要重试时通常返回 null 或抛出原异常，由实现决定
     */
    Object retryHandler()throws Throwable;
}
