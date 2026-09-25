package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class IncrementingNextPlanTimeStrategyTest {

    @Test
    public void testLargeRetryCountDoesNotOverflowIncrementingInterval() {
        IncrementingNextPlanTimeStrategy strategy = new IncrementingNextPlanTimeStrategy();
        Date currentPlanTime = new Date(1_000_000L);

        RetryTask retryTask = new RetryTask();
        retryTask.setIntervalSecond(1);
        retryTask.setOriginRetryNum(Integer.MAX_VALUE);
        retryTask.setRetryNum(1);
        retryTask.setNextPlanTime(currentPlanTime);

        Date nextPlanTime = strategy.nextExecuteTime(retryTask);

        Assertions.assertTrue(nextPlanTime.getTime() > currentPlanTime.getTime(),
                "线性递增不能因重试次数溢出得到更早的执行时间");
    }
}
