package com.smart.retry.common.annotation;

import com.smart.retry.common.IRetryCallback;
import com.smart.retry.common.notify.RetryTaskNotify;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * @Author xiaoqiang
 * @Version RetryOnClass.java, v 0.1 2025年02月14日 09:52 xiaoqiang
 * @Description: 类级重试注解。标注实现 RetryListener 的 Spring Bean，
 * 启动扫描时以 taskCode 注册到本地缓存，任务创建后由调度器调用 consume 方法。
 * 该注解自带 @Component 语义，可省略额外组件注解。
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Component
public @interface RetryOnClass {

    /**
     * 任务名称
     * 必填，任务创建方、扫描注册表和数据库记录通过该编码建立关联。
     *
     * @return
     */
    String taskCode();

    /**
     * 任务描述，用于管理后台展示，不参与执行路由。
     *
     * @return 任务描述
     */
    String taskDesc() default "";

    /**
     * 任务生命周期通知列表，按声明顺序执行。
     *
     * @return 通知类型数组
     */
    Class<? extends RetryTaskNotify>[] retryTaskNotifies() default {};

}
