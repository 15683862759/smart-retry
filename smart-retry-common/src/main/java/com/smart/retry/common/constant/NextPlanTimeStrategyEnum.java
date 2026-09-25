package com.smart.retry.common.constant;

/**
 * @Author xiaoqiang
 * @Version RetryTypeEnum.java, v 0.1 2025年07月15日 17:09 xiaoqiang
 * @Description: 下次执行时间策略枚举。根据策略编码选择固定、递增、
 * 斐波那契或指数退避算法计算 retry_task.next_plan_time。
 */
public enum NextPlanTimeStrategyEnum {

    /**
     * 每次失败后按固定间隔重试。
     */
    FIXED(1,"固定间隔"),
    /**
     * 每次失败后间隔按执行次数线性递增。
     */
    INCREMENTING(2,"递增"),

    /**
     * 每次失败后间隔按斐波那契数列递增。
     */
    FIBONACCI(3,"斐波那契"),
    /**
     * 每次失败后间隔按 2 的幂次指数退避。
     */
    BACKOFF(4,"退避"),;
    private int code;
    private String desc;

    NextPlanTimeStrategyEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据持久化的策略编码还原枚举。
     *
     * @param code 策略编码
     * @return 对应策略；编码不存在时返回 null，由调用方做参数校验
     */
    public static NextPlanTimeStrategyEnum getByCode(int code) {
        for (NextPlanTimeStrategyEnum retryTypeEnum : NextPlanTimeStrategyEnum.values()) {
            if (retryTypeEnum.getCode() == code) {
                return retryTypeEnum;
            }
        }
        return null;
    }
}
