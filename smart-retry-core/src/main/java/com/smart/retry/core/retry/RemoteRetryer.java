package com.smart.retry.core.retry;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.annotation.RetryOnMethod;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.model.RetryAttemptContext;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.retry.IRetryer;
import com.smart.retry.common.serializer.SmartSerializer;
import com.smart.retry.common.utils.IpUtils;
import com.smart.retry.common.utils.LogIdUtils;
import com.smart.retry.core.ShardingContextHolder;
import com.smart.retry.core.SimpleContainer;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * @Author xiaoqiang
 * @Version RemoteRetryer.java, v 0.1 2025年02月14日 19:24 xiaoqiang
 * @Description: 方法级远程重试注册器。将首次失败的方法调用快照序列化落库，
 * 并在事务提交后加入内存精准调度队列。
 */
public class RemoteRetryer implements IRetryer {
    private static final Logger log = LoggerFactory.getLogger(RemoteRetryer.class);

    private MethodInvocation methodInvocation;


    private RetryOnMethod retryable;

    private RetryAttemptContext retryAttemptContext;

    private RetryConfiguration retryConfiguration;

    /**
     * 创建方法级任务注册器。
     *
     * @param retryConfiguration    框架配置门面
     * @param methodInvocation      当前 AOP 调用现场
     * @param retryable             方法重试注解配置
     * @param retryAttemptContext   首次调用结果和异常上下文
     */
    public RemoteRetryer(RetryConfiguration retryConfiguration,MethodInvocation methodInvocation,
                         RetryOnMethod retryable,
                         RetryAttemptContext retryAttemptContext) {
        this.retryConfiguration = retryConfiguration;
        this.methodInvocation = methodInvocation;
        this.retryable = retryable;
        this.retryAttemptContext = retryAttemptContext;
    }

    //借助guava的开源组件进行重试
    @Override
    /**
     * 注册异步重试任务，并按首次调用语义返回结果或抛出原异常。
     *
     * @return 原方法返回值
     * @throws Throwable 首次调用抛出的业务异常
     */
    public Object retry() throws Throwable{
        registerRemoteRetryTask();
        if (retryAttemptContext.getThrowable() != null) {
            throw retryAttemptContext.getThrowable();
        }
        return retryAttemptContext.getResult();
    }


    /**
     * 构建并保存方法级重试任务。
     * 保存成功后等待事务提交再入队，避免回滚产生幽灵任务。
     */
    private void registerRemoteRetryTask() {



        RetryTask retryTask = new RetryTask();
        retryTask.setRetryNum(retryable.maxAttempt() - 1);
        retryTask.setDelaySecond(retryable.firstDelaySecond());
        retryTask.setOriginRetryNum(retryable.maxAttempt() - 1);
        retryTask.setCreator(IpUtils.getIp());
        retryTask.setStatus(RetryTaskStatus.WAITING.getCode());
        Method method = methodInvocation.getMethod();
        String taskCode = method.getDeclaringClass().getName() + "#" + method.getName();
        retryTask.setTaskCode(taskCode);
        retryTask.setParameters(getArgs());
        retryTask.setUniqueKey(getUniqueKey(taskCode, getArgs()));
        retryTask.setShardingKey(ShardingContextHolder.getRandomShardingIndex());
        retryTask.setNextPlanTimeStrategy(retryable.nextPlanTimeStragy().getCode());
        retryTask.setIntervalSecond(retryable.intervalSecond());

        long firstNextExecuteTime = System.currentTimeMillis()+retryable.firstDelaySecond()*1000L;
        retryTask.setNextPlanTime(new Date(firstNextExecuteTime));

        // 把当前线程命中的 MDC traceId key + value 一起编码落到 current_log_id，
        // 重试执行时按当初那个 key 精准还原，避免污染业务方线程其它 MDC key。
        LogIdUtils.LogIdLookup lookup = LogIdUtils.getCurrentLogIdAndKey();
        retryTask.setCurrentLogId(lookup.isPresent()
                ? LogIdUtils.encode(lookup.getKey(), lookup.getValue())
                : LogIdUtils.encode(null, LogIdUtils.getCurrentLogId()));

        long taskId = retryConfiguration.getRetryTaskAcess().saveRetryTask(retryTask);
        retryTask.setId(taskId);
        if (taskId <= 0) {
            log.warn("[RemoteRetryer#registerRemoteRetryTask] retry task save failed, skip enqueue, taskId:{}",
                    taskId);
            return;
        }

        // 将任务加入 DelayQueue 精准调度（窗口内才入队）。
        // 业务方法可能处于事务中，必须等事务提交后再入队，
        // 避免回滚后留下 DB 已消失但内存仍会消费的幽灵任务。
        SimpleContainer.getContainer(retryConfiguration).enqueueAfterCommit(retryTask);

    }

    /**
     * 序列化重试任务的参数
     *
     * @return
     */
    private String getArgs() {
        Method method = methodInvocation.getMethod();
        Object []args = methodInvocation.getArguments();
        SmartSerializer serializer = retryConfiguration.getSmartSerializer();

        return serializer.serializer(method, args);

    }


    /**
     * 获取uniqueKey
     *
     * @param taskCode
     * @param argsStr
     * @return
     */
    private String getUniqueKey( String taskCode, String argsStr) {

        Identifier identifier = retryConfiguration.getIdentifier();
        return identifier.identify(taskCode, argsStr);

    }
}
