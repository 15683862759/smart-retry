package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class FixedNextPlanTimeStrategyTest {

    @Test
    public void testLargeIntervalDoesNotOverflowFixedInterval() {
        FixedNextPlanTimeStrategy strategy = new FixedNextPlanTimeStrategy();
        Date currentPlanTime = new Date(1_000_000L);

        RetryTask retryTask = new RetryTask();
        retryTask.setIntervalSecond(Integer.MAX_VALUE);
        retryTask.setNextPlanTime(currentPlanTime);

        Date nextPlanTime = strategy.nextExecuteTime(retryTask);

        Assertions.assertTrue(nextPlanTime.getTime() > currentPlanTime.getTime(),
                "固定间隔不能因秒转毫秒溢出得到更早的执行时间");
    }
}
