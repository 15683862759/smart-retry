package com.smart.retry.core.interceptor;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryHandler;
import com.smart.retry.core.handler.DefaultRetryHandler;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.IntroductionInterceptor;

/**
 * @Author xiaoqiang
 * @Version RetryTaskInterceptor.java, v 0.1 2025年02月14日 19:04 xiaoqiang
 * @Description: @RetryOnMethod 方法拦截器。把 AOP 调用统一交给
 * DefaultRetryHandler，是 Spring AOP 和重试决策逻辑的适配层。
 */
public class RetryTaskInterceptor implements IntroductionInterceptor {

    private RetryConfiguration retryConfiguration;

    /**
     * 创建方法拦截器。
     *
     * @param retryConfiguration 框架配置门面
     */
    public RetryTaskInterceptor(RetryConfiguration retryConfiguration) {
        this.retryConfiguration = retryConfiguration;
    }

    @Override
    /**
     * 拦截 @RetryOnMethod 方法并交给默认处理器。
     *
     * @param methodInvocation 当前调用
     * @return 原方法返回值
     * @throws Throwable 原方法或重试注册过程中的异常
     */
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        RetryHandler retryHandler = new DefaultRetryHandler(retryConfiguration,methodInvocation);
        return retryHandler.retryHandler();

        //RetryConfiguration retryConfiguration = methodInvocation.getThis().getClass().getAnnotation(RetryConfiguration.class);
    }

    @Override
    public boolean implementsInterface(Class<?> aClass) {
        return false;
    }
}
