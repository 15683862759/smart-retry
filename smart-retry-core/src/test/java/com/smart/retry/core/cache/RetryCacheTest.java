package com.smart.retry.core.cache;

import com.smart.retry.common.exception.RetryException;
import com.smart.retry.common.model.RetryTaskObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryCacheTest {

    @AfterEach
    void clearCache() {
        RetryCache.clear();
    }

    @Test
    void concurrentPutAcceptsOnlyOneTaskDefinition() throws Exception {
        int threadCount = 64;
        int rounds = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int round = 0; round < rounds; round++) {
                assertOneWinnerPerRound(executor, threadCount);
            }
        } finally {
            executor.shutdownNow();
            Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void assertOneWinnerPerRound(ExecutorService executor, int threadCount) throws Exception {
        RetryCache.clear();
        String taskCode = "concurrent-task";
        Set<RetryTaskObject> definitions = new HashSet<>();
        AtomicInteger acceptedCount = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        for (int i = 0; i < threadCount; i++) {
            definitions.add(RetryTaskObject.of());
        }
        Assertions.assertEquals(threadCount, definitions.size());

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (RetryTaskObject definition : definitions) {
            tasks.add(() -> {
                barrier.await();
                try {
                    RetryCache.put(taskCode, definition);
                    acceptedCount.incrementAndGet();
                    return true;
                } catch (RetryException ignored) {
                    return false;
                }
            });
        }
        executor.invokeAll(tasks);

        Assertions.assertEquals(1, acceptedCount.get(),
                "Concurrent registration must atomically accept one task definition");
        RetryTaskObject cachedDefinition = RetryCache.get(taskCode);
        Assertions.assertTrue(definitions.contains(cachedDefinition),
                "Only one submitted definition may be cached");
    }
}
