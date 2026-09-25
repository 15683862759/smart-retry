package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.constant.NextPlanTimeStrategyEnum;
import com.smart.retry.common.model.RetryTask;

import java.util.Date;

/**
 * @Author xiaoqiang
 * @Version NextTimeStrategyManager.java, v 0.1 2025年07月15日 19:21 xiaoqiang
 * @Description: 下次执行时间策略分发器。根据数据库中的策略编码选择具体算法，
 * 未知编码回退为固定间隔策略。
 */
public class NextPlanTimeStrategyManager {

    /**
     * 根据任务策略编码计算下次执行时间。
     *
     * @param retryTask 当前任务
     * @return 按策略计算出的下次执行时间；未知编码回退固定间隔
     */
    public static Date nextTime(RetryTask retryTask){
        NextPlanTimeStrategyEnum retryTypeEnum = NextPlanTimeStrategyEnum.getByCode(retryTask.getNextPlanTimeStrategy());
        NextPlanTimeStrategy nextPlanTimeStrategy = null;
        if (retryTypeEnum == null) {
            nextPlanTimeStrategy = new FixedNextPlanTimeStrategy();
        } else if (retryTypeEnum.getCode() == NextPlanTimeStrategyEnum.FIXED.getCode()) {
            nextPlanTimeStrategy = new FixedNextPlanTimeStrategy();
        } else if (retryTypeEnum.getCode() == NextPlanTimeStrategyEnum.INCREMENTING.getCode()) {
            nextPlanTimeStrategy = new IncrementingNextPlanTimeStrategy();
        } else if (retryTypeEnum.getCode()==NextPlanTimeStrategyEnum.BACKOFF.getCode()) {
            nextPlanTimeStrategy = new BackOffNextPlanTimeStrategy();
        } else {
            nextPlanTimeStrategy = new FibonacciNextPlanTimeStrategy();
        }
        Date nextPlanTime = nextPlanTimeStrategy.nextExecuteTime(retryTask);
        return nextPlanTime;
    }
}
