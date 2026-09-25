package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class BackOffNextPlanTimeStrategyTest {

    @Test
    public void testLargeRetryCountDoesNotOverflowBackoffInterval() {
        BackOffNextPlanTimeStrategy strategy = new BackOffNextPlanTimeStrategy();
        Date currentPlanTime = new Date(1_000_000L);

        RetryTask retryTask = new RetryTask();
        retryTask.setIntervalSecond(1);
        retryTask.setOriginRetryNum(61);
        retryTask.setRetryNum(1);
        retryTask.setNextPlanTime(currentPlanTime);

        Date nextPlanTime = strategy.nextExecuteTime(retryTask);

        Assertions.assertTrue(nextPlanTime.getTime() > currentPlanTime.getTime(),
                "指数退避不能因溢出得到负时间");
    }
}
