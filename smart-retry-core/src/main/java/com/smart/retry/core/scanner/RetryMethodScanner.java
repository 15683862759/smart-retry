package com.smart.retry.core.scanner;

import com.google.common.collect.Maps;
import com.smart.retry.common.annotation.RetryOnMethod;
import com.smart.retry.common.constant.RetryTaskTypeEnum;
import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.model.RetryTaskObject;
import com.smart.retry.common.scanner.RetryScanner;
import com.smart.retry.core.cache.RetryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * @Author xiaoqiang
 * @Version MethodScanner.java, v 0.1 2025年02月14日 09:49 xiaoqiang
 * @Description: 方法级重试扫描器。启动时发现所有 @RetryOnMethod 方法，
 * 校验异常配置并按“类名#方法名”注册到 RetryCache。
 */
public class RetryMethodScanner implements RetryScanner {


    private static final Logger logger = LoggerFactory.getLogger(RetryMethodScanner.class);



    @Override
    /**
     * 遍历 Spring 容器，扫描并注册所有 @RetryOnMethod 方法。
     *
     * @param applicationContext Spring 应用上下文
     */
    public void scan(ApplicationContext applicationContext) {
        String[] allBeanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : allBeanNames) {
            Object bean = applicationContext.getBean(beanName);

            //1、查找标有注解@see RetryableOnMethod 的方法
            resolveMethodAnnotation(bean, applicationContext);
        }

    }



    /**
     * 解析单个 Bean 的方法注解，生成方法级任务定义。
     *
     * @param bean              Spring Bean
     * @param applicationContext Spring 上下文，用于事务代理目标对象解析
     */
    private void resolveMethodAnnotation(Object bean,ApplicationContext applicationContext) {
        Map<Method, RetryOnMethod> methodTMap = MethodIntrospector.selectMethods(bean.getClass(),
                new MethodIntrospector.MetadataLookup<RetryOnMethod>() {
                    @Override
                    public RetryOnMethod inspect(Method method) {
                        return AnnotationUtils.findAnnotation(method, RetryOnMethod.class);
                    }
                });
        if (methodTMap == null || methodTMap.isEmpty()) {
            return;
        }


        methodTMap.forEach((method, retryOnMethod) -> {
            String taskCode = method.getDeclaringClass().getName() + "#" + method.getName();
            checkExceptionConfiguration(taskCode, retryOnMethod);
            boolean hasTransactional = method.isAnnotationPresent(Transactional.class) ||
                    method.getDeclaringClass().isAnnotationPresent(Transactional.class);
            Object proxy = bean;
            if (hasTransactional) {
                Class<?> beanType = AopUtils.getTargetClass(bean); // 处理代理类获取真实类型
                proxy = applicationContext.getBean(beanType);
                logger.warn("{} has transactional, please check", taskCode);
            }

            RetryTaskObject retryTaskObject =
                    RetryTaskObject.of().withRetryCallback(retryOnMethod.retryCallback())
                            .withTaskCode(taskCode)
                            .withMethod(method)
                            .withExcludes(retryOnMethod.exclude())
                            .withBeanObj(proxy)
                            .withRetryTaskNotify(retryOnMethod.retryTaskNotifies())
                            .withParams(method.getParameters())
                            .withRetryType(RetryTaskTypeEnum.METHOD);
            RetryCache.put(taskCode, retryTaskObject);
        });
    }

    /**
     * 校验 include 和 exclude 异常集合没有交集。
     *
     * @param taskCode      任务编码，用于错误定位
     * @param retryOnMethod 方法重试配置
     * @throws RetryException 异常集合存在父子类或相同类型重叠
     */
    static void checkExceptionConfiguration(String taskCode, RetryOnMethod retryOnMethod) {
        for (Class<? extends Throwable> include : retryOnMethod.include()) {
            for (Class<? extends Throwable> exclude : retryOnMethod.exclude()) {
                if (include.equals(exclude) || include.isAssignableFrom(exclude)
                        || exclude.isAssignableFrom(include)) {
                    throw new RetryException(String.format(
                            "retry task %s include and exclude exception types overlap: include=%s, exclude=%s",
                            taskCode, include.getName(), exclude.getName()));
                }
            }
        }
    }

}
