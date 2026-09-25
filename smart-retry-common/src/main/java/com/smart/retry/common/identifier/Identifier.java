package com.smart.retry.common.identifier;

/**
 * @Author xiaoqiang
 * @Version Identifier.java, v 0.1 2025年02月12日 12:50 xiaoqiang
 * @Description: 重试任务唯一标识生成接口。用于根据任务编码和序列化后的调用参数
 * 生成 unique_key，配合数据库唯一索引和内存缓存完成幂等去重。
 */
public interface Identifier {

    /**
     * 生成任务唯一标识。
     *
     * @param taskCode 任务编码，必须与消费者注册时使用的编码一致
     * @param argStr   已序列化的方法参数；相同业务语义应生成稳定字符串，
     *                 以保证重复提交能够命中同一个标识
     * @return 唯一标识字符串
     */
    String identify(String taskCode,String argStr);
}
