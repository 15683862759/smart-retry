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
 * @Description: 指数退避策略。按已执行次数计算 2 的幂次倍率，
 * 并在倍率和时间相加前做饱和处理，避免大重试次数导致溢出。
 */
class BackOffNextPlanTimeStrategy implements NextPlanTimeStrategy {

    @Override
    /**
     * 计算指数退避后的下次执行时间。
     *
     * @param retryTask 当前任务，需要包含原始次数、剩余次数、间隔和当前计划时间
     * @return 下次执行时间；溢出时封顶为 Date 能表达的最大毫秒值
     */
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
