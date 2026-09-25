package com.smart.retry.web.service;

import com.smart.retry.web.dao.WebRetryShardingDao;
import com.smart.retry.web.dao.WebRetryTaskDao;
import com.smart.retry.web.dto.dashboard.DashboardVO;
import com.smart.retry.web.dto.dashboard.DeadLetterTrendVO;
import com.smart.retry.web.dto.dashboard.InstanceHeartbeatVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 仪表盘监控服务。
 *
 * <p>聚合实例心跳、分片分布、任务状态、任务积压、死信趋势和处理速率，
 * 为管理后台提供一屏式运行状态视图。
 */
@Slf4j
@Service
public class RetryDashboardService {

    private final WebRetryShardingDao webRetryShardingDao;
    private final WebRetryTaskDao webRetryTaskDao;
    private final int activeInstanceTimeoutSeconds;

    /**
     * 创建生产环境使用的仪表盘服务。
     *
     * @param webRetryShardingDao          分片DAO 必填
     * @param webRetryTaskDao              任务DAO 必填
     * @param activeInstanceTimeoutSeconds 活跃实例心跳超时时间，单位秒；
     *                                      默认取框架健康检查超时时间 240 秒
     */
    @Autowired
    public RetryDashboardService(WebRetryShardingDao webRetryShardingDao,
                                 WebRetryTaskDao webRetryTaskDao,
                                 @Value("${spring.smart-retry.health.timeout:240}") int activeInstanceTimeoutSeconds) {
        this.webRetryShardingDao = webRetryShardingDao;
        this.webRetryTaskDao = webRetryTaskDao;
        this.activeInstanceTimeoutSeconds = activeInstanceTimeoutSeconds;
    }

    /**
     * 创建使用框架默认健康检查超时时间的仪表盘服务，便于单元测试和普通实例化场景复用。
     *
     * @param webRetryShardingDao 分片DAO 必填
     * @param webRetryTaskDao     任务DAO 必填
     */
    public RetryDashboardService(WebRetryShardingDao webRetryShardingDao,
                                 WebRetryTaskDao webRetryTaskDao) {
        this(webRetryShardingDao, webRetryTaskDao, 240);
    }
    
    /**
     * 获取仪表盘监控数据。
     *
     * <p>实现过程：
     * 1. 查询活跃实例数量和分片分布；
     * 2. 转换实例心跳，并兼容 LocalDateTime 与 Date 两种驱动返回类型；
     * 3. 汇总任务状态、任务类型积压、死信趋势和处理速率。
     *
     * @return 仪表盘数据
     */
    public DashboardVO getDashboardData() {
        DashboardVO dashboard = new DashboardVO();
        
        // 1. 活跃实例数量；统计窗口必须与实例死亡判定配置保持一致，避免短时间心跳抖动被误判为下线
        dashboard.setActiveInstanceCount(webRetryShardingDao.countActiveInstances(activeInstanceTimeoutSeconds));
        
        // 2. 分片分布情况
        List<Map<String, Object>> shardingDist = webRetryShardingDao.getShardingDistribution();
        Map<String, Integer> shardingMap = new HashMap<>();
        for (Map<String, Object> item : shardingDist) {
            String instanceId = (String) item.get("instanceId");
            Number count = (Number) item.get("count");
            shardingMap.put(instanceId, count != null ? count.intValue() : 0);
        }
        dashboard.setShardingDistribution(shardingMap);
        
        // 3. 实例心跳信息
        List<Map<String, Object>> heartbeats = webRetryShardingDao.getInstanceHeartbeats();
        List<InstanceHeartbeatVO> heartbeatList = new ArrayList<>();
        for (Map<String, Object> item : heartbeats) {
            InstanceHeartbeatVO vo = new InstanceHeartbeatVO();
            vo.setInstanceId((String) item.get("instanceId"));
            // MyBatis返回的是LocalDateTime，直接设置
            Object lastHeartbeatObj = item.get("lastHeartbeat");
            if (lastHeartbeatObj instanceof LocalDateTime) {
                vo.setLastHeartbeat((LocalDateTime) lastHeartbeatObj);
            } else if (lastHeartbeatObj instanceof Date) {
                // 兼容旧版本，将Date转换为LocalDateTime
                vo.setLastHeartbeat(((Date) lastHeartbeatObj).toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime());
            }
            Number delay = (Number) item.get("heartbeatDelay");
            vo.setHeartbeatDelay(delay != null ? delay.longValue() : 0L);
            heartbeatList.add(vo);
        }
        dashboard.setInstanceHeartbeats(heartbeatList);
        
        // 4. 任务状态分布
        List<Map<String, Object>> statusDist = webRetryTaskDao.countTaskStatusDistribution();
        Map<Integer, Long> statusMap = new HashMap<>();
        for (Map<String, Object> item : statusDist) {
            Number status = (Number) item.get("status");
            Number count = (Number) item.get("count");
            statusMap.put(status != null ? status.intValue() : -1, 
                         count != null ? count.longValue() : 0L);
        }
        dashboard.setTaskStatusDistribution(statusMap);
        
        // 5. 各任务类型积压量
        List<Map<String, Object>> backlogByType = webRetryTaskDao.countTaskBacklogByType();
        Map<String, Long> backlogMap = new HashMap<>();
        for (Map<String, Object> item : backlogByType) {
            String taskCode = (String) item.get("taskCode");
            Number count = (Number) item.get("count");
            backlogMap.put(taskCode, count != null ? count.longValue() : 0L);
        }
        dashboard.setTaskBacklogByType(backlogMap);
        
        // 6. 死信任务趋势
        List<Map<String, Object>> deadLetterData = webRetryTaskDao.getDeadLetterTrend(24);
        List<DeadLetterTrendVO> trendList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Map<String, Object> item : deadLetterData) {
            DeadLetterTrendVO vo = new DeadLetterTrendVO();
            try {
                String timeStr = (String) item.get("timePoint");
                if (timeStr != null) {
                    vo.setTimePoint(sdf.parse(timeStr));
                }
            } catch (Exception e) {
                log.error("[DashboardService#getDashboardData]解析时间失败: {}", item.get("timePoint"), e);
            }
            Number count = (Number) item.get("count");
            vo.setCount(count != null ? count.longValue() : 0L);
            trendList.add(vo);
        }
        dashboard.setDeadLetterTrend(trendList);
        
        // 7. 任务处理速率
        Double rate = webRetryTaskDao.getTaskProcessRate(5);
        dashboard.setTaskProcessRate(rate != null ? rate : 0.0);
        
        return dashboard;
    }
}
