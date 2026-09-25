package com.smart.retry.core.nextPlanTimeStrategy;

import com.smart.retry.common.model.RetryTask;

import java.util.Date;

/**
 * @Author xiaoqiang
 * @Version NextTimeStrategy.java, v 0.1 2025年07月15日 19:20 xiaoqiang
 * @Description: 下次执行时间策略接口。根据任务历史和剩余重试次数计算 next_plan_time。
 */
interface NextPlanTimeStrategy {

    /**
     * 计算任务下次执行时间。
     *
     * @param retryTask 当前任务
     * @return 下次执行时间
     */
    Date nextExecuteTime(RetryTask retryTask);
}
