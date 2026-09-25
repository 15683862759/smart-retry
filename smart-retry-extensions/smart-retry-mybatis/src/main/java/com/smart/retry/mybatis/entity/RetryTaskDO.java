package com.smart.retry.mybatis.entity;

import com.smart.retry.common.model.BaseEntity;

import java.util.Date;

/**
 * @Author xiaoqiang
 * @Version RetryTaskDO.java, v 0.1 2025年02月15日 21:19 xiaoqiang
 * @Description: 重试任务数据库实体。对应 retry_task 表，承载任务定位、执行参数、
 * 分片归属、执行状态、下次执行时间和唯一去重键。
 */
public class RetryTaskDO extends BaseEntity {

    /**
     * 重试任务的描述
     */
    private String taskDesc;

    /**
     * 重试任务的bean 名称
     */
    private String taskCode;


    /**
     * 参数
     */
    private String parameters;


    /** 当前归属的分片 ID，多实例按该字段划分任务处理范围 */
    private long shardingKey;

    /** 最近一次执行结果或异常摘要，用于页面展示和问题定位 */
    private String attribute;

    /**
     *
     * 任务状态 '最终执行状态 0:待执行,1:执行中,-1:执行失败,2:执行成功'
     */
    private int status;

    /** 每次重试的基础间隔秒数 */
    private int intervalSecond;

    /** 首次执行前的延迟秒数 */
    private int delaySecond;

    /** 下一次允许执行的时间 */
    private Date nextPlanTime;

    /** 剩余可重试次数；认领成功后先扣减再执行 */
    private int retryNum;



    /** 创建时配置的原始重试次数，用于管理端展示和重置 */
    private int originRetryNum;

    /** 创建任务的实例 IP */
    private String creator;

    /** 当前执行租约 token；终态写入时用于 CAS 校验 */
    private String executor;

    /**
     * 当前 traceId 编码，格式 {@code key::value}；兼容历史的 {@code BIGINT} 数据。
     */
    private String currentLogId;

    /** 业务幂等键，与 taskCode 组合用于任务去重 */
    private String uniqueKey;

    /** 下次执行时间策略编码 @see com.smart.retry.common.constant.NextPlanTimeStrategyEnum */
    private int nextPlanTimeStrategy;

    public int getNextPlanTimeStrategy() {
        return nextPlanTimeStrategy;
    }

    public void setNextPlanTimeStrategy(int nextPlanTimeStrategy) {
        this.nextPlanTimeStrategy = nextPlanTimeStrategy;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    public String getTaskDesc() {
        return taskDesc;
    }

    public void setTaskDesc(String taskDesc) {
        this.taskDesc = taskDesc;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public long getShardingKey() {
        return shardingKey;
    }

    public void setShardingKey(long shardingKey) {
        this.shardingKey = shardingKey;
    }


    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getIntervalSecond() {
        return intervalSecond;
    }

    public void setIntervalSecond(int intervalSecond) {
        this.intervalSecond = intervalSecond;
    }

    public int getDelaySecond() {
        return delaySecond;
    }

    public void setDelaySecond(int delaySecond) {
        this.delaySecond = delaySecond;
    }

    public Date getNextPlanTime() {
        return nextPlanTime;
    }

    public void setNextPlanTime(Date nextPlanTime) {
        this.nextPlanTime = nextPlanTime;
    }

    public int getRetryNum() {
        return retryNum;
    }

    public void setRetryNum(int retryNum) {
        this.retryNum = retryNum;
    }


    public int getOriginRetryNum() {
        return originRetryNum;
    }

    public void setOriginRetryNum(int originRetryNum) {
        this.originRetryNum = originRetryNum;
    }


    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }


    public String getCurrentLogId() {
        return currentLogId;
    }

    public void setCurrentLogId(String currentLogId) {
        this.currentLogId = currentLogId;
    }
}
