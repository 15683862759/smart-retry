package com.smart.retry.common;

/**
 * @Author xiaoqiang
 * @Version SmartRtryFlag.java, v 0.1 2025年11月14日 21:19 xiaoqiang
 * @Description: 进程退出标志。JVM 关闭钩子置位后，调度任务通过该标志
 * 停止继续拉取新任务，避免关闭期间继续认领数据库任务。
 */
public class SmartRetryExit {

    private static volatile Boolean taskFlag = false;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            taskFlag = true;
        }));
    }

    /**
     * 判断框架是否仍允许运行。
     *
     * @return true 表示仍在运行；JVM 收到关闭请求后返回 false
     */
    public static Boolean isExit() {
        return !taskFlag;
    }
}
