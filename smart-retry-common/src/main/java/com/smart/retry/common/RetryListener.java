package com.smart.retry.common;

import com.smart.retry.common.constant.ExecuteResultStatus;
import com.smart.retry.common.model.RetryTask;

/**
 * @Author xiaoqiang
 * @Version RetryListener.java, v 0.1 2025年02月14日 10:30 xiaoqiang
 * @Description: 类级重试消费者接口。实现类配合 @RetryOnClass 注册，
 * 参数从任务记录反序列化后交给 consume 执行。
 */
public interface RetryListener<T> {


    /**
     * 消费
     * 返回或者SUCCESS或者null表示消费成功，
     * 抛异常或者FAIL表示消费失败
     *
     * @param param
     * @return 执行状态；返回 SUCCESS 表示成功，返回 FAIL 表示本次失败
     */
    ExecuteResultStatus consume(T param);

    /**
     * 消费前置钩子。默认空实现，可用于初始化上下文或记录任务开始信息。
     *
     * @param retryTask 当前任务元数据
     * @param context   反序列化后的业务参数
     */
    default void beforeConsume(RetryTask retryTask, T context) {

    }

    /**
     * 消费后置钩子。默认空实现，可在成功或失败后记录业务指标。
     *
     * @param retryTask     当前任务元数据
     * @param consumeStatus consume 返回或异常转换后的执行状态
     * @param param         反序列化后的业务参数
     */
    default void afterConsume(RetryTask retryTask, ExecuteResultStatus consumeStatus, T param) {

    }
}
