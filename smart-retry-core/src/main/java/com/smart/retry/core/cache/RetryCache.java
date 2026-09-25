package com.smart.retry.core.cache;

import com.google.common.collect.Maps;
import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.model.RetryTaskObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author xiaoqiang
 * @Version RetryCache.java, v 0.1 2025年02月14日 09:54 xiaoqiang
 * @Description: 重试任务定义注册表。应用启动扫描后保存 taskCode 到执行对象、
 * 目标方法和回调配置的映射，是任务能否被本实例识别和执行的核心索引。
 */
public class RetryCache {

    private static final Map<String, RetryTaskObject> RETRY_CACHE = new ConcurrentHashMap<String, RetryTaskObject>();

    /**
     * 注册任务定义；重复 taskCode 视为启动配置错误。
     *
     * @param key             任务编码
     * @param retryTaskObject 任务执行定义
     * @throws RetryException taskCode 已存在
     */
    public static void put(String key, RetryTaskObject retryTaskObject) {
        String errFormat = "retry task {} already exists in cache";
        if (RETRY_CACHE.putIfAbsent(key, retryTaskObject) != null) {
            throw new RetryException(String.format(errFormat, key));
        }
    }

    /**
     * 按任务编码获取执行定义。
     *
     * @param key 任务编码
     * @return 执行定义；本实例未注册时返回 null
     */
    public static RetryTaskObject get(String key) {
        return RETRY_CACHE.get(key);
    }

    /**
     * 获取原始注册表，用于扫描和测试。
     *
     * @return taskCode 到任务定义的映射
     */
    public static Map<String, RetryTaskObject> getAll() {
        return RETRY_CACHE;
    }

    /**
     * 移除指定任务定义。
     *
     * @param key 任务编码
     */
    public static void remove(String key) {
        RETRY_CACHE.remove(key);
    }

    /**
     * 容器销毁时清空已注册的重试定义，避免应用上下文重建后重复注册失败。
     */
    public static void clear() {
        RETRY_CACHE.clear();
    }

    /**
     * 获取当前注册的任务定义总数。
     *
     * @return 注册表大小；参数 key 仅保留兼容语义
     */
    public static int retryCount(String key) {
        return RETRY_CACHE.size();
    }

}
