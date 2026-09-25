package com.smart.retry.mybatis.heart;

import com.smart.retry.common.RetryTaskHeart;
import com.smart.retry.common.SmartRetryExit;
import com.smart.retry.core.ShardingContextHolder;
import com.smart.retry.core.config.SmartExecutorConfigure;
import com.smart.retry.mybatis.entity.RetryShardingDO;
import com.smart.retry.mybatis.repo.RetryShardingRepo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author xiaoqiang
 * @Version MybatisHeart.java, v 0.1 2025年02月15日 22:19 xiaoqiang
 * @Description: MyBatis 分片心跳管理器。负责当前实例分片初始化、持续心跳，
 * 并周期性抢占心跳超时的死分片，实现多实例故障转移。
 */
public class MybatisHeart implements RetryTaskHeart {

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisHeart.class);

    private RetryShardingRepo retryShardingRepo;

    private String instanceId;

    private SmartExecutorConfigure smartExecutorConfigure;

    private volatile Thread heartbeatThread;

    private volatile Thread scrambleDeadShardingThread;




    public MybatisHeart(RetryShardingRepo retryShardingRepo,
                        String instanceId,
                        SmartExecutorConfigure smartExecutorConfigure) {
        this.retryShardingRepo = retryShardingRepo;

        this.instanceId = instanceId;
        this.smartExecutorConfigure = smartExecutorConfigure;
    }


    /**
     * 初始化当前实例的分片归属。
     *
     * <p>实现过程：
     * 1. 先刷新实例已有分片心跳，避免正常重启期间分片被误判为死分片；
     * 2. 若实例没有分片记录，则注册新分片并回读数据库生成的主键；
     * 3. 将实例名下所有分片 ID 写入 ShardingContextHolder，供任务扫描和执行校验使用。
     */
    @Override
    public void initHeart() {
        String instanceId = getInstanceId();
        //1.初始化时先更新当前实例的心跳
        retryShardingRepo.updateLastHeartbeat(instanceId, 1);

        //2.初始化sharding
        List<RetryShardingDO> retryShardingDOS = retryShardingRepo.selectByInstanceId(instanceId);
        if (CollectionUtils.isEmpty(retryShardingDOS)) {
            RetryShardingDO retryShardingDO = new RetryShardingDO();
            retryShardingDO.setCreatorId(instanceId);
            retryShardingDO.setInstanceId(instanceId);
            retryShardingDO.setLastHeartbeat(new Date());
            retryShardingRepo.saveRetrySharding(retryShardingDO);
            retryShardingDOS = retryShardingRepo.selectByInstanceId(instanceId);
        }
        List<Long> existShardingIds = retryShardingDOS.stream()
                .map(RetryShardingDO::getId)
                .collect(Collectors.toList());
        ShardingContextHolder.initShardingIndex(existShardingIds);
    }


    /**
     * 心跳后台任务。按配置间隔刷新分片心跳；线程被中断时恢复中断标记并退出。
     */
    class HeartbeatTask implements Runnable {
        @Override
        public void run() {
            String instanceId = getInstanceId();

            int interval = smartExecutorConfigure.getHealth().getInterval();

            while (SmartRetryExit.isExit()) {
                try {
                    TimeUnit.SECONDS.sleep(interval);
                    int heartBeatCount = retryShardingRepo.updateLastHeartbeat(instanceId, 1);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[MybatisHeart#heartBeat] heart beat success, instanceId:{}, heartBeatCount:{}", instanceId, heartBeatCount);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    LOGGER.error("[MybatisHeart#heartBeat] heart beat error，instanceId:{}", instanceId, e);
                }
            }
        }
    }

    /**
     * 死分片扫描任务。按扫描间隔抢占超时分片，并在归属变化后同步内存分片列表。
     */
    class ScrambleDeadShardingTask implements Runnable {
        @Override
        public void run() {
            String instanceId = getInstanceId();

            int timeout = smartExecutorConfigure.getHealth().getTimeout();
            int scanInterval = smartExecutorConfigure.getHealth().getScanInterval();
            while (SmartRetryExit.isExit()) {
                try {
                    TimeUnit.SECONDS.sleep(scanInterval);
                    int shardingCount = retryShardingRepo.scrambleDeadSharding(instanceId, 1,timeout);
                    List<RetryShardingDO> retryShardingDOS = retryShardingRepo.selectByInstanceId(instanceId);
                    if (CollectionUtils.isEmpty(retryShardingDOS)) {
                        initHeart();
                        continue;
                    }
                    if(shardingCount > 0){
                        List<Long> existShardingIds = retryShardingDOS.stream().map(retryShardingDO -> {
                            return retryShardingDO.getId();
                        }).collect(Collectors.toList());
                        ShardingContextHolder.initShardingIndex(existShardingIds);
                    }

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[MybatisHeart#scrambleDeadSharding] scrambleDeadSharding success, instanceId:{}, shardingCount:{}", instanceId, shardingCount);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    LOGGER.error("[MybatisHeart#scrambleDeadSharding] scrambleDeadSharding error，instanceId:{}", instanceId, e);
                }
            }
        }
    }

    /**
     * 获取当前实例 ID。
     *
     * @return 初始化时绑定的实例 ID
     */
    private String getInstanceId() {
        //String instanceId = IpUtils.getIp()+":"+port;
        return instanceId;
    }

    @Override
    /**
     * 启动心跳后台线程。重复调用时保留首个线程，避免产生多个心跳循环。
     */
    public void heartBeat() {
        if (heartbeatThread != null) {
            return;
        }
        Thread thread = new Thread(new HeartbeatTask());
        thread.setName("smart-retry-heartbeat");
        thread.setDaemon(true);
        heartbeatThread = thread;
        thread.start();

    }

    /**
     * 启动死分片扫描后台线程。重复调用时保留首个线程，避免重复抢占。
     */
    @Override
    public void scrambleDeadSharding() {
        if (scrambleDeadShardingThread != null) {
            return;
        }
        Thread thread = new Thread(new ScrambleDeadShardingTask());
        thread.setName("smart-retry-scramble");
        thread.setDaemon(true);
        scrambleDeadShardingThread = thread;
        thread.start();
    }

    /**
     * 中断并释放心跳与死分片扫描线程。
     */
    @Override
    public synchronized void stop() {
        stopThread(heartbeatThread);
        heartbeatThread = null;
        stopThread(scrambleDeadShardingThread);
        scrambleDeadShardingThread = null;
    }

    private void stopThread(Thread thread) {
        if (thread != null) {
            thread.interrupt();
        }
    }
}
