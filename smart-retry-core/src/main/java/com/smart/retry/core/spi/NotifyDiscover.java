package com.smart.retry.core.spi;

import com.smart.retry.common.notify.RetryTaskNotify;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version NotifySpiDiscover.java, v 0.1 2025年02月18日 16:15 xiaoqiang
 * @Description: Spring 通知发现器。容器初始化完成后收集所有 RetryTaskNotify Bean，
 * 供任务执行完成后的全局通知流程复用。
 */
public class NotifyDiscover implements ApplicationContextAware, InitializingBean {

    private ApplicationContext applicationContext;

    private static final List<RetryTaskNotify> notifyList  = new ArrayList<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 注册单个通知对象。
     *
     * @param notify Spring 容器中的通知 Bean
     */
    private static void registerNotify(RetryTaskNotify notify) {
        notifyList.add(notify);
    }

    /**
     * 获取当前容器发现的全部通知对象。
     *
     * @return 通知列表；顺序由 Spring Bean 发现顺序决定
     */
    public static List<RetryTaskNotify> getNotifyList() {
        return notifyList;
    }

    /**
     * Bean 初始化完成后扫描容器，把所有 RetryTaskNotify 实现加入静态注册表。
     *
     * @throws Exception Spring 生命周期约定的异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        Collection<RetryTaskNotify> retryTaskNotifies = applicationContext.getBeansOfType(RetryTaskNotify.class).values();
        for (RetryTaskNotify retryTaskNotify : retryTaskNotifies) {
            registerNotify(retryTaskNotify);
        }
    }





}
