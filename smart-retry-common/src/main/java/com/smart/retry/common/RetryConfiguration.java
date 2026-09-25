package com.smart.retry.common;

import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.innovation.SmartInnovation;
import com.smart.retry.common.serializer.SmartSerializer;

/**
 * @Author xiaoqiang
 * @Version RetryConfiguration.java, v 0.1 2025年02月14日 21:15 xiaoqiang
 * @Description: 框架运行配置门面。将任务访问器、唯一标识生成器和参数序列化器
 * 聚合后注入核心引擎，具体持久化和序列化实现由 Starter 或业务方提供。
 */
public interface RetryConfiguration {
    /**
     * 获取重试任务的访问器
     * 实现必须返回非 null 对象，否则任务无法持久化和状态流转。
     *
     * @return 任务持久化访问器
     */
    RetryTaskAccess getRetryTaskAcess();
    /**
     * 获取重试任务的标识符
     * 实现必须返回非 null 对象，否则任务无法完成幂等去重。
     *
     * @return 唯一标识生成器
     */
    Identifier getIdentifier();


    /**
     * 获取序列化器
     *
     * @return 方法参数序列化器
     */
    SmartSerializer getSmartSerializer();







}
