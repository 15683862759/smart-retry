package com.smart.retry.common.constant;

/**
 * 方法重试复发的类型
 *
 * @Author gao.gwq
 * @Version RetryOccurType.java, v 0.1 2023年09月14日 15:53 gao.gwq
 * @Description: 重试触发类型枚举，决定首次执行结果如何进入重试判断。
 */
public enum RetryOccurType {

    /**
     * 方法抛出异常时触发重试判断。
     */
    EXCEPTION("EXCEPTION", "异常"),
    /**
     * 方法正常返回后触发重试判断，适合由业务返回值判断是否失败。
     */
    RESULT("RESULT", "结果");

    private String code;

    private String desc;

    RetryOccurType(String code, String desc) {
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
