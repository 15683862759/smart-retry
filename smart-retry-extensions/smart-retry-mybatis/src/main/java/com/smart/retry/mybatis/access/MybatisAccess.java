package com.smart.retry.mybatis.access;

import com.smart.retry.common.RetryTaskAccess;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.mybatis.entity.RetryTaskDO;
import com.smart.retry.mybatis.repo.RetryTaskRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author xiaoqiang
 * @Version MybatisAccess.java, v 0.1 2025年02月15日 22:13 xiaoqiang
 * @Description: MyBatis 持久化访问适配器。将核心引擎使用的 RetryTask 领域模型
 * 转换为 MyBatis DO，并封装任务保存、查询、原子状态迁移、死信复活和清理操作。
 */
public class MybatisAccess implements RetryTaskAccess {


    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisAccess.class);


    private RetryTaskRepo retryTaskRepo;

    public MybatisAccess(RetryTaskRepo retryTaskRepo) {
        this.retryTaskRepo = retryTaskRepo;
    }


    @Override
    /**
     * 查询 RUNNING 状态且执行时间超过阈值的疑似死信任务。
     *
     * @param maxExecuteTime 最大执行时长秒数
     * @return 疑似死信任务列表；查询失败时由调用方记录并等待下一轮扫描
     */
    public List<RetryTask> listDeadTask(int maxExecuteTime) {
        long currentTime = System.currentTimeMillis();

        Date deadTaskTime = new Date(currentTime - maxExecuteTime * 1000L);

        List<RetryTaskDO> retryTaskDOS = retryTaskRepo.listAllDeadTask(deadTaskTime);
        if (CollectionUtils.isEmpty(retryTaskDOS)) {
            return Collections.emptyList();
        }
        List<RetryTask> retryTasks = new ArrayList<>(retryTaskDOS.size());
        for (RetryTaskDO retryTask : retryTaskDOS) {
            RetryTask retryTaskDo = new RetryTask();
            BeanUtils.copyProperties(retryTask, retryTaskDo);
            retryTasks.add(retryTaskDo);
        }
        return retryTasks;
    }


    /**
     * 按主键查询单个任务。
     *
     * @param taskId 任务 ID
     * @return 任务领域对象；不存在时返回 null
     */
    public RetryTask getRetryTask(long taskId) {
        RetryTaskDO retryTask = retryTaskRepo.getRetryTask(taskId);
        if (retryTask == null) {
            return null;
        }
        RetryTask retryTaskDo = new RetryTask();
        BeanUtils.copyProperties(retryTask, retryTaskDo);
        return retryTaskDo;
    }
    @Override
    /**
     * 查询当前实例分片中的全部可执行任务。
     *
     * @return 可执行任务领域对象列表
     */
    public List<RetryTask> listRetryTask() {
        List<RetryTaskDO> retryTaskDOS = retryTaskRepo.listAllWaitingRetryTask();
        if (CollectionUtils.isEmpty(retryTaskDOS)) {
            return Collections.emptyList();
        }
        List<RetryTask> retryTasks = new ArrayList<>(retryTaskDOS.size());
        for (RetryTaskDO retryTask : retryTaskDOS) {
            RetryTask retryTaskDo = new RetryTask();
            BeanUtils.copyProperties(retryTask, retryTaskDo);
            retryTasks.add(retryTaskDo);
        }
        return retryTasks;
    }

    @Override
    /**
     * 查询预加载窗口内到期的可执行任务。
     *
     * @param maxNextPlanTime 执行时间上界
     * @param limit           查询上限
     * @return 可执行任务领域对象列表
     */
    public List<RetryTask> listRetryTask(Date maxNextPlanTime, int limit) {
        List<RetryTaskDO> retryTaskDOS = retryTaskRepo.listAllWaitingRetryTask(maxNextPlanTime, limit);
        if (CollectionUtils.isEmpty(retryTaskDOS)) {
            return Collections.emptyList();
        }
        List<RetryTask> retryTasks = new ArrayList<>(retryTaskDOS.size());
        for (RetryTaskDO retryTask : retryTaskDOS) {
            RetryTask retryTaskDo = new RetryTask();
            BeanUtils.copyProperties(retryTask, retryTaskDo);
            retryTasks.add(retryTaskDo);
        }
        return retryTasks;
    }

    @Override
    /**
     * 保存重试任务。未显式设置下次执行时间时，按延迟秒数计算；
     * 重复任务由仓库层返回 -1。
     *
     * @param retryTask 待保存任务
     * @return 新任务 ID；重复任务返回 -1
     */
    public long saveRetryTask(RetryTask retryTask) {

        if (retryTask.getNextPlanTime() == null) {
            long nextTime = System.currentTimeMillis() + retryTask.getDelaySecond() * 1000L;
            retryTask.setNextPlanTime(new Date(nextTime));
        }

        RetryTaskDO retryTaskDO = new RetryTaskDO();
        BeanUtils.copyProperties(retryTask, retryTaskDO);
       return retryTaskRepo.saveRetryTask(retryTaskDO);
    }

    @Override
    /**
     * 管理端按主键更新任务。该方法不校验执行租约，不能替代 CAS 状态迁移。
     *
     * @param retryTask 待更新任务
     */
    public void updateRetryTask(RetryTask retryTask) {
        RetryTaskDO retryTaskDO = new RetryTaskDO();
        BeanUtils.copyProperties(retryTask, retryTaskDO);
        retryTaskRepo.updateRetryTask(retryTaskDO);

    }

    @Override
    public int claimRetryTask(Long id, String executor, Date nextPlanTime, Long shardingKey) {
        // 仅设置认领所需字段，委托 Repo 执行单条原子 UPDATE（乐观锁 CAS）
        RetryTaskDO retryTaskDO = new RetryTaskDO();
        retryTaskDO.setId(id);
        retryTaskDO.setExecutor(executor);
        retryTaskDO.setNextPlanTime(nextPlanTime);
        retryTaskDO.setShardingKey(shardingKey);
        return retryTaskRepo.claimRetryTask(retryTaskDO);
    }

    @Override
    public int markRetryTaskTerminal(Long id, int status, String executor, int retryNum, Date nextPlanTime, String attribute) {
        // 仅设置终态写入所需字段，委托 Repo 执行单条原子 UPDATE（乐观锁 CAS 守卫）
        RetryTaskDO retryTaskDO = new RetryTaskDO();
        retryTaskDO.setId(id);
        retryTaskDO.setStatus(status);
        retryTaskDO.setExecutor(executor);
        retryTaskDO.setRetryNum(retryNum);
        retryTaskDO.setNextPlanTime(nextPlanTime);
        retryTaskDO.setAttribute(attribute);
        return retryTaskRepo.markRetryTaskTerminal(retryTaskDO);
    }

    @Override
    /**
     * 条件化把未注册 taskCode 的任务标记为失败，保留原下次执行时间。
     *
     * @param id         任务 ID
     * @param executor   本次执行租约
     * @param retryNum   扣减前剩余重试次数
     * @param attribute  失败原因
     * @return 受影响行数：1=成功，0=状态已漂移或已被认领
     */
    public int markNullTaskObjectFail(Long id, String executor, int retryNum, String attribute) {
        return markNullTaskObjectFail(id, executor, retryNum, null, attribute);
    }

    @Override
    public int markNullTaskObjectFail(Long id, String executor, int retryNum,
                                      Date nextPlanTime, String attribute) {
        // 仅设置"未注册 taskCode"失败标记所需字段，委托 Repo 执行单条原子 UPDATE（乐观锁 CAS 守卫）
        RetryTaskDO retryTaskDO = new RetryTaskDO();
        retryTaskDO.setId(id);
        retryTaskDO.setExecutor(executor);
        retryTaskDO.setRetryNum(retryNum);
        retryTaskDO.setNextPlanTime(nextPlanTime);
        retryTaskDO.setAttribute(attribute);
        return retryTaskRepo.markNullTaskObjectFail(retryTaskDO);
    }

    @Override
    public int renewExecutionLease(Long id, String executor) {
        // 委托 Repo 执行带租约守卫的原子心跳续期。
        return retryTaskRepo.renewExecutionLease(id, executor);
    }

    @Override
    public int reviveDeadRetryTask(Long id, Date deadTaskTime) {
        // 单条原子 UPDATE（乐观锁 CAS 守卫）：仅复活 RUNNING 且确认超时的任务
        return retryTaskRepo.reviveDeadRetryTask(id, deadTaskTime);
    }

    @Override
    /**
     * 删除任务。
     *
     * @param taskId 任务 ID
     */
    public void deleteRetryTask(long taskId) {
        retryTaskRepo.deleteRetryTask(taskId);
    }

    @Override
    /**
     * 停止任务。仅允许非终态任务被停止，成功后不再继续调度。
     *
     * @param taskId 任务 ID
     */
    public void stopRetryTask(long taskId) {
        retryTaskRepo.stopRetryTask(taskId);
    }

    @Override
    /**
     * 分批删除指定天数前的成功任务。
     *
     * @param clearBeforeDays 保留天数
     * @param limitRows       单批删除上限
     * @return 实际删除总数
     */
    public int deleteHistoryRetryTask(int clearBeforeDays, int limitRows) {
        Date clearBeforeDate = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(clearBeforeDays));

        return retryTaskRepo.deleteByGmtCreate(clearBeforeDate, limitRows, RetryTaskStatus.SUCCESS.getCode());

    }

    @Override
    /**
     * 重启失败任务，恢复剩余重试次数和下次执行时间。
     *
     * @param taskId         任务 ID
     * @param targetRetryNum 重启后的剩余重试次数
     * @param nextPlanTime   重启后的下次执行时间
     * @return 受影响行数
     */
    public int restartRetryTask(long taskId, int targetRetryNum, Date nextPlanTime) {
        return retryTaskRepo.restartRetryTask(taskId, targetRetryNum, nextPlanTime);
    }
}
