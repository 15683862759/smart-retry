package com.smart.retry.core;

import com.smart.retry.common.exception.RetryException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @Author xiaoqiang
 * @Version ShardingIndex.java, v 0.1 2025年02月15日 21:42 xiaoqiang
 * @Description: 当前实例分片上下文。维护本实例接管的全部分片索引，
 * 使用读写锁保证心跳更新分片与任务读写分片列表之间的一致性。
 */
public class ShardingContextHolder {


    private static Set<Long> shardingIndexSet = new TreeSet<>();
    //private static AtomicLong totalIndex = new AtomicLong(0);


    private static ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private static ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();

    private static ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();


    /**
     * 获取当前实例接管的全部分片。
     *
     * @return 分片快照列表；多线程读取期间通过读锁避免与初始化并发修改
     */
    public static List<Long> shardingIndex() {
        try {
            readLock.lock();
            return new ArrayList<>(shardingIndexSet);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 心跳或故障转移后重建本实例分片列表。
     *
     * @param shardingIndexList 最新的分片索引集合
     */
    public static void initShardingIndex(List<Long> shardingIndexList) {
        try {
            writeLock.lock();
            shardingIndexSet.clear();
            shardingIndexSet.addAll(shardingIndexList);

        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 默认只取最小的一个分区, 避免多个一个机器的任务分配到多个分区
     *
     * @return 最小分片索引；未初始化时抛出 RetryException，阻止创建无主任务
     */
    public static Long getRandomShardingIndex() {

        try {
            readLock.lock();
            if (shardingIndexSet.isEmpty()) {
                throw new RetryException("sharding index is not init");
            }
            return shardingIndex().get(0);
        } finally {
            readLock.unlock();
        }
    }

}
