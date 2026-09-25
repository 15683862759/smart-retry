package com.smart.retry.core.handler;

import com.smart.retry.common.RetryCondition;
import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryHandler;
import com.smart.retry.common.annotation.RetryOnMethod;
import com.smart.retry.common.model.MethodChain;
import com.smart.retry.common.model.RetryAttemptContext;
import com.smart.retry.common.retry.IRetryer;
import com.smart.retry.core.DefaultRetryCondition;
import com.smart.retry.core.RetrySnapshot;
import com.smart.retry.core.retry.RemoteRetryer;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

/**
 * @Author xiaoqiang
 * @Version DefaultRetryHandler.java, v 0.1 2025年02月14日 19:10 xiaoqiang
 * @Description: 默认 AOP 处理器。负责执行原方法、捕获异常、维护调用链，
 * 并在满足条件时委托 RemoteRetryer 创建重试任务。
 */
public class DefaultRetryHandler implements RetryHandler {


    private MethodInvocation methodInvocation;

    private RetryConfiguration retryConfiguration;

    private RetryOnMethod retryable;

    /**
     * 创建方法重试处理器。
     *
     * @param retryConfiguration 框架配置门面
     * @param methodInvocation    当前 AOP 调用现场
     */
    public DefaultRetryHandler(RetryConfiguration retryConfiguration, MethodInvocation methodInvocation) {
        this.retryConfiguration = retryConfiguration;
        this.methodInvocation = methodInvocation;
    }

    @Override
    /**
     * AOP 入口：未标注 @RetryOnMethod 时原样放行，标注后进入重试判断流程。
     *
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    public Object retryHandler() throws Throwable {
        Method method = methodInvocation.getMethod();
        RetryOnMethod retryable = AnnotatedElementUtils.findMergedAnnotation(method, RetryOnMethod.class);
        //1、如果没有重注解，则直接执行方法
        if (retryable == null) {
            return methodInvocation.proceed();
        }
        this.retryable = retryable;
        //2、执行重试
        return retry();
    }

    /**
     * 执行原方法并维护嵌套调用链。
     *
     * @return 原方法返回值
     * @throws Throwable 满足重试条件时重新抛出的原异常
     */
    private Object retry() throws Throwable {
        MethodChain methodChainModel = new MethodChain();
        methodChainModel.setMethod(methodInvocation.getMethod());

        RetryAttemptContext retryAttemptContext = initRetryAttemptContext();
        retryAttemptContext.setCurrentMethodChain(methodChainModel);
        Object result = null;
        try {
            RetrySnapshot.setInterceptorChain(methodChainModel);
            if(TransactionSynchronizationManager.isSynchronizationActive()){
                result = ((ProxyMethodInvocation)methodInvocation).invocableClone().proceed();

            }else{
                result = methodInvocation.proceed();
            }
            retryAttemptContext.setResult(result);
        } catch (Throwable ex) {
            methodChainModel.setThrowable(ex);
            retryAttemptContext.setThrowable(ex);
            //return processRetry( ex, methodChainModel);
        } /*finally {
            RetryInterceptorSnapot.removeInterceptorChain(methodChainModel.getMethod());
        }*/

        try {
            return processRetry(retryAttemptContext);
        } finally {
            RetrySnapshot.removeInterceptorChain(methodChainModel.getMethod());
        }
    }

    /**
     * 判断重试条件，必要时注册任务并还原原调用结果。
     *
     * @param retryAttemptContext 本次执行上下文
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    private Object processRetry(RetryAttemptContext retryAttemptContext) throws Throwable {
        //1、如果是被远程调用调用发起的重试，不会进行重试操作
        RetryCondition retryCondition = new DefaultRetryCondition(retryAttemptContext);
        boolean flag = retryCondition.needRetry();
        //如果需要重试，则执行重试
        if (flag && retryable.maxAttempt() > 1) {
            doRetry(retryAttemptContext);
        }
        //如果异常不为null直接抛出异常
        if (retryAttemptContext.getThrowable() != null) {
            throw retryAttemptContext.getThrowable();
        }
        //返回结果
        return retryAttemptContext.getResult();
    }

    /**
     * 初始化重试判断的上下文信息
     *
     * @return
     */
    private RetryAttemptContext initRetryAttemptContext() {
        RetryAttemptContext retryAttemptContext = new RetryAttemptContext();
        retryAttemptContext.setMethod(methodInvocation.getMethod());
        //retryAttemptContext.setRetryOccurType(retryable.retryOccurType());
        retryAttemptContext.setExcludes(retryable.exclude());
        retryAttemptContext.setIncludes(retryable.include());
        retryAttemptContext.setRetryOccurType(retryable.occurType());
        return retryAttemptContext;
    }

    /**
     * 将满足条件的方法调用交给远程重试注册器。
     *
     * @param retryAttemptContext 本次执行上下文
     * @throws Throwable 序列化或持久化失败时抛出
     */
    private void doRetry(RetryAttemptContext retryAttemptContext) throws Throwable {
        IRetryer<Object> retryer = new RemoteRetryer(retryConfiguration,methodInvocation, retryable, retryAttemptContext);
        retryer.retry();
    }
}
