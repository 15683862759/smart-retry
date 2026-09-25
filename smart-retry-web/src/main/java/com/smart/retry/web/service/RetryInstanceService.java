package com.smart.retry.web.service;

import com.smart.retry.common.utils.IpUtils;
import com.smart.retry.web.dao.WebRetryShardingDao;
import com.smart.retry.web.entity.RetryShardingDO;
import com.smart.retry.web.dto.PageResult;
import com.smart.retry.web.dto.instance.InstanceQueryRequest;
import com.smart.retry.web.dto.instance.InstanceUpdateRequest;
import com.smart.retry.web.dto.instance.InstanceVO;
import com.smart.retry.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 实例管理服务。
 *
 * <p>负责分片实例的查询、实例地址修改和删除。
 * 删除操作必须与活跃任务联动校验，避免删除分片后产生无人调度的悬挂任务。
 */
@Service
@RequiredArgsConstructor
public class RetryInstanceService {


    private static final Logger log = LoggerFactory.getLogger(RetryInstanceService.class);
    
    private final WebRetryShardingDao webRetryShardingDao;
    
    /**
     * 分页查询实例分片列表。
     *
     * @param request 查询请求，支持创建者和实例 ID 过滤
     * @return 实例分页结果
     */
    public PageResult<InstanceVO> queryInstances(InstanceQueryRequest request) {
        // 查询总数
        long total = webRetryShardingDao.countAll(request.getCreatorId(), request.getInstanceId());
        
        if (total == 0) {
            return new PageResult<>(new ArrayList<>(), 0L, request.getPageNum(), request.getPageSize());
        }
        
        // 查询列表
        List<RetryShardingDO> doList = webRetryShardingDao.selectAllWithPage(
                request.getOffset(), 
                request.getPageSize(),
                request.getCreatorId(),
                request.getInstanceId()
        );
        
        List<InstanceVO> voList = new ArrayList<>();
        for (RetryShardingDO shardingDO : doList) {
            InstanceVO vo = new InstanceVO();
            BeanUtils.copyProperties(shardingDO, vo);
            voList.add(vo);
        }
        
        return new PageResult<>(voList, total, request.getPageNum(), request.getPageSize());
    }
    
    /**
     * 更新实例地址。
     *
     * <p>地址必须符合 ip:port 格式，防止管理端写入无法识别的实例标识。
     *
     * @param request 更新请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateInstance(InstanceUpdateRequest request) {
        // 校验instanceId格式
        if (!IpUtils.isIPLegal(request.getInstanceId())) {
            throw new BusinessException(400, "instanceId必须是ip:port格式，例如：192.168.1.100:8080");
        }
        
        int result = webRetryShardingDao.updateInstanceId(request.getId(), request.getInstanceId());
        if (result == 0) {
            throw new BusinessException("更新失败，实例不存在");
        }
        
        log.info("[InstanceService#updateInstance]更新实例成功，id: {}, instanceId: {}", request.getId(), request.getInstanceId());
    }
    
    /**
     * 删除实例分片。
     *
     * <p>删除和任务校验由同一条 SQL 完成，避免两次数据库操作之间
     * 新任务写入导致分片被删除后任务悬挂。
     *
     * @param id 分片 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteInstance(Long id) {
        // 查询分片信息
        RetryShardingDO shardingDO = webRetryShardingDao.selectById(id);
        if (shardingDO == null) {
            throw new BusinessException("实例不存在");
        }
            
        // 删除和活跃任务校验在同一条 SQL 中完成，避免校验与删除之间新任务写入造成悬挂数据。
        int result = webRetryShardingDao.deleteByIdWhenNoUndeletableTasks(id);
        if (result == 0) {
            throw new BusinessException("该实例下存在待执行、执行中或可重试的任务，无法删除。请先处理相关任务。");
        }
            
        log.info("[InstanceService#deleteInstance]删除实例成功，id: {}, instanceId: {}", id, shardingDO.getInstanceId());
    }
}
