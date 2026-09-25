package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;

import java.util.Date;

/**
 *
 * example: 斐波那契策略
 * 斐波那契数列：1,1,2,3,5,8,13,21,34,55,89
 * 第n项 = 前两项之和
 * @Author xiaoqiang
 * @Version FibonacciNextTimeStrategy.java, v 0.1 2025年07月15日 19:20 xiaoqiang
 * @Description: TODO
 */
class FibonacciNextPlanTimeStrategy implements NextPlanTimeStrategy {

    @Override
    public Date nextExecuteTime(RetryTask retryTask) {

        long retryNum = retryTask.getOriginRetryNum() - retryTask.getRetryNum() + 1;
        long fibonacciNum = fib(retryNum);
        long intervalMs = retryTask.getIntervalSecond() * 1000L;
        long cappedFibonacciNum = Math.min(fibonacciNum,
                Long.MAX_VALUE / Math.max(1L, intervalMs));
        long fibonacciIntervalMs = cappedFibonacciNum * intervalMs;
        long currentPlanTime = retryTask.getNextPlanTime().getTime();
        long nextTime = currentPlanTime > Long.MAX_VALUE - fibonacciIntervalMs
                ? Long.MAX_VALUE
                : currentPlanTime + fibonacciIntervalMs;
        return new Date(nextTime);
    }

    private long fib(long n) {
        if (n == 0L) {
            return 0L;
        } else if (n == 1L) {
            return 1L;
        } else {
            long prevPrev = 0L;
            long prev = 1L;

            for (long i = 2L; i <= n; ++i) {
                if (prev > Long.MAX_VALUE - prevPrev) {
                    return Long.MAX_VALUE;
                }
                long result = prev + prevPrev;
                prevPrev = prev;
                prev = result;
            }

            return prev;
        }
    }
}
