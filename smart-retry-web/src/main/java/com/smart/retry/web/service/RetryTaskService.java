package com.smart.retry.web.service;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.smart.retry.common.RetryTaskEnqueuer;
import com.smart.retry.common.constant.RetryTaskStatus;
import com.smart.retry.common.identifier.Identifier;
import com.smart.retry.common.model.RetryTask;
import com.smart.retry.common.serializer.SmartSerializer;
import com.smart.retry.web.dao.WebRetryShardingDao;
import com.smart.retry.web.dao.WebRetryTaskDao;
import com.smart.retry.web.entity.RetryShardingDO;
import com.smart.retry.web.entity.RetryTaskDO;
import com.smart.retry.web.entity.query.RetryTaskQuery;
import com.smart.retry.web.dto.PageResult;
import com.smart.retry.web.dto.task.*;
import com.smart.retry.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 任务管理服务。
 *
 * <p>负责任务查询、人工创建、更新、删除和分片选项查询。
 * 写操作使用事务包裹，并与调度器通过 common 接口解耦，
 * 确保 Web 模块不直接依赖 core 的具体实现。
 */
@Service
@RequiredArgsConstructor
public class RetryTaskService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RetryTaskService.class);
    
    private final WebRetryTaskDao webRetryTaskDao;
    private final WebRetryShardingDao webRetryShardingDao;
    private final ObjectProvider<RetryTaskEnqueuer> retryTaskEnqueuerProvider;
    
    private static final Gson GSON = new Gson();
    
    /**
     * 分页查询任务列表。
     *
     * <p>实现过程：
     * 1. 将请求转换为 DAO 查询条件；
     * 2. 先查询总数，总数为 0 时直接返回空页；
     * 3. 查询任务列表并按分片 ID 去重查询分片信息，避免 N+1；
     * 4. 将 DO 转为 VO 并补充实例与分片 ID 的展示文本。
     *
     * @param request 查询请求，包含分页和过滤条件
     * @return 任务分页结果
     */
    public PageResult<TaskVO> queryTasks(TaskQueryRequest request) {
        // 构建查询条件
        RetryTaskQuery query = new RetryTaskQuery();
        query.setOffset(request.getOffset());
        query.setLimit(request.getPageSize());
        query.setId(request.getId());
        query.setTaskCode(request.getTaskCode());
        query.setStatus(request.getStatus());
        query.setShardingKeyList(request.getShardingKey() != null ? 
                Collections.singletonList(request.getShardingKey()) : null);
        query.setMinGmtCreate(request.getGmtCreateStart());
        query.setMaxGmtCreate(request.getGmtCreateEnd());
        
        // 查询总数
        int total = webRetryTaskDao.countByQuery(query);
        
        if (total == 0) {
            return new PageResult<>(new ArrayList<>(), 0L, request.getPageNum(), request.getPageSize());
        }
        
        // 查询列表
        List<RetryTaskDO> doList = webRetryTaskDao.selectByQuery(query);
        
        // 批量查询分片信息，避免 N+1 查询
        Map<Long, RetryShardingDO> shardingMap = new HashMap<>();
        for (RetryTaskDO taskDO : doList) {
            long shardingKey = taskDO.getShardingKey();
            if (!shardingMap.containsKey(shardingKey)) {
                RetryShardingDO sharding = webRetryShardingDao.selectById(shardingKey);
                if (sharding != null) {
                    shardingMap.put(shardingKey, sharding);
                }
            }
        }
        
        List<TaskVO> voList = new ArrayList<>();
        for (RetryTaskDO taskDO : doList) {
            TaskVO vo = new TaskVO();
            BeanUtils.copyProperties(taskDO, vo);
            
            // 设置分片信息：ip(分片id)
            long shardingKey = taskDO.getShardingKey();
            RetryShardingDO sharding = shardingMap.get(shardingKey);
            if (sharding != null && sharding.getInstanceId() != null) {
                vo.setShardingInfo(sharding.getInstanceId() + "(" + sharding.getId() + ")");
            } else {
                vo.setShardingInfo(taskDO.getShardingKey()+"");
            }
            
            voList.add(vo);
        }
        
        return new PageResult<>(voList, (long) total, request.getPageNum(), request.getPageSize());
    }
    
    /**
     * 人工创建重试任务。
     *
     * <p>实现过程：
     * 1. 校验参数 JSON 格式和目标分片存在性；
     * 2. 组装任务实体，生成 MD5 uniqueKey 和延迟后的首次执行时间；
     * 3. 在当前事务中写入数据库；
     * 4. 若调度器存在，通过 enqueueAfterCommit 在事务提交后再入内存队列。
     *
     * @param request 创建请求
     * @return 新任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(TaskCreateRequest request) {
        // 校验JSON格式
        validateJson(request.getParam());

        // 不存在的分片不会被调度器接管，必须阻止任务落库
        if (webRetryShardingDao.selectById(request.getShardingKey()) == null) {
            throw new BusinessException(400, "分片不存在");
        }
        
        // 创建任务对象
        RetryTaskDO taskDO = new RetryTaskDO();
        taskDO.setTaskCode(request.getTaskCode());
        taskDO.setTaskDesc(request.getTaskDesc());
        taskDO.setRetryNum(request.getRetryNum());
        taskDO.setOriginRetryNum(request.getRetryNum()); // 默认和retryNum一样
        taskDO.setDelaySecond(request.getDelaySecond());
        taskDO.setIntervalSecond(request.getIntervalSecond());
        taskDO.setParameters(request.getParam());
        taskDO.setShardingKey(request.getShardingKey());
        taskDO.setNextPlanTimeStrategy(request.getNextPlanTimeStrategy());
        taskDO.setStatus(RetryTaskStatus.WAITING.getCode());
        taskDO.setUniqueKey(DigestUtils.md5Hex( taskDO.getTaskCode() +":"+ taskDO.getParameters()));
        taskDO.setCreator("custom"); // 默认人为创建

        // 计算下次执行时间：当前时间 + delaySecond
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, request.getDelaySecond());
        taskDO.setNextPlanTime(calendar.getTime());
        
        // 插入数据库
        webRetryTaskDao.insert(taskDO);

        // Web 与 core 位于不同模块，通过 common 接口解耦。
        // 无调度器时保持只落库；有调度器时延迟到事务提交后入队。
        RetryTaskEnqueuer retryTaskEnqueuer = retryTaskEnqueuerProvider.getIfAvailable();
        if (retryTaskEnqueuer != null) {
            RetryTask retryTask = new RetryTask();
            BeanUtils.copyProperties(taskDO, retryTask);
            retryTaskEnqueuer.enqueueAfterCommit(retryTask);
        }
        
        log.info("[TaskService#createTask]创建任务成功，id: {}, taskCode: {}", taskDO.getId(), taskDO.getTaskCode());
        return taskDO.getId();
    }
    
    /**
     * 更新任务的可编辑字段。
     *
     * <p>仅允许更新下次执行时间、剩余次数、参数和状态；
     * RUNNING 任务不可编辑，终态任务只有失败或成功可重置为待执行。
     *
     * @param request 更新请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(TaskUpdateRequest request) {
        // 查询当前任务
        RetryTaskDO taskDO = webRetryTaskDao.selectById(request.getId());
        if (taskDO == null) {
            throw new BusinessException("任务不存在");
        }

        // 检查任务状态，执行中的任务不能编辑
        if (RetryTaskStatus.RUNNING.getCode().equals(taskDO.getStatus())) {
            throw new BusinessException("执行中的任务无法编辑");
        }
        
        // 只允许编辑 nextPlanTime, retryNum, param, status
        if (request.getNextPlanTime() != null) {
            try {
                taskDO.setNextPlanTime(DateUtils.parseDate(request.getNextPlanTime(),
                        "yyyy-MM-dd HH:mm:ss"));
            } catch (java.text.ParseException e) {
                throw new BusinessException(400, "时间格式必须为 yyyy-MM-dd HH:mm:ss");
            }
        }
        
        if (request.getRetryNum() != null) {
            taskDO.setRetryNum(request.getRetryNum());
        }
        
        if (request.getParam() != null) {
            // 校验JSON格式
            validateJson(request.getParam());
            taskDO.setParameters(request.getParam());
        }
        
        // 处理状态更新：只有失败(3)或成功(2)的任务可以调整为待执行(0)
        if (request.getStatus() != null) {
            Integer currentStatus = taskDO.getStatus();
            Integer newStatus = request.getStatus();
            
            // 执行中状态不能被设置
            if (RetryTaskStatus.RUNNING.getCode().equals(newStatus)) {
                throw new BusinessException("不能将任务状态设置为执行中");
            }
            
            // 只有失败或成功的任务可以重置为待执行
            if (RetryTaskStatus.WAITING.getCode().equals(newStatus)) {
                if (!RetryTaskStatus.FAIL.getCode().equals(currentStatus) 
                    && !RetryTaskStatus.SUCCESS.getCode().equals(currentStatus)) {
                    throw new BusinessException("只有失败或成功的任务才能重置为待执行");
                }
            }
            
            taskDO.setStatus(newStatus);
        }
        
        int updated = webRetryTaskDao.update(taskDO);
        if (updated == 0) {
            throw new BusinessException("任务状态已变化，更新失败");
        }
        log.info("[TaskService#updateTask]更新任务成功，id: {}", request.getId());
    }
    
    /**
     * 删除任务。
     *
     * <p>RUNNING 状态任务可能正在执行，禁止删除；
     * 删除行数为 0 表示状态在读取后变化，事务回滚。
     *
     * @param id 任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        RetryTaskDO taskDO = webRetryTaskDao.selectById(id);
        if (taskDO == null) {
            throw new BusinessException("任务不存在");
        }
        
        // 执行中的任务不能删除
        if (RetryTaskStatus.RUNNING.getCode().equals(taskDO.getStatus())) {
            throw new BusinessException("执行中的任务无法删除");
        }
        
        int deleted = webRetryTaskDao.deleteById(id);
        if (deleted == 0) {
            throw new BusinessException("任务状态已变化，删除失败");
        }
        log.info("[TaskService#deleteTask]删除任务成功，id: {}", id);
    }
    
    /**
     * 批量删除任务。
     *
     * <p>先检查全部目标状态，再对 ID 去重后批量删除；
     * 删除数量与请求不一致时抛出异常并回滚整批操作。
     *
     * @param ids 任务 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteTasks(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        
        // 检查是否有执行中的任务
        for (Long id : ids) {
            RetryTaskDO taskDO = webRetryTaskDao.selectById(id);
            if (taskDO != null && RetryTaskStatus.RUNNING.getCode().equals(taskDO.getStatus())) {
                throw new BusinessException("任务ID " + id + " 正在执行中，无法删除");
            }
        }
        
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(ids));
        int deleted = webRetryTaskDao.batchDeleteByIds(distinctIds);
        if (deleted != distinctIds.size()) {
            throw new BusinessException("部分任务状态已变化，删除失败");
        }
        log.info("[TaskService#batchDeleteTasks]批量删除任务成功，数量: {}", distinctIds.size());
    }
    
    /**
     * 获取人工创建任务可选择的分片列表。
     *
     * <p>同一实例下只保留 ID 最小的分片作为默认选项，并按分片 ID 排序，
     * 让选项顺序稳定。
     *
     * @return 实例与分片选项列表
     */
    public List<ShardingOptionVO> getShardingOptions() {
        // 查询所有分片
        List<RetryShardingDO> shardingList = webRetryShardingDao.selectAllWithPage(0, 1000, null, null);
        
        // 按instanceId分组，每个instanceId取id最小的shardingKey
        Map<String, RetryShardingDO> instanceMap = new HashMap<>();
        for (RetryShardingDO sharding : shardingList) {
            String instanceId = sharding.getInstanceId();
            if (instanceId != null && !instanceId.isEmpty()) {
                if (!instanceMap.containsKey(instanceId)) {
                    instanceMap.put(instanceId, sharding);
                } else {
                    // 比较id，保留较小的
                    if (sharding.getId() < instanceMap.get(instanceId).getId()) {
                        instanceMap.put(instanceId, sharding);
                    }
                }
            }
        }
        
        // 转换为VO
        List<ShardingOptionVO> options = new ArrayList<>();
        for (Map.Entry<String, RetryShardingDO> entry : instanceMap.entrySet()) {
            ShardingOptionVO vo = new ShardingOptionVO();
            vo.setInstanceId(entry.getKey());
            vo.setShardingKey(entry.getValue().getId()); // 使用分片的id作为shardingKey
            vo.setDisplayText(entry.getKey());
            options.add(vo);
        }
        
        // 按id排序
        options.sort(Comparator.comparing(ShardingOptionVO::getShardingKey));
        
        return options;
    }
    
    /**
     * 校验参数 JSON 格式。
     *
     * @param json 参数字符串
     */
    private void validateJson(String json) {
        try {
            JsonParser.parseString(json);
        } catch (Exception e) {
            throw new BusinessException(400, "参数不是有效的JSON格式: " + e.getMessage());
        }
    }
}
