package com.smart.retry.common.exception;

/**
 * @Author xiaoqiang
 * @Version RetryException.java, v 0.1 2025年02月14日 13:03 xiaoqiang
 * @Description: 框架运行时异常。用于在参数校验、配置错误和内部状态异常时
 * 向调用方返回明确的框架错误信息。
 */
public class RetryException extends RuntimeException {
    /**
     * 使用错误信息构造异常。
     *
     * @param message 面向调用方的错误描述
     */
    public RetryException(String message) {
        super(message);
    }
    /**
     * 使用错误信息和底层原因构造异常。
     *
     * @param message 错误描述
     * @param cause   底层异常
     */
    public RetryException(String message, Throwable cause) {
        super(message, cause);
    }
    /**
     * 直接包装底层异常，保留原始堆栈。
     *
     * @param cause 底层异常
     */
    public RetryException(Throwable cause) {
        super(cause);
    }
    /**
     * 构造无信息异常。
     */
    public RetryException() {
        super();
    }
}
