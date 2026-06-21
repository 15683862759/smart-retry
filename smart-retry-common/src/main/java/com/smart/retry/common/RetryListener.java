package com.smart.retry.common;

import com.smart.retry.common.constant.ExecuteResultStatus;
import com.smart.retry.common.model.RetryTask;

/**
 * @Author xiaoqiang
 * @Version RetryListener.java, v 0.1 2025年02月14日 10:30 xiaoqiang
 * @Description: TODO
 */
public interface RetryListener<T> {


    /**
     * 消费
     * 返回或者SUCCESS或者null表示消费成功，
     * 抛异常或者FAIL表示消费失败
     *
     * @param param
     * @return
     */
    ExecuteResultStatus consume(T param);

    default void beforeConsume(RetryTask retryTask, T context) {

    }

    default void afterConsume(RetryTask retryTask, ExecuteResultStatus consumeStatus, T param) {

    }
}
