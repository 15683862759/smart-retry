package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;

import java.util.Date;

/**
 * examples:指数回避策略
 * 第一次重试：间隔 = 100s
 * 第二次重试：间隔 = 100s * 2^1 = 200s
 * 第三次重试：间隔 = 100s * 2^2 = 400s
 * 第五次重试：间隔 = 100s * 2^4 = 1600s
 *
 * @Author xiaoqiang
 * @Version BackOffNextPlanTimeStrategy.java, v 0.1 2025年09月19日 11:19 xiaoqiang
 * @Description: TODO
 */
class BackOffNextPlanTimeStrategy implements NextPlanTimeStrategy {

    @Override
    public Date nextExecuteTime(RetryTask retryTask) {
        // 已重试次数（从1开始计算第几次重试）
        int attempt = retryTask.getOriginRetryNum() - retryTask.getRetryNum() + 1;

        // 指数退避：间隔 = 基础间隔 * 2^(attempt - 1)，第一次重试就是 base * 1。
        // 大重试次数时饱和到 Long.MAX_VALUE，避免位移回绕或乘法溢出得到负间隔。
        int shift = Math.max(0, attempt - 1);
        long multiplier = 1L << Math.min(shift, 62);
        long intervalMs = retryTask.getIntervalSecond() * 1000L;
        long cappedIntervalMs = Math.min(intervalMs, Long.MAX_VALUE / multiplier);
        long backoffIntervalMs = cappedIntervalMs * multiplier;

        // 下次执行时间 = 当前计划时间 + 计算出的退避间隔
        long currentPlanTime = retryTask.getNextPlanTime().getTime();
        long nextTime = currentPlanTime > Long.MAX_VALUE - backoffIntervalMs
                ? Long.MAX_VALUE
                : currentPlanTime + backoffIntervalMs;

        return new Date(nextTime);
    }
}
