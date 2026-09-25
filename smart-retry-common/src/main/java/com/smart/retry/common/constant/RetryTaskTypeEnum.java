package com.smart.retry.common.constant;

/**
 * @Author xiaoqiang
 * @Version RetryTaskTypeEnum.java, v 0.1 2025年02月14日 09:13 xiaoqiang
 * @Description: 重试任务类型枚举，持久化到 retry_task.task_type，
 * 执行器据此选择监听器调用或目标方法反射调用。
 */
public enum RetryTaskTypeEnum {

    /**
     * 类级任务：调用 RetryListener.consume。
     */
    CLASS("CLASS","类级别"),
    /**
     * 方法级任务：根据方法签名和序列化参数反射调用原方法。
     */
    METHOD("METHOD","方法级别"),;

    private String code;

    private String desc;

    RetryTaskTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
