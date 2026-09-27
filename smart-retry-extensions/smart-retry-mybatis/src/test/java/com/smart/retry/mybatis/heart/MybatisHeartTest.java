package com.smart.retry.mybatis.heart;

import com.smart.retry.core.config.SmartExecutorConfigure;
import com.smart.retry.mybatis.entity.RetryShardingDO;
import com.smart.retry.mybatis.repo.RetryShardingRepo;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
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

    @Test
    public void testRestartDoesNotOverlapHeartbeatWhileOldThreadIsStopping() throws Exception {
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.getHealth().setInterval(1);
        BlockingShardingRepo repo = new BlockingShardingRepo();
        MybatisHeart heart = new MybatisHeart(repo, "test-instance", configure);
        try {
            heart.heartBeat();
            Assert.assertTrue(repo.awaitBlockingStarted());

            // 旧线程卡在不可中断的仓库调用时，stop() 后不能立刻创建第二个心跳线程。
            heart.stop();
            heart.heartBeat();
            TimeUnit.MILLISECONDS.sleep(100);

            Assert.assertEquals("旧心跳线程未退出前不应创建新的心跳线程",
                    1, countNamedThreads("smart-retry-heartbeat"));
        } finally {
            repo.release();
            heart.stop();
            waitForNamedThreadExit("smart-retry-heartbeat");
        }
    }

    @Test
    public void testRestartDoesNotOverlapScrambleWhileOldThreadIsStopping() throws Exception {
        SmartExecutorConfigure configure = new SmartExecutorConfigure();
        configure.getHealth().setScanInterval(1);
        BlockingShardingRepo repo = new BlockingShardingRepo();
        MybatisHeart heart = new MybatisHeart(repo, "test-instance", configure);
        try {
            heart.scrambleDeadSharding();
            Assert.assertTrue(repo.awaitBlockingStarted());

            heart.stop();
            heart.scrambleDeadSharding();
            TimeUnit.MILLISECONDS.sleep(100);

            Assert.assertEquals("旧死分片扫描线程未退出前不应创建新的扫描线程",
                    1, countNamedThreads("smart-retry-scramble"));
        } finally {
            repo.release();
            heart.stop();
            waitForNamedThreadExit("smart-retry-scramble");
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

    private static void waitForNamedThreadExit(String threadName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (countNamedThreads(threadName) > 0 && System.currentTimeMillis() < deadline) {
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

    private static class BlockingShardingRepo implements RetryShardingRepo {
        private final CountDownLatch blockingStarted = new CountDownLatch(1);
        private volatile boolean released;

        @Override
        public long saveRetrySharding(RetryShardingDO retrySharding) {
            return 0;
        }

        @Override
        public int updateLastHeartbeat(String instanceId, int status) {
            blockingStarted.countDown();
            blockUntilReleased();
            return 1;
        }

        @Override
        public int scrambleDeadSharding(String instanceId, int status, int timeout) {
            blockingStarted.countDown();
            blockUntilReleased();
            return 0;
        }

        private void blockUntilReleased() {
            synchronized (this) {
                while (!released) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                        // 模拟不响应中断的数据库 IO，覆盖 stop 后立刻重启的边界。
                    }
                }
            }
        }

        @Override
        public List<RetryShardingDO> selectByInstanceId(String instanceId) {
            return Collections.emptyList();
        }

        boolean awaitBlockingStarted() throws InterruptedException {
            return blockingStarted.await(5, TimeUnit.SECONDS);
        }

        void release() {
            synchronized (this) {
                released = true;
                notifyAll();
            }
        }
    }
}
