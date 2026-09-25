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
 * @Description: 斐波那契退避策略。按斐波那契数列扩大失败后的执行间隔，
 * 同时限制数列值和乘法结果，避免大次数重试时发生溢出。
 */
class FibonacciNextPlanTimeStrategy implements NextPlanTimeStrategy {

    @Override
    /**
     * 计算斐波那契退避后的下次执行时间。
     *
     * @param retryTask 当前任务，需要包含原始次数、剩余次数、间隔和当前计划时间
     * @return 下次执行时间；计算溢出时封顶
     */
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

    /**
     * 迭代计算第 n 个斐波那契数，超过 Long 范围时返回最大值。
     *
     * @param n 序号，从 0 开始
     * @return 第 n 个斐波那契数
     */
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
