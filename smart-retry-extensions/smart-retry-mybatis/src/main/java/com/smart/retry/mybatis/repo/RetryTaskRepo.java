package com.smart.retry.mybatis.repo;

import com.smart.retry.mybatis.entity.RetryTaskDO;
import com.smart.retry.mybatis.entity.query.RetryTaskQuery;

import java.util.Date;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryTaskRepo.java, v 0.1 2025年02月16日 21:03 xiaoqiang
 * @Description: TODO
 */
public interface RetryTaskRepo {

    long saveRetryTask(RetryTaskDO retryTask);



    int updateRetryTask(RetryTaskDO retryTask);

    RetryTaskDO getRetryTask(long id);

    List<RetryTaskDO> listAllWaitingRetryTask();

    List<RetryTaskDO> listAllWaitingRetryTask(Date maxNextPlanTime, int limit);

    List<RetryTaskDO> listAllDeadTask(Date deadTaskTime);


    int deleteRetryTask(long taskId);



    int deleteByGmtCreate(Date gmtCreate, int limitRows);

    /**
     * 把失败的任务重置为待执行（status=0），保留原 {@code current_log_id} 现场。
     *
     * @param taskId          任务 ID
     * @param targetRetryNum  重置后剩余可重试次数
     * @param nextPlanTime    下次执行时间
     * @return 受影响行数
     */
    int restartRetryTask(long taskId, int targetRetryNum, Date nextPlanTime);
}
