package com.smart.retry.common.notify;

import com.smart.retry.common.constant.ExecuteResultStatus;
import com.smart.retry.common.model.RetryTask;

/**
 * @Author xiaoqiang
 * @Version NotifyContext.java, v 0.1 2025年02月18日 17:28 xiaoqiang
 * @Description: 任务完成通知上下文。携带任务元数据、还原后的参数、
 * 执行状态、业务返回值和异常，供通知实现生成告警或外部系统记录。
 */
public class NotifyContext {

    private RetryTask retryTask;


    /**
     * 反序列化后的方法参数或监听器上下文参数。
     */
    private Object []args;

    /**
     * 本次执行结果状态。
     */
    private ExecuteResultStatus executionStatus;


    /**
     * 本次执行抛出的异常；执行成功时为 null。
     */
    private Throwable throwable;

    /**
     * 业务方法返回值；监听器模式通常为 null。
     */
    private Object result;
    public Object getResult() {
        return result;
    }
    public void setResult(Object result) {
        this.result = result;
    }

    public RetryTask getRetryTask() {
        return retryTask;
    }

    public void setRetryTask(RetryTask retryTask) {
        this.retryTask = retryTask;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public ExecuteResultStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(ExecuteResultStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }
}
