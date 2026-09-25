package com.smart.retry.mybatis.entity.query;

import java.util.Date;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryTaskQuery.java, v 0.1 2025年02月15日 21:19 xiaoqiang
 * @Description: 重试任务查询条件对象。供 MyBatis 动态 SQL 组合 ID、任务编码、状态、
 * 分片、重试次数、执行时间和分页条件。
 */
public class RetryTaskQuery {


    /** 任务 ID 精确匹配 */
    private Long id;

    /** 任务 ID 集合匹配 */
    private List<Long> idList;
    /** 任务编码精确匹配 */
    private String taskCode;

    /** 单个任务状态精确匹配 */
    private Integer status;

    /** 多个任务状态匹配 */
    private List<Integer> statusList;

    /** 分片 ID 集合匹配，用于限定实例处理范围 */
    private List<Long> shardingKeyList;


    /** 创建实例精确匹配 */
    private String creator;

    private Integer intervalSecond;

    private Integer delayTime;

    public Integer getIntervalSecond() {
        return intervalSecond;
    }

    public void setIntervalSecond(Integer intervalSecond) {
        this.intervalSecond = intervalSecond;
    }

    public Integer getDelayTime() {
        return delayTime;
    }

    public void setDelayTime(Integer delayTime) {
        this.delayTime = delayTime;
    }

    /** 剩余重试次数精确匹配 */
    private Integer retryNum;

    /** 下次执行时间下界 */
    private Date minNextPlanTime;

    /** 下次执行时间上界 */
    private Date maxNextPlanTime;

    /** 剩余重试次数下界 */
    private Integer minRetryNum;

    /** 剩余重试次数上界 */
    private Integer maxRetryNum;

    /** 原始重试次数精确匹配 */
    private Integer originRetryNum;

    /** 原始重试次数下界 */
    private Integer minOriginRetryNum;
    /** 原始重试次数上界 */
    private Integer maxOriginRetryNum;

    /** 执行租约 token 精确匹配 */
    private String executor;

    /** 业务幂等键精确匹配 */
    private String uniqueKey;

    /** 下次执行时间精确匹配 */
    private Date nextPlanTime;

    /** 死信判定时间点，通常用于 gmt_modified 小于该值 */
    private Date deadTaskTime;

    public Date getDeadTaskTime() {
        return deadTaskTime;
    }

    public void setDeadTaskTime(Date deadTaskTime) {
        this.deadTaskTime = deadTaskTime;
    }

    public Date getNextPlanTime() {
        return nextPlanTime;
    }

    public void setNextPlanTime(Date nextPlanTime) {
        this.nextPlanTime = nextPlanTime;
    }

    /** 每页查询数量，默认 100 */
    private int limit = 100;
    /** 查询偏移量，默认 0 */
    private int offset = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public List<Long> getIdList() {
        return idList;
    }

    public void setIdList(List<Long> idList) {
        this.idList = idList;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<Integer> getStatusList() {
        return statusList;
    }

    public void setStatusList(List<Integer> statusList) {
        this.statusList = statusList;
    }

    public List<Long> getShardingKeyList() {
        return shardingKeyList;
    }

    public void setShardingKeyList(List<Long> shardingKeyList) {
        this.shardingKeyList = shardingKeyList;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Integer getRetryNum() {
        return retryNum;
    }

    public void setRetryNum(Integer retryNum) {
        this.retryNum = retryNum;
    }

    public Date getMinNextPlanTime() {
        return minNextPlanTime;
    }

    public void setMinNextPlanTime(Date minNextPlanTime) {
        this.minNextPlanTime = minNextPlanTime;
    }

    public Date getMaxNextPlanTime() {
        return maxNextPlanTime;
    }

    public void setMaxNextPlanTime(Date maxNextPlanTime) {
        this.maxNextPlanTime = maxNextPlanTime;
    }

    public Integer getMinRetryNum() {
        return minRetryNum;
    }

    public void setMinRetryNum(Integer minRetryNum) {
        this.minRetryNum = minRetryNum;
    }

    public Integer getMaxRetryNum() {
        return maxRetryNum;
    }

    public void setMaxRetryNum(Integer maxRetryNum) {
        this.maxRetryNum = maxRetryNum;
    }

    public Integer getOriginRetryNum() {
        return originRetryNum;
    }

    public void setOriginRetryNum(Integer originRetryNum) {
        this.originRetryNum = originRetryNum;
    }

    public Integer getMinOriginRetryNum() {
        return minOriginRetryNum;
    }

    public void setMinOriginRetryNum(Integer minOriginRetryNum) {
        this.minOriginRetryNum = minOriginRetryNum;
    }

    public Integer getMaxOriginRetryNum() {
        return maxOriginRetryNum;
    }

    public void setMaxOriginRetryNum(Integer maxOriginRetryNum) {
        this.maxOriginRetryNum = maxOriginRetryNum;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }
}
