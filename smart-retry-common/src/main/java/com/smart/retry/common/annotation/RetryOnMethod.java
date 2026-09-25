package com.smart.retry.common.annotation;

import com.smart.retry.common.IRetryCallback;
import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.constant.RetryOccurType;
import com.smart.retry.common.notify.RetryTaskNotify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author xiaoqiang
 * @Version Retryable.java, v 0.1 2025年02月12日 11:11 xiaoqiang
 * @Description: 方法级重试注解。标注在 Spring Bean 的 public 方法上，
 * 首次同步执行抛出命中异常后，框架在当前事务内注册重试任务，
 * 后续由调度器按配置策略异步重试。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)

public @interface RetryOnMethod {


    /**
     * 指定的异常会进行重试，如果不配置 所有的异常会重试
     * 不能和{exclude} 有重叠的异常类型配置
     * @return exception types to retry
     */
    Class<? extends Throwable>[] include() default {};


    /**
     * 指定重试的异常，如果不配置，所有的异常都会重试，如果配置，在配置中的异常不会重试。
     * 不能和 {include} 有重叠的异常类型配置
     * @return exception types not to retry
     */
    Class<? extends Throwable>[] exclude() default {};

    /**
     * 最大执行次数，包含首次同步调用；小于等于 1 时不创建异步重试任务
     *
     * @return 最大执行次数
     */
    int maxAttempt() default 3;


    /**
     * 第一次延迟时间，如果小于0，会先
     * 按任务创建时间立即调度；正常配置应大于等于 0。
     * @return
     */
    int firstDelaySecond() default 10;

    /**
     * 间隔时间，单位秒；必须大于 0
     * @return 下次执行的基础间隔
     */
    int intervalSecond() default 180;


    /**
     * 时间间隔策略
     * 固定间隔
     * 增长间隔
     * 斐波那契间隔
     * @return
     */
    NextPlanTimeStrategyEnum nextPlanTimeStragy() default NextPlanTimeStrategyEnum.FIXED;

    /**
     * 任务执行结束后发起回调
     * 如果是多个回调则 按照默认配置的顺序进行执行，各个回调之间不相互影响
     * @example A->B->C 三个回调，B抛出异常，不影响C的执行，回调的方法事务是独立的，回调的事务和父方法相互影响
     * @return
     */
    Class<? extends IRetryCallback>[] retryCallback() default {};


    /**
     * 首次调用的触发方式。EXCEPTION 表示命中异常时重试，
     * RESULT 表示由调用方基于返回值或业务状态主动创建任务。
     *
     * @return 重试触发类型
     */
    RetryOccurType occurType() default RetryOccurType.EXCEPTION;

    /**
     * 任务结束通知列表。每个通知独立执行，单个通知失败不影响任务状态。
     *
     * @return 通知类型数组
     */
    Class<? extends RetryTaskNotify>[] retryTaskNotifies() default {};

}
