package com.smart.retry.core;

import com.smart.retry.common.model.MethodChain;

import java.lang.reflect.Method;

/**
 * @Author xiaoqiang
 * @Version RetrySnapshot.java, v 0.1 2025年02月15日 16:32 xiaoqiang
 * @Description: TODO
 */
public class RetrySnapshot {

    private static final ThreadLocal<MethodChain> METHOD_CHAIN_THREAD_LOCAL = new ThreadLocal<>();



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
