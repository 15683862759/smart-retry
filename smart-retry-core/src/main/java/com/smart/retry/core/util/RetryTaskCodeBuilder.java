package com.smart.retry.core.util;

import com.smart.retry.common.annotation.RetryOnMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 方法级任务编码构建器。
 *
 * <p>扫描注册和任务落库必须生成完全相同的 taskCode。普通方法保持
 * “类名#方法名”的旧格式；仅当同一个类中多个同名重载方法都标注
 * {@link RetryOnMethod} 时，追加参数类型签名，避免破坏既有任务记录。
 */
public final class RetryTaskCodeBuilder {

    private RetryTaskCodeBuilder() {
    }

    /**
     * 构建方法级任务编码。
     *
     * @param method 当前重试方法
     * @return 普通方法为 “类名#方法名”；重载冲突时为 “类名#方法名(参数类型,...)”
     */
    public static String build(Method method) {
        String baseCode = method.getDeclaringClass().getName() + "#" + method.getName();

        long annotatedOverloadCount = Arrays.stream(method.getDeclaringClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(method.getName()))
                .filter(candidate -> candidate.isAnnotationPresent(RetryOnMethod.class))
                .count();
        if (annotatedOverloadCount <= 1) {
            return baseCode;
        }

        String parameterSignature = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        return baseCode + "(" + parameterSignature + ")";
    }
}
