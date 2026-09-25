package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class FibonacciNextPlanTimeStrategyTest {

    @Test
    public void testLargeRetryCountDoesNotOverflowFibonacciInterval() {
        FibonacciNextPlanTimeStrategy strategy = new FibonacciNextPlanTimeStrategy();
        Date currentPlanTime = new Date(1_000_000L);

        RetryTask retryTask = new RetryTask();
        retryTask.setIntervalSecond(1);
        retryTask.setOriginRetryNum(100);
        retryTask.setRetryNum(1);
        retryTask.setNextPlanTime(currentPlanTime);

        Date nextPlanTime = strategy.nextExecuteTime(retryTask);

        Assertions.assertTrue(nextPlanTime.getTime() > currentPlanTime.getTime(),
                "斐波那契退避不能因溢出得到更早的执行时间");
    }
}
