package com.smart.retry.core.scanner;

import com.smart.retry.common.SmartRetryRunFlag;
import com.smart.retry.common.scanner.RetryScanner;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;

/**
 * 应用启动后扫描并注册重试入口。
 *
 * @Author xiaoqiang
 * @Version RetryScannerManager.java, v 0.1 2025年02月14日 18:52 xiaoqiang
 * @Description: 扫描方法注解与监听器模式的重试入口。
 */
public class RetryScannerManager implements RetryScanner, ApplicationContextAware {

    private ApplicationContext context;
    private volatile boolean flag = false;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void scan(ApplicationContext applicationContext) {
        RetryScanner methodScanner = new RetryMethodScanner();
        methodScanner.scan(applicationContext);

        RetryScanner classScanner = new RetryClassScanner();
        classScanner.scan(applicationContext);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        if (flag) {
            return;
        }
        scan(context);
        flag = true;
        SmartRetryRunFlag.setFlag(true);
    }
}
