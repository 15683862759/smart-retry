<template>
  <div class="dashboard">
    <h2>整体分布监控</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card class="stat-card-wrapper">
          <div class="stat-card">
            <div class="stat-title">活跃实例数</div>
            <div class="stat-value">{{ dashboardData.activeInstanceCount || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card class="stat-card-wrapper">
          <div class="stat-card">
            <div class="stat-title">任务处理速率</div>
            <div class="stat-value">{{ dashboardData.taskProcessRate?.toFixed(2) || 0 }}/分钟</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card class="stat-card-wrapper">
          <div class="stat-card">
            <div class="stat-title">待执行任务</div>
            <div class="stat-value">{{ dashboardData.taskStatusDistribution?.[0] || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 — 第一行 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :sm="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div>任务状态分布</div>
          </template>
          <div ref="statusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div>分片分布</div>
          </template>
          <div ref="shardingChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 — 第二行 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :sm="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div>死信任务趋势（24小时）</div>
          </template>
          <div ref="deadLetterChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div>任务类型积压量</div>
          </template>
          <div ref="backlogChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实例心跳列表 -->
    <el-card style="margin-top: 20px;" class="table-card">
      <template #header>
        <div>实例心跳监控</div>
      </template>
      <div class="table-scroll">
        <el-table :data="dashboardData.instanceHeartbeats || []" border size="small">
          <el-table-column prop="instanceId" label="实例ID" min-width="180" />
          <el-table-column prop="lastHeartbeat" label="最后心跳时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.lastHeartbeat) }}
            </template>
          </el-table-column>
          <el-table-column prop="heartbeatDelay" label="心跳延迟(秒)" width="120">
            <template #default="{ row }">
              <el-tag :type="getDelayType(row.heartbeatDelay)">
                {{ row.heartbeatDelay }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getDashboardData } from '@/api'
import { ElMessage } from 'element-plus'

const dashboardData = ref({})
const statusChartRef = ref(null)
const shardingChartRef = ref(null)
const deadLetterChartRef = ref(null)
const backlogChartRef = ref(null)

let statusChart = null
let shardingChart = null
let deadLetterChart = null
let backlogChart = null
let refreshTimer = null
let resizeTimer = null

// 获取仪表盘数据
const fetchDashboardData = async () => {
  try {
    const data = await getDashboardData()
    dashboardData.value = data
    updateCharts()
  } catch (error) {
    ElMessage.error('获取仪表盘数据失败')
  }
}

// 更新图表
const updateCharts = () => {
  updateStatusChart()
  updateShardingChart()
  updateDeadLetterChart()
  updateBacklogChart()
}

// 任务状态分布图
const updateStatusChart = () => {
  if (!statusChart) return

  const statusMap = dashboardData.value.taskStatusDistribution || {}
  const option = {
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['55%', '50%'],
      data: [
        { value: statusMap[0] || 0, name: '待执行' },
        { value: statusMap[1] || 0, name: '执行中' },
        { value: statusMap[2] || 0, name: '成功' },
        { value: statusMap[3] || 0, name: '失败' }
      ],
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }]
  }
  statusChart.setOption(option, true)
}

// 分片分布图
const updateShardingChart = () => {
  if (!shardingChart) return

  const distribution = dashboardData.value.shardingDistribution || {}
  const data = Object.entries(distribution).map(([name, value]) => ({ name, value }))

  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.name), axisLabel: { rotate: data.length > 5 ? 30 : 0 } },
    yAxis: { type: 'value' },
    series: [{
      data: data.map(d => d.value),
      type: 'bar',
      itemStyle: { borderRadius: [4, 4, 0, 0] }
    }]
  }
  shardingChart.setOption(option, true)
}

// 死信任务趋势图
const updateDeadLetterChart = () => {
  if (!deadLetterChart) return

  const trend = dashboardData.value.deadLetterTrend || []
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: trend.map(t => t.timePoint),
      axisLabel: { rotate: trend.length > 10 ? 30 : 0 }
    },
    yAxis: { type: 'value' },
    series: [{
      data: trend.map(t => t.count),
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 }
    }]
  }
  deadLetterChart.setOption(option, true)
}

// 任务类型积压量图
const updateBacklogChart = () => {
  if (!backlogChart) return

  const backlog = dashboardData.value.taskBacklogByType || {}
  const data = Object.entries(backlog).map(([name, value]) => ({ name, value }))

  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.name), axisLabel: { rotate: data.length > 5 ? 30 : 0 } },
    yAxis: { type: 'value' },
    series: [{
      data: data.map(d => d.value),
      type: 'bar',
      itemStyle: { borderRadius: [4, 4, 0, 0] }
    }]
  }
  backlogChart.setOption(option, true)
}

// 初始化图表
const initCharts = () => {
  if (statusChartRef.value) {
    statusChart = echarts.init(statusChartRef.value)
  }
  if (shardingChartRef.value) {
    shardingChart = echarts.init(shardingChartRef.value)
  }
  if (deadLetterChartRef.value) {
    deadLetterChart = echarts.init(deadLetterChartRef.value)
  }
  if (backlogChartRef.value) {
    backlogChart = echarts.init(backlogChartRef.value)
  }
}

// 窗口大小变化时重绘所有图表（防抖）
const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    if (statusChart) statusChart.resize()
    if (shardingChart) shardingChart.resize()
    if (deadLetterChart) deadLetterChart.resize()
    if (backlogChart) backlogChart.resize()
  }, 150)
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

// 获取延迟类型
const getDelayType = (delay) => {
  if (delay < 10) return 'success'
  if (delay < 30) return 'warning'
  return 'danger'
}

onMounted(() => {
  initCharts()
  fetchDashboardData()
  // 每30秒刷新一次
  refreshTimer = setInterval(fetchDashboardData, 30000)
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  if (resizeTimer) {
    clearTimeout(resizeTimer)
  }
  window.removeEventListener('resize', handleResize)
  if (statusChart) statusChart.dispose()
  if (shardingChart) shardingChart.dispose()
  if (deadLetterChart) deadLetterChart.dispose()
  if (backlogChart) backlogChart.dispose()
})
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.stat-card-wrapper {
  margin-bottom: 0;
}

.stat-card {
  text-align: center;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 10px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

/* 响应式调整 */

/* 平板及以下 */
@media (max-width: 992px) {
  .chart-container {
    height: 250px;
  }
}

/* 手机端 */
@media (max-width: 768px) {
  .stat-card-wrapper {
    margin-bottom: 10px;
  }

  .stat-value {
    font-size: 24px;
  }

  .chart-card {
    margin-bottom: 10px;
  }

  .chart-container {
    height: 220px;
  }

  .dashboard h2 {
    font-size: 18px;
  }
}
</style>