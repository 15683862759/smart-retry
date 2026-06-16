package com.smart.retry.common.model;

import java.util.Date;

/**
 * @Author xiaoqiang
 * @Version RetryTask.java, v 0.1 2025年02月11日 19:42 xiaoqiang
 * @Description: 任务上下文，用来在任务执行的时候传递上下文的信息
 */
public class RetryTask extends BaseEntity{

    /**
     * 任务编码
     */
    private String taskCode;

    /**
     * 重试任务的描述
     */
    private String taskDesc;





    /**
     * 执行时间间隔 秒
     */
    private Integer intervalSecond;

    /**
     * 初次创建延迟时间
     */
    private Integer delaySecond;

    /**
     * 重试次数>1 小于100
     */
    private Integer retryNum;


    private Date nextPlanTime;
    /**
     * 任务预计最大执行时间
     */
    private Integer maxExecuteTime;

    private String parameters;


    private String creator;

    private String executor;

    private Integer status;

    private Integer originRetryNum;

    private String attribute;

    private String uniqueKey;

    /**
     * 当前运行 traceId。存储格式为 {@code key::value}（如 {@code traceId::abc123}），
     * 由 {@link com.smart.retry.common.utils.LogIdUtils} 编码；
     * 空字符串或 null 表示兼容历史数据（{@code BIGINT} 旧值）。
     */
    private String currentLogId;


    private Long shardingKey;


    private int  nextPlanTimeStrategy;

    public int getNextPlanTimeStrategy() {
        return nextPlanTimeStrategy;
    }

    public void setNextPlanTimeStrategy(int nextPlanTimeStrategy) {
        this.nextPlanTimeStrategy = nextPlanTimeStrategy;
    }

    public Long getShardingKey() {
        return shardingKey;
    }

    public void setShardingKey(Long shardingKey) {
        this.shardingKey = shardingKey;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getTaskDesc() {
        return taskDesc;
    }

    public void setTaskDesc(String taskDesc) {
        this.taskDesc = taskDesc;
    }

    public Date getNextPlanTime() {
        return nextPlanTime;
    }

    public void setNextPlanTime(Date nextPlanTime) {
        this.nextPlanTime = nextPlanTime;
    }

    public Integer getIntervalSecond() {
        return intervalSecond;
    }

    public void setIntervalSecond(Integer intervalSecond) {
        this.intervalSecond = intervalSecond;
    }

    public Integer getDelaySecond() {
        return delaySecond;
    }

    public void setDelaySecond(Integer delaySecond) {
        this.delaySecond = delaySecond;
    }

    public Integer getRetryNum() {
        return retryNum;
    }

    public void setRetryNum(Integer retryNum) {
        this.retryNum = retryNum;
    }


    public Integer getMaxExecuteTime() {
        return maxExecuteTime;
    }

    public void setMaxExecuteTime(Integer maxExecuteTime) {
        this.maxExecuteTime = maxExecuteTime;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }


    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getOriginRetryNum() {
        return originRetryNum;
    }

    public void setOriginRetryNum(Integer originRetryNum) {
        this.originRetryNum = originRetryNum;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * 获取当前运行 traceId 编码，格式 {@code key::value}。
     *
     * @return 当前 traceId 编码，未设置时返回 null
     */
    public String getCurrentLogId() {
        return currentLogId;
    }

    /**
     * 设置当前运行 traceId 编码，格式 {@code key::value}。
     * 建议通过 {@link com.smart.retry.common.utils.LogIdUtils#encode(String, String)}
     * 生成，避免直接拼字符串。
     *
     * @param currentLogId 编码后的 traceId
     */
    public void setCurrentLogId(String currentLogId) {
        this.currentLogId = currentLogId;
    }
}
