package com.smart.retry.common.model;

import java.util.Date;

/**
 * @Author xiaoqiang
 * @Version BaseEntity.java, v 0.1 2025年02月11日 17:22 xiaoqiang
 * @Description: 持久化模型公共基类，统一任务、日志等表的主键与审计时间字段。
 */

public class BaseEntity implements java.io.Serializable {
    private static final long serialVersionUID = 1L;


    private Long id;

    private Date gmtCreate;

    private Date gmtModified;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(Date gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    public Date getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }
}
