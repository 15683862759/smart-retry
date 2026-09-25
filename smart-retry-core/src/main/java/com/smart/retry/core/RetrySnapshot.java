package com.smart.retry.core;

import com.smart.retry.common.model.MethodChain;

import java.lang.reflect.Method;

/**
 * @Author xiaoqiang
 * @Version RetrySnapshot.java, v 0.1 2025年02月15日 16:32 xiaoqiang
 * @Description: 重试调用链快照。通过 ThreadLocal 记录本次请求中嵌套的
 * @RetryOnMethod 方法链，用于把异常收敛到最底层方法并避免重复注册任务。
 */
public class RetrySnapshot {

    private static final ThreadLocal<MethodChain> METHOD_CHAIN_THREAD_LOCAL = new ThreadLocal<>();



    /**
     * 从当前线程调用链中移除指定方法节点。
     *
     * @param method 已完成拦截的方法；null 时直接清空整个调用链
     */
    public static void removeInterceptorChain(Method method) {

        if (method == null) {
            METHOD_CHAIN_THREAD_LOCAL.remove();
            return;
        }
        MethodChain methodChainModel = METHOD_CHAIN_THREAD_LOCAL.get();
        MethodChain preTail = null;
        MethodChain current = methodChainModel;
        MethodChain matched = null;
        MethodChain matchedPre = null;
        while (current != null) {
            if (method.equals(current.getMethod())) {
                matched = current;
                matchedPre = preTail;
            }
            preTail = current;
            current = current.getNext();
        }
        if (matched == null) {
            return;
        }
        if (matchedPre == null) {
            METHOD_CHAIN_THREAD_LOCAL.remove();
            return;
        }
        matchedPre.setNext(null);
    }

    /**
     * 按方法对象查找当前线程中的调用链节点。
     *
     * @param method 目标方法
     * @return 匹配节点；不存在时返回 null
     */
    public static MethodChain getChainByMethod(Method method) {
        MethodChain methodChainModel = METHOD_CHAIN_THREAD_LOCAL.get();
        MethodChain matched = null;
        while (methodChainModel != null) {
            if (method.equals(methodChainModel.getMethod())) {
                matched = methodChainModel;
            }
            methodChainModel = methodChainModel.getNext();
        }
        return matched;
    }

    /**
     * 把新拦截的方法追加到当前线程调用链。
     *
     * @param chainModel 新进入的重试方法节点
     */
    public static synchronized void setInterceptorChain(MethodChain chainModel) {

        MethodChain methodChainModel = METHOD_CHAIN_THREAD_LOCAL.get();
        if (methodChainModel == null) {
            chainModel.setHeader(true);
            chainModel.setTail(true);
            METHOD_CHAIN_THREAD_LOCAL.set(chainModel);
            return;
        }
        MethodChain tmpChain = methodChainModel;
        MethodChain preTmpChain = tmpChain;
        while (true) {
            preTmpChain = tmpChain;
            tmpChain = tmpChain.getNext();
            if (tmpChain == null) {
                break;
            }
        }
        preTmpChain.setHeader(false);
        preTmpChain.setTail(false);

        chainModel.setTail(true);
        chainModel.setHeader(false);
        //设置第一个为header数据
        methodChainModel.setHeader(true);
        preTmpChain.setNext(chainModel);

    }
}
