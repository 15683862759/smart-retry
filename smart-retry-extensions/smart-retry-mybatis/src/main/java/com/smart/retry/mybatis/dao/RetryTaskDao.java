package com.smart.retry.mybatis.dao;

import com.smart.retry.mybatis.entity.RetryTaskDO;
import com.smart.retry.mybatis.entity.query.RetryTaskQuery;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryTaskDao.java, v 0.1 2025年02月16日 20:11 xiaoqiang
 * @Description: TODO
 */
public interface RetryTaskDao {

    long insert(RetryTaskDO retryTaskDO);


    int update(RetryTaskDO retryTaskDO);


    int countByQuery(RetryTaskQuery retryTaskQuery);


    List<RetryTaskDO> selectByQuery(RetryTaskQuery retryTaskQuery);


    RetryTaskDO selectById(Long id);


    int deleteById(Long id);

    int deleteByGmtCreate(@Param("gmtCreate") Date  gmtCreate,
                          @Param("limitRows") int limitRows,
    @Param("shardingKeyList")List<Long> shardingKeyList);

    /**
     * 批量删除任务
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 把失败的任务重置为待执行（status=0），保留原 {@code current_log_id} 现场。
     *
     * @param id              任务 ID
     * @param targetRetryNum  重置后剩余可重试次数
     * @param nextPlanTime    下次执行时间
     * @return 受影响行数
     */
    int restartRetryTask(@Param("id") Long id,
                         @Param("targetRetryNum") int targetRetryNum,
                         @Param("nextPlanTime") Date nextPlanTime);
}
