package com.smart.retry.common.constant;

/**
 * @Author xiaoqiang
 * @Version ExecuteResultStatus.java, v 0.1 2025年02月12日 11:14 xiaoqiang
 * @Description: 单次重试执行结果状态，用于回调、通知和任务终态判断。
 */
public enum ExecuteResultStatus {
    /**
     * 本次业务执行成功，任务可写入 SUCCESS 终态。
     */
    SUCCESS,
    /**
     * 本次业务执行失败，任务按剩余次数和下次执行时间继续调度。
     */
    FAIL,;
}
