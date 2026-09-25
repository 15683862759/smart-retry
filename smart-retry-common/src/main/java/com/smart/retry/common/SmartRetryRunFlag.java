package com.smart.retry.common;

/**
 * @Author xiaoqiang
 * @Version SmartRetryRunFlag.java, v 0.1 2025年11月17日 17:25 xiaoqiang
 * @Description: 调度启动标志。Spring 启动扫描完成并注册消费者后置为 true，
 * Producer 只有在该标志开启后才会开始拉取任务，避免任务早注册导致找不到消费者。
 */
public class SmartRetryRunFlag {

    private static volatile Boolean flag = false;


    /**
     * 获取调度启动标志。
     *
     * @return true 表示扫描完成并允许调度
     */
    public static Boolean getFlag() {
        return flag;
    }

    /**
     * 设置调度启动标志。
     *
     * @param flag true 表示允许调度；false 用于停机或测试重置
     */
    public static void setFlag(Boolean flag) {
        SmartRetryRunFlag.flag = flag;
    }

}
