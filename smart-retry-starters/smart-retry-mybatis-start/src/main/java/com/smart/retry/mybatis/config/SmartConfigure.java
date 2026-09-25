package com.smart.retry.mybatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author xiaoqiang
 * @Version SmartConfigure.java, v 0.1 2025年02月21日 11:30 xiaoqiang
 * @Description: MyBatis Starter 配置属性。绑定 spring.smart-retry.mybatis 前缀，
 * 用于指定 Spring 容器中参与重试持久化的数据源 Bean。
 */
@ConfigurationProperties(prefix = "spring.smart-retry.mybatis")
public class SmartConfigure {


    private String datasource = "dataSource";



    /**
     * 获取重试框架使用的数据源 Bean 名称。
     *
     * @return 数据源 Bean 名称，默认 dataSource
     */
    public String getDatasource() {
        return datasource;
    }

    /**
     * 设置重试框架使用的数据源 Bean 名称。
     *
     * @param datasource 数据源 Bean 名称
     */
    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }
}
