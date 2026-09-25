package com.smart.retry.core.innovation;

import com.smart.retry.common.RetryConfiguration;
import com.smart.retry.common.RetryListener;
import com.smart.retry.common.constant.ExecuteResultStatus;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.constant.RetryTaskTypeEnum;
import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.exception.RetryTaskClaimedException;
import com.smart.retry.common.innovation.SmartInnovation;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.model.RetryTaskObject;
import com.smart.retry.common.notify.NotifyContext;
import com.smart.retry.common.notify.RetryTaskNotify;
import com.smart.retry.common.utils.ExceptionUtils;
import com.smart.retry.common.utils.GsonTool;
import com.smart.retry.core.cache.RetryCache;
import com.smart.retry.core.nextPlanTimeStrategy.*;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author xiaoqiang
 * @Version DefaultInnovation.java, v 0.1 2025年02月18日 13:41 xiaoqiang
 * @Description: 默认任务执行器。先原子认领数据库任务，再根据任务类型调用监听器
 * 或业务方法，最后条件化写入终态并触发通知，是并发安全和幂等执行的关键实现。
 */
public class DefaultInnovation implements SmartInnovation {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultInnovation.class);

    private RetryTask retryTask;

    private RetryConfiguration retryConfiguration;


    private static final Map<Class<? extends RetryTaskNotify>, RetryTaskNotify> retryTaskNotifyMap = new ConcurrentHashMap<>();

    public DefaultInnovation(RetryTask retryTask, RetryConfiguration retryConfiguration) {
        this.retryTask = retryTask;
        this.retryConfiguration = retryConfiguration;
    }


    /**
     * 原子认领任务（乐观锁 CAS）。
     *
     * <p>仅当任务在 DB 中仍为 WAITING(0)/FAIL(3) 且 {@code retry_num >= 1}
     * （且调用方传入的 sharding_key 与任务本身一致）时，
     * 原子地置为 RUNNING(1)、{@code retry_num - 1}，并写入唯一 executor 租约与 next_plan_time。
     * 并发（同 JVM delayQueue 残留 + 手动触发，或多实例 Producer）下只有一个调用方受影响行数为 1，
     * 其余认领失败并抛出 {@link RetryTaskClaimedException}，从而保证业务方法至多被执行一次。
     *
     * <p>认领成功后同步内存状态，保证内存对象与 DB 一致（finally 写入的也是扣减后的值，幂等）。
     * 认领失败抛出异常且在任何内存变更之前，无副作用。
     */
    private void beforeProcessTask(RetryTask retryTask) {
        String leaseToken = UUID.randomUUID().toString();
        int claimed = retryConfiguration.getRetryTaskAcess().claimRetryTask(
                retryTask.getId(),
                leaseToken,
                retryTask.getNextPlanTime(),
                retryTask.getShardingKey());
        if (claimed != 1) {
            throw new RetryTaskClaimedException(retryTask.getId());
        }
        retryTask.setStatus(RetryTaskStatus.RUNNING.getCode());

        Integer retryNum = retryTask.getRetryNum();
        if (retryNum >= 1) {
            retryTask.setRetryNum(retryNum - 1);
        }
        retryTask.setExecutor(leaseToken);
    }

    @Override
    /**
     * 执行一次重试任务。
     *
     * <p>实现过程：
     * 1. 先按策略计算下次执行时间，供认领任务和失败后调度使用；
     * 2. 查找 taskCode 对应对象，缺失时用 CAS 将任务标记为失败；
     * 3. 通过数据库 CAS 认领任务，只有一个执行方能够成功；
     * 4. 按 CLASS/METHOD 模式反序列化参数并调用业务代码；
     * 5. 在 finally 中以执行租约为守卫写入终态，成功写入后触发通知。
     *
     * @return 业务方法或监听器的返回值
     * @throws Throwable 业务异常或认领竞争异常
     */
    public Object invoke() throws Throwable {

        processNextExecuteTime(retryTask);

        String taskCode = retryTask.getTaskCode();
        RetryTaskObject taskObject = RetryCache.get(taskCode);
        if (taskObject == null) {
            LOGGER.error("[DefaultInnovation#invoke]taskObject is null, taskCode:{}", taskCode);

            // 手动更新任务状态：减少重试次数、标记失败
            // 不调用beforeProcessTask，保留原有nextPlanTime不变
            processNullTaskObject();
            // 直接更新DB状态，不走finally块（避免notify NPE）
            return null;
        }
        // 原子认领任务（CAS）：认领移出 try/finally。
        // 认领失败抛 RetryTaskClaimedException 直接向外传播，绝不进入 finally，
        // 避免败者把胜者的 RUNNING 覆盖成 FAIL，导致业务被重复执行。
        beforeProcessTask(retryTask);

        Method method = taskObject.getMethod();

        Throwable throwable = null;


        ExecuteResultStatus executeResultStatus = ExecuteResultStatus.SUCCESS;
        NotifyContext notifyContext = new NotifyContext();
        notifyContext.setRetryTask(retryTask);
        try {
            Object result = doInvoke(taskObject, retryTask, method);
            RetryTaskTypeEnum retryTaskTypeEnum = taskObject.getRetryType();

            if (result == null) {
                executeResultStatus = ExecuteResultStatus.SUCCESS;
            }
            if (result != null && !(result instanceof ExecuteResultStatus)) {
                executeResultStatus = ExecuteResultStatus.SUCCESS;
            }
            if (result != null && (result instanceof ExecuteResultStatus)) {
                executeResultStatus = (ExecuteResultStatus) result;
            }
            notifyContext.setResult(result);
            return result;
        } catch (Throwable ex) {
            throwable = ex;
            LOGGER.error("[DefaultInnovation#invoke]taskCode:{} invoke error", taskCode, ex);
            executeResultStatus = ExecuteResultStatus.FAIL;
            notifyContext.setThrowable(ex);
            throw ex;
        } finally {
            String exceptionMsg = ExceptionUtils.createConciseStackTraceMessage(throwable);
            if (exceptionMsg != null) {
                retryTask.setAttribute(exceptionMsg);
            }
            //更新任务状态为执行完成
            if (executeResultStatus == ExecuteResultStatus.SUCCESS) {
                retryTask.setStatus(RetryTaskStatus.SUCCESS.getCode());
            }
            if (executeResultStatus == ExecuteResultStatus.FAIL) {
                retryTask.setStatus(RetryTaskStatus.FAIL.getCode());
            }
            // 条件化写入终态（乐观锁 CAS 守卫）：仅当仍由本执行方持有租约（executor 匹配）
            // 且 retry_num 无漂移（== 认领后扣减值）时生效，防止 stale 副本覆盖
            // DeadLetterTask 复活后的状态（retry_num 已 +1）或新租约持有者写入的 RUNNING。
            // 写入失败说明任务已被复活/转交他人，不触发通知，避免重复或错误通知。
            int terminal = retryConfiguration.getRetryTaskAcess().markRetryTaskTerminal(
                    retryTask.getId(),
                    retryTask.getStatus(),
                    retryTask.getExecutor(),
                    retryTask.getRetryNum(),
                    retryTask.getNextPlanTime(),
                    retryTask.getAttribute());
            if (terminal == 1) {
                // 仅当终态写入成功（仍持有租约）时触发通知，避免重复或错误通知。
                // 注意：不得在 finally 中 return（invoke 返回 Object，finally 的 return 会覆盖 try 返回值）。
                notify(taskObject, taskCode, notifyContext, executeResultStatus, throwable);
            } else {
                LOGGER.warn("[DefaultInnovation#invoke] markTerminal skipped, task may be "
                        + "revived or claimed by another executor, taskId:{}, status:{}",
                        retryTask.getId(), retryTask.getStatus());
            }
        }
    }

    /**
     * 处理 taskCode 未注册的任务。
     *
     * <p>不进入普通执行租约流程，而是以 WAITING/FAIL 状态和扣减前 retry_num
     * 为 CAS 守卫，原子扣减一次并标记 FAIL，避免覆盖其他实例刚认领的 RUNNING。
     */
    private void processNullTaskObject() {
        String leaseToken = UUID.randomUUID().toString();
        Integer retryNum = retryTask.getRetryNum();
        int before = retryNum == null ? 0 : retryNum;
        if (before >= 1) {
            retryTask.setRetryNum(before - 1);
        }
        retryTask.setStatus(RetryTaskStatus.FAIL.getCode());
        retryTask.setExecutor(leaseToken);
        retryTask.setAttribute("taskObject is null");
        // 条件化更新（乐观锁 CAS 守卫）：仅当任务仍为 WAITING/FAIL 且 retry_num 与内存一致
        // （扣减前值）时写 FAIL 并扣减一次，防止分片重叠窗口下覆盖他方已认领的 RUNNING 或已终态。
        int updated = retryConfiguration.getRetryTaskAcess()
                .markNullTaskObjectFail(retryTask.getId(), leaseToken, before,
                        retryTask.getNextPlanTime(), "taskObject is null");
        if (updated != 1) {
            LOGGER.warn("[DefaultInnovation#processNullTaskObject] mark fail skipped, "
                    + "task may be claimed/revived, taskId:{}", retryTask.getId());
        }
    }


    /**
     * 按配置策略计算下次执行时间并写回内存任务对象。
     *
     * @param retryTask 当前任务
     */
    private void processNextExecuteTime(RetryTask retryTask) {

        Date nextPlanTime = NextPlanTimeStrategyManager.nextTime(retryTask);
        retryTask.setNextPlanTime(nextPlanTime);

    }

    /**
     * 调用业务代码。
     *
     * <p>CLASS 模式反序列化监听器泛型参数并执行 before/consume/after 生命周期；
     * METHOD 模式反序列化方法参数并调用真实目标对象，避免再次经过重试 AOP。
     *
     * @param taskObject 任务注册对象
     * @param retryTask  当前任务
     * @param method    目标方法
     * @return 业务返回值
     * @throws Throwable 反序列化或业务调用异常
     */
    private Object doInvoke(RetryTaskObject taskObject, RetryTask retryTask, Method method) throws Throwable {
        RetryTaskTypeEnum retryTaskTypeEnum = taskObject.getRetryType();

        if (retryTaskTypeEnum == RetryTaskTypeEnum.CLASS) {
            Object parameterValue = null;
            if (retryTask.getParameters() == null) {
                parameterValue = null;
            }
            Type type = getRealType(taskObject);

            parameterValue = GsonTool.fromJson(retryTask.getParameters(), type);
            Object[] args = new Object[1];
            args[0] = parameterValue;

            return invokeRetryListener(retryTask, taskObject, args[0]);
        }
        if (retryTaskTypeEnum == RetryTaskTypeEnum.METHOD) {
            Object[] args = retryConfiguration.getSmartSerializer().deSerializer(method, retryTask.getParameters());
            // METHOD 重试必须回到真实业务对象执行。若继续调用 Spring 代理，
            // @RetryOnMethod 拦截器会再次注册同 uniqueKey 的任务并被去重拦截，
            // 造成后续重试停止推进。
            Object target = AopProxyUtils.getSingletonTarget(taskObject.getTargetObj());
            return method.invoke(target != null ? target : taskObject.getTargetObj(), args);
        }
        throw new RetryException("retryTaskTypeEnum is not support");
    }

    /**
     * 执行监听器模式生命周期。
     *
     * <p>beforeConsume 的异常只记录日志，不阻断 consume；
     * afterConsume 始终执行，且自身异常也不会覆盖 consume 的返回值或异常。
     *
     * @param retryTask 当前任务
     * @param taskObject 任务注册对象
     * @param args       反序列化后的监听器参数
     * @return consume 的返回值
     * @throws Throwable consume 抛出的业务异常
     */
    private ExecuteResultStatus invokeRetryListener(RetryTask retryTask, RetryTaskObject taskObject, Object args) throws Throwable {

        RetryListener retryListener = (RetryListener) taskObject.getTargetObj();
        try {

            retryListener.beforeConsume(retryTask, args);
        } catch (Exception ex) {
            LOGGER.error("retry-task listener beforeConsume {}", ex.getMessage(), ex);
        }

        ExecuteResultStatus consumeStatus = null;
        try {
            consumeStatus = retryListener.consume(args);
            return consumeStatus;
        } finally {
            try {
                retryListener.afterConsume(retryTask, consumeStatus, args);
            } catch (Exception ex) {
                LOGGER.error("retry-task listener afterConsume {}", ex.getMessage(), ex);
            }
        }

    }

    /**
     * 获取真实的参数类型
     *
     * @param taskObject
     * @return
     */

    private Type getRealType(RetryTaskObject taskObject) {


        Object targetObj = taskObject.getTargetObj();
        Class<?> clazz = targetObj.getClass();
        //判断对象是否是代理对象
        if (AopUtils.isAopProxy(targetObj)) {
            clazz = AopUtils.getTargetClass(targetObj);
        }
        for (Type genericInterface : clazz.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if (pt.getRawType() == RetryListener.class) {
                    Type[] args = pt.getActualTypeArguments();
                    if (args.length > 0) {
                        Type targetType = args[0];

                        // 不要试图转成 Class！保留完整的 Type（可能是 ParameterizedType）
                        // 例如：TestModel<String>、List<Map<String, Object>> 等都能正确表示

                        LOGGER.debug("[getRealType] Resolved generic type: {}", targetType.getTypeName());
                        return targetType;
                    }
                }
            }
        }

        LOGGER.warn("[getRealType] Failed to resolve generic type for: {}", clazz.getName());
        return Object.class; // fallback
    }
    /*private  Type getRealType(RetryTaskObject taskObject) {
        Type superClass = taskObject.getTargetObj().getClass().getGenericSuperclass();
        ParameterizedType parameterizedType = (ParameterizedType) superClass;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        Type type = TypeToken.get(actualTypeArguments[0]).getType();
        return type;
    }*/

    /**
     * 执行任务通知。
     *
     * <p>每次执行结束都会调用 oneTimeNotify；当剩余重试次数为 0 时，
     * 额外调用 allRetryTaskFinishNotify 表示整个重试链路结束。
     * 通知实现实例按类型缓存，单个通知失败只记录日志，不影响其他通知。
     *
     * @param taskObject           任务注册对象
     * @param taskCode             任务编码
     * @param notifyContext        通知上下文
     * @param executeResultStatus  本次执行结果
     * @param throwable            本次执行异常，成功时为 null
     */
    private void notify(RetryTaskObject taskObject, String taskCode, NotifyContext notifyContext, ExecuteResultStatus executeResultStatus, Throwable throwable) {
        if (taskObject == null) {
            return;
        }
        Class<? extends RetryTaskNotify>[] clazzs = taskObject.getRetryTaskNotify();
        if (ArrayUtils.isEmpty(clazzs)) {
            return;
        }
        notifyContext.setExecutionStatus(executeResultStatus);
        notifyContext.setThrowable(throwable);
        //1.m每次执行结束
        Arrays.stream(clazzs).forEach(clazz -> {
            RetryTaskNotify notify = retryTaskNotifyMap.get(clazz);
            if (notify == null) {
                notify = generateNotify(taskCode, clazz);
                if (notify == null) {
                    return;
                }
            }
            try {
                notify.oneTimeNotify(notifyContext);
            } catch (Throwable ex) {
                LOGGER.warn("[DefaultInnovation#notify] oneTimeNotify invoke error,taskCode:{} ", taskCode, ex);
            }
            if (retryTask.getRetryNum() == 0) {
                try {
                    notify.allRetryTaskFinishNotify(notifyContext);
                } catch (Throwable ex) {
                    LOGGER.warn("[DefaultInnovation#notify]taskCode:{} finishTaskNotify invoke error,taskCode:{} ", taskCode, ex);
                }
            }
        });
    }

    /**
     * 反射创建通知实例并按类型缓存。
     *
     * @param taskCode 任务编码，仅用于失败日志
     * @param clazz    通知类型
     * @return 通知实例；创建失败时返回 null
     */
    private static RetryTaskNotify generateNotify(String taskCode, Class<? extends RetryTaskNotify> clazz) {
        RetryTaskNotify notify = null;
        try {
            notify = clazz.newInstance();
        } catch (Exception e) {
            LOGGER.warn("[DefaultInnovation#notify] notify instance error,taskCode:{} ", taskCode, e);
            return notify;
        }
        retryTaskNotifyMap.put(clazz, notify);
        return notify;
    }

}


