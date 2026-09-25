package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;

import java.util.Date;

/**
 *
 */
class IncrementingNextPlanTimeStrategy implements NextPlanTimeStrategy {
    @Override
    public Date nextExecuteTime(RetryTask retryTask) {
        long attempt = retryTask.getOriginRetryNum() - retryTask.getRetryNum() + 1L;
        long intervalMs = retryTask.getIntervalSecond() * 1000L;
        long cappedAttempt = Math.min(attempt,
                Long.MAX_VALUE / Math.max(1L, intervalMs));
        long incrementingIntervalMs = cappedAttempt * intervalMs;
        long currentPlanTime = retryTask.getNextPlanTime().getTime();
        long nextTime = currentPlanTime > Long.MAX_VALUE - incrementingIntervalMs
                ? Long.MAX_VALUE
                : currentPlanTime + incrementingIntervalMs;
        return new Date(nextTime);
    }
}
