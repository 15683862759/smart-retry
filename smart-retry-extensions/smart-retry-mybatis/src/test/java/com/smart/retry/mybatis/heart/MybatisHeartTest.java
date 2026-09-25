package com.smart.retry.mybatis.heart;

import com.smart.retry.core.config.SmartExecutorConfigure;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MybatisHeartTest {

    @Test
    public void testConcurrentHeartBeatStartsSingleThread() throws Exception {
        MybatisHeart heart = new MybatisHeart(null, "test-instance", new SmartExecutorConfigure());
        try {
            assertConcurrentStartStartsSingleThread(heart::heartBeat, "smart-retry-heartbeat");
        } finally {
            heart.stop();
        }
    }

    @Test
    public void testConcurrentScrambleStartsSingleThread() throws Exception {
        MybatisHeart heart = new MybatisHeart(null, "test-instance", new SmartExecutorConfigure());
        try {
            assertConcurrentStartStartsSingleThread(heart::scrambleDeadSharding, "smart-retry-scramble");
        } finally {
            heart.stop();
        }
    }

    private static void assertConcurrentStartStartsSingleThread(Runnable starter, String threadName) throws Exception {
        int callerCount = 64;
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        try {
            CountDownLatch ready = new CountDownLatch(callerCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(callerCount);
            for (int i = 0; i < callerCount; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    starter.run();
                    finished.countDown();
                    return null;
                });
            }

            Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Assert.assertTrue(finished.await(5, TimeUnit.SECONDS));
            waitForNamedThread(threadName);

            Assert.assertEquals("并发启动时只应创建一个后台线程：" + threadName,
                    1, countNamedThreads(threadName));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void waitForNamedThread(String threadName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (countNamedThreads(threadName) == 0 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    private static int countNamedThreads(String threadName) {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (threadName.equals(thread.getName())) {
                count++;
            }
        }
        return count;
    }
}
