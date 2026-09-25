package com.smart.retry.mybatis.entity;

import com.smart.retry.common.model.BaseEntity;

import java.util.Date;

/**
 * @Author xiaoqiang
 * @Version RetryShardingDO.java, v 0.1 2025年02月15日 23:56 xiaoqiang
 * @Description: 重试分片数据库实体。对应 retry_sharding 表，记录分片归属实例、
 * 最近心跳和状态，支撑多实例负载划分与死分片接管。
 */
public class RetryShardingDO extends BaseEntity {


    /** 创建该分片的实例 ID */
    private String creatorId;

    /** 当前拥有该分片的实例 ID */
    private String instanceId;

    /** 分片最后一次心跳时间；超时后可被其他实例抢占 */
    private Date lastHeartbeat;

    /** 分片状态：1=正常，其他值由具体部署约定 */
    private int status;

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Date getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Date lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }
}
