package com.smart.retry.core.scanner;

import com.smart.retry.common.RetryListener;
import com.smart.retry.common.annotation.RetryOnClass;
import com.smart.retry.common.constant.RetryTaskTypeEnum;
import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.model.RetryTaskObject;
import com.smart.retry.common.scanner.RetryScanner;
import com.smart.retry.core.cache.RetryCache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version RetryClassScanner.java, v 0.1 2025年02月14日 17:23 xiaoqiang
 * @Description: 类级重试扫描器。启动时发现 @RetryOnClass 的 RetryListener Bean，
 * 提取 consume 方法并按 taskCode 注册到 RetryCache。
 */
public class RetryClassScanner implements RetryScanner {


    @Override
    /**
     * 扫描所有 @RetryOnClass Bean 并注册类级消费者。
     *
     * @param applicationContext Spring 应用上下文
     */
    public void scan(ApplicationContext applicationContext) {
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(RetryOnClass.class);
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            resolveClassAnnotation(bean, applicationContext);
        }
    }

    /**
     * 解析单个类级注解，提取 consume 方法并注册到任务缓存。
     *
     * @param bean              Spring Bean
     * @param applicationContext Spring 上下文，保留给代理场景扩展
     */
    private void resolveClassAnnotation(Object bean, ApplicationContext applicationContext) {
        if (!(bean instanceof RetryListener)) {
            return;
        }
        if (bean.getClass() == RetryListener.class) {
            return;
        }
        RetryOnClass retryableOnClass = AnnotationUtils.findAnnotation(bean.getClass(), RetryOnClass.class);
        if (retryableOnClass == null) {
            return;
        }
        if (StringUtils.isBlank(retryableOnClass.taskCode())) {
            throw new RetryException(String.format(
                    "retry listener %s taskCode must not be blank", bean.getClass().getName()));
        }
        RetryTaskObject retryTaskObject =
                RetryTaskObject.of().withBeanObj(bean)
                        .withRetryTaskNotify(retryableOnClass.retryTaskNotifies())
                        .withRetryType(RetryTaskTypeEnum.CLASS);
        List<Method> consumeMethods = new ArrayList<>();
        for (Method method : bean.getClass().getMethods()) {
            if (StringUtils.equals(method.getName(), "consume")
                    && !method.isBridge() && !method.isSynthetic()) {
                consumeMethods.add(method);
            }
        }
        if (consumeMethods.size() != 1) {
            throw new RetryException(String.format(
                    "retry listener %s must declare exactly one non-synthetic consume method, found %s",
                    bean.getClass().getName(), consumeMethods.size()));
        }
        retryTaskObject.withMethod(consumeMethods.get(0));
        String taskCode = retryableOnClass.taskCode();
        RetryCache.put(taskCode, retryTaskObject);
    }
}
