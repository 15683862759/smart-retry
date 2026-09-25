package com.smart.retry.core.scanner;

import com.smart.retry.common.annotation.RetryOnMethod;
import com.smart.retry.common.constant.RetryTaskTypeEnum;
import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.model.RetryTaskObject;
import com.smart.retry.common.scanner.RetryScanner;
import com.smart.retry.core.cache.RetryCache;
import com.smart.retry.core.util.RetryTaskCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
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
        if (!(applicationContext instanceof ConfigurableApplicationContext)) {
            scanAllBeans(applicationContext);
            return;
        }

        ConfigurableListableBeanFactory beanFactory =
                ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            if (beanFactory.getBeanDefinition(beanName).isAbstract()) {
                continue;
            }
            // Bean Definition 的声明类型不受 BeanPostProcessor 生成的 JDK 代理影响。
            Class<?> beanType = beanFactory.getBeanDefinition(beanName).getResolvableType().resolve();
            if (beanType == null) {
                beanType = beanFactory.getType(beanName, false);
            }
            if (beanType == null || findRetryMethods(beanType).isEmpty()) {
                continue;
            }

            // 只有包含重试方法的 Bean 才实例化，避免提前创建无关 lazy Bean。
            resolveMethodAnnotation(applicationContext.getBean(beanName), applicationContext);
        }
    }

    /**
     * 兼容不可配置上下文的兜底扫描路径。
     *
     * @param applicationContext Spring 应用上下文
     */
    private void scanAllBeans(ApplicationContext applicationContext) {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            resolveMethodAnnotation(applicationContext.getBean(beanName), applicationContext);
        }
    }

    /**
     * 查找类型中标注 {@link RetryOnMethod} 的方法。
     *
     * @param beanType Bean 的目标类型
     * @return 方法到注解的映射
     */
    private Map<Method, RetryOnMethod> findRetryMethods(Class<?> beanType) {
        return MethodIntrospector.selectMethods(beanType,
                new MethodIntrospector.MetadataLookup<RetryOnMethod>() {
                    @Override
                    public RetryOnMethod inspect(Method method) {
                        return AnnotationUtils.findAnnotation(method, RetryOnMethod.class);
                    }
                });
    }



    /**
     * 解析单个 Bean 的方法注解，生成方法级任务定义。
     *
     * @param bean              Spring Bean
     * @param applicationContext Spring 上下文，用于事务代理目标对象解析
     */
    private void resolveMethodAnnotation(Object bean,ApplicationContext applicationContext) {
        // JDK 代理类自身没有目标方法上的注解，必须回到 AOP 目标类型解析。
        Map<Method, RetryOnMethod> methodTMap = findRetryMethods(AopUtils.getTargetClass(bean));
        if (methodTMap == null || methodTMap.isEmpty()) {
            return;
        }


        methodTMap.forEach((method, retryOnMethod) -> {
            String taskCode = RetryTaskCodeBuilder.build(method);
            checkRetryConfiguration(taskCode, retryOnMethod);
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

    /**
     * 校验方法级重试注解的完整运行配置。
     *
     * @param taskCode      任务编码，用于错误定位
     * @param retryOnMethod 方法重试配置
     * @throws RetryException 异常集合重叠或执行间隔非法
     */
    static void checkRetryConfiguration(String taskCode, RetryOnMethod retryOnMethod) {
        checkExceptionConfiguration(taskCode, retryOnMethod);
        if (retryOnMethod.intervalSecond() <= 0) {
            throw new RetryException(String.format(
                    "retry task %s intervalSecond must be positive, current=%d",
                    taskCode, retryOnMethod.intervalSecond()));
        }
    }

}
