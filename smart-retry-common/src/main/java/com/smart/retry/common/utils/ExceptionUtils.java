package com.smart.retry.common.utils;

/**
 * @author gao.gwq
 * @version 1.0
 * @date 2022/5/6  19:21
 * @Description: 异常堆栈格式化工具。将 Throwable 转为完整或精简堆栈文本，
 * 用于写入任务错误字段和日志，便于后续排查失败原因。
 */
public class ExceptionUtils {
    /**
     * 将异常转换为完整堆栈文本。
     *
     * @param e 业务异常；null 返回空字符串
     * @return 异常类型、消息和完整堆栈
     */
    public static String createStackTrackMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        StringBuilder messsage = new StringBuilder();
        if (e != null) {
            messsage.append(e.getClass()).append(": ").append(e.getMessage()).append("\n");
            StackTraceElement[] elements = e.getStackTrace();
            for (StackTraceElement stackTraceElement : elements) {
                messsage.append("\t").append(stackTraceElement.toString()).append("\n");
            }
        }
        return messsage.toString();
    }

    // 定义要记录的每个异常堆栈的最大深度
    private static final int MAX_STACK_DEPTH = 6;

    /**
     * 创建一个精简版的异常堆栈跟踪消息。
     * 主要包含：
     * 1. 异常类型和消息
     * 2. 堆栈跟踪的前几行（由 MAX_STACK_DEPTH 控制）
     * 3. 如果有 cause，则递归处理 cause（同样精简）
     *
     * @param e 要处理的 Throwable 对象
     * @return 格式化后的字符串
     */
    public static String createConciseStackTraceMessage(Throwable e) {
        if (e == null) {
            return "";
        }

        StringBuilder message = new StringBuilder();

        // --- 记录当前异常 ---
        appendExceptionInfo(message, e, "");

        // --- 记录 Cause Chain (可选) ---
        Throwable cause = e.getCause();
        int depth = 0;
        while (cause != null && depth < 12) { // 防止循环引用导致的无限循环，限制深度
            // 使用 "Caused by: " 前缀，符合标准异常输出格式
            appendExceptionInfo(message, cause, "Caused by: ");
            cause = cause.getCause();
            depth++;
        }

        return message.toString();
    }

    /**
     * 将单个异常的信息（类型、消息、部分堆栈）追加到 StringBuilder。
     *
     * @param sb       目标 StringBuilder
     * @param t        要处理的 Throwable
     * @param prefix   添加到异常行前的前缀（例如 "Caused by: "）
     */
    private static void appendExceptionInfo(StringBuilder sb, Throwable t, String prefix) {
        // 添加异常类型和消息
        sb.append(prefix).append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");

        // 添加部分堆栈跟踪
        StackTraceElement[] elements = t.getStackTrace();
        int elementsToPrint = Math.min(elements.length, MAX_STACK_DEPTH);
        for (int i = 0; i < elementsToPrint; i++) {
            sb.append("\tat ").append(elements[i].toString()).append("\n");
        }

        // 如果堆栈被截断，添加提示
        if (elements.length > MAX_STACK_DEPTH) {
            sb.append("\t... ").append(elements.length - MAX_STACK_DEPTH).append(" more\n");
        }
    }
}
