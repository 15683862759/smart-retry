package com.smart.retry.core.context;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @Author xiaoqiang
 * @Version SmartContext.java, v 0.1 2025年11月17日 17:09 xiaoqiang
 * @Description: Spring 上下文持有器。用于非 Spring 注入路径获取 ApplicationContext，
 * 便于扫描器和扩展模块访问容器中的 Bean。
 */
public class SmartContext implements ApplicationContextAware {

    private static ApplicationContext applicationContext;



    @Override
    /**
     * 保存 Spring 应用上下文。
     *
     * @param applicationContext 容器上下文
     */
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取已保存的应用上下文。
     *
     * @return Spring 容器；未初始化时为 null
     */
    public  ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
