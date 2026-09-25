package com.smart.retry.mybatis.access;

import com.smart.retry.common.model.RetryTask;
import com.smart.retry.mybatis.entity.RetryTaskDO;
import com.smart.retry.mybatis.repo.RetryTaskRepo;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

public class MybatisAccessTest {

    @Test
    public void testSaveRetryTaskDoesNotOverflowLargeDelaySecond() {
        final AtomicReference<RetryTaskDO> savedTask = new AtomicReference<>();
        RetryTaskRepo repo = proxy(new RecordingHandler(savedTask));
        MybatisAccess access = new MybatisAccess(repo);
        RetryTask task = new RetryTask();
        task.setIntervalSecond(0);
        task.setDelaySecond(Integer.MAX_VALUE);
        task.setRetryNum(0);
        task.setOriginRetryNum(0);
        task.setStatus(0);
        task.setShardingKey(0L);
        task.setNextPlanTimeStrategy(0);

        long beforeSave = System.currentTimeMillis();
        access.saveRetryTask(task);

        RetryTaskDO saved = savedTask.get();
        Assert.assertNotNull(saved);
        Assert.assertNotNull(saved.getNextPlanTime());
        Assert.assertTrue("最大 int 秒延迟不应回绕成过去时间",
                saved.getNextPlanTime().getTime() >= beforeSave + Integer.MAX_VALUE * 1000L);
    }

    @Test
    public void testSaveRetryTaskKeepsExplicitNextPlanTime() {
        final AtomicReference<RetryTaskDO> savedTask = new AtomicReference<>();
        RetryTaskRepo repo = proxy(new RecordingHandler(savedTask));
        MybatisAccess access = new MybatisAccess(repo);
        RetryTask task = new RetryTask();
        task.setIntervalSecond(0);
        task.setDelaySecond(10);
        task.setRetryNum(0);
        task.setOriginRetryNum(0);
        task.setStatus(0);
        task.setShardingKey(0L);
        task.setNextPlanTimeStrategy(0);
        Date explicitNextPlanTime = new Date(123456789L);
        task.setNextPlanTime(explicitNextPlanTime);

        access.saveRetryTask(task);

        RetryTaskDO saved = savedTask.get();
        Assert.assertNotNull(saved);
        Assert.assertSame("调用方显式传入的下次执行时间不应被 delaySecond 覆盖",
                explicitNextPlanTime, saved.getNextPlanTime());
    }

    private RetryTaskRepo proxy(InvocationHandler handler) {
        return (RetryTaskRepo) Proxy.newProxyInstance(
                RetryTaskRepo.class.getClassLoader(),
                new Class<?>[]{RetryTaskRepo.class},
                handler);
    }

    private static class RecordingHandler implements InvocationHandler {
        private final AtomicReference<RetryTaskDO> savedTask;

        private RecordingHandler(AtomicReference<RetryTaskDO> savedTask) {
            this.savedTask = savedTask;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("saveRetryTask".equals(method.getName())) {
                savedTask.set((RetryTaskDO) args[0]);
                return 1L;
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
