package com.smart.retry.common;

/**
 * @Author xiaoqiang
 * @Version RetryCondition.java, v 0.1 2025年02月15日 17:10 xiaoqiang
 * @Description: 重试条件 SPI。在首次同步执行完成后判断是否需要注册异步重试任务，
 * 用于支持异常匹配或基于返回值的业务化判断。
 */
public interface RetryCondition {
    /**
     * 判断能否重试
     * 实现不应修改业务状态；返回 true 时框架才会创建或推进重试任务。
     *
     * @return true 表示需要重试；false 表示不重试
     */
    boolean needRetry();
}
