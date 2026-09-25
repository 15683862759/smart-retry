package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;

import java.util.Date;

/**
 * 固定间隔，每次间隔都是一样的
 */
class FixedNextPlanTimeStrategy implements NextPlanTimeStrategy {


    @Override
    public Date nextExecuteTime(RetryTask retryTask) {

        long intervalMs = retryTask.getIntervalSecond() * 1000L;
        long currentPlanTime = retryTask.getNextPlanTime().getTime();
        long nextTime = currentPlanTime > Long.MAX_VALUE - intervalMs
                ? Long.MAX_VALUE
                : currentPlanTime + intervalMs;
        return new Date(nextTime);
    }
}
