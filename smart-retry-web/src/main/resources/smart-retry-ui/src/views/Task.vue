<template>
  <div class="task-management">
    <h2>任务管理</h2>

    <!-- 搜索区域 -->
    <el-card style="margin-top: 20px;" class="search-card">
      <el-form :model="queryForm" label-width="auto">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label="任务ID">
              <el-input v-model.number="queryForm.id" placeholder="请输入任务ID" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label="任务编码">
              <el-input v-model="queryForm.taskCode" placeholder="请输入任务编码" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label="任务描述">
              <el-input v-model="queryForm.taskDesc" placeholder="请输入任务描述" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label="状态">
              <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 100%;">
                <el-option label="待执行" :value="0" />
                <el-option label="执行中" :value="1" />
                <el-option label="成功" :value="2" />
                <el-option label="失败" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="16" :lg="12">
            <el-form-item label="创建时间">
              <el-date-picker
                v-model="createTimeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="8" :lg="12">
            <el-form-item>
              <el-button type="primary" @click="handleQuery">查询</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card style="margin-top: 20px;" class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon> 新建任务
        </el-button>
        <el-button type="danger" @click="handleBatchDelete" :disabled="selectedIds.length === 0">
          批量删除
        </el-button>
      </div>

      <div class="table-scroll">
        <el-table :data="tableData" border v-loading="loading" @selection-change="handleSelectionChange" size="small">
          <el-table-column type="selection" width="45" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="taskCode" label="任务编码" min-width="130" show-overflow-tooltip />
          <el-table-column prop="taskDesc" label="任务描述" min-width="160" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="剩余重试" width="85" align="center">
            <template #default="{ row }">
              {{ row.retryNum }}
            </template>
          </el-table-column>
          <el-table-column prop="nextPlanTime" label="下次执行时间" width="160">
            <template #default="{ row }">
              {{ formatTime(row.nextPlanTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="creator" label="创建者" min-width="100" />
          <el-table-column prop="shardingInfo" label="分片信息" min-width="140" show-overflow-tooltip />
          <el-table-column prop="gmtCreate" label="创建时间" width="160">
            <template #default="{ row }">
              {{ formatTime(row.gmtCreate) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="200">
            <template #default="{ row }">
              <el-button link type="info" size="small" @click="handleView(row)">查看</el-button>
              <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          :layout="paginationLayout"
          :small="isSmallScreen"
          @size-change="handleQuery"
          @current-change="handleQuery"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" :width="dialogWidth" :fullscreen="isMobile" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" :label-position="isMobile ? 'top' : 'right'">
        <el-row :gutter="20">
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="任务编码" prop="taskCode" v-if="isCreate">
              <el-input v-model="form.taskCode" placeholder="请输入任务编码" />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="任务描述" prop="taskDesc" v-if="isCreate">
              <el-input v-model="form.taskDesc" placeholder="请输入任务描述" />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="重试次数" prop="retryNum">
              <el-input-number v-model="form.retryNum" :min="1" :max="100" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="延迟时间(秒)" prop="delaySecond" v-if="isCreate">
              <el-input-number v-model="form.delaySecond" :min="0" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="执行间隔(秒)" prop="intervalSecond" v-if="isCreate">
              <el-input-number v-model="form.intervalSecond" :min="0" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="执行策略" prop="nextPlanTimeStrategy" v-if="isCreate">
              <el-select v-model="form.nextPlanTimeStrategy" placeholder="请选择执行策略" style="width: 100%;">
                <el-option label="固定间隔" :value="1" />
                <el-option label="递增" :value="2" />
                <el-option label="斐波那契" :value="3" />
                <el-option label="退避" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="执行实例" prop="shardingKey">
              <el-select v-model="form.shardingKey" placeholder="请选择执行实例" style="width: 100%;">
                <el-option
                  v-for="item in shardingOptions"
                  :key="item.shardingKey"
                  :label="item.displayText"
                  :value="item.shardingKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12" v-if="!isCreate">
            <el-form-item label="下次执行时间" prop="nextPlanTime">
              <el-date-picker
                v-model="form.nextPlanTime"
                type="datetime"
                placeholder="选择日期时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12" v-if="!isCreate">
            <el-form-item label="任务状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择任务状态" :disabled="isStatusDisabled" style="width: 100%;">
                <el-option label="待执行" :value="0" />
                <el-option label="执行中" :value="1" :disabled="true" />
                <el-option label="成功" :value="2" />
                <el-option label="失败" :value="3" />
              </el-select>
              <div v-if="isStatusDisabled" style="color: #909399; font-size: 12px; margin-top: 4px;">
                {{ statusDisableReason }}
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="参数(JSON)" prop="param">
          <div style="position: relative; width: 100%;">
            <el-input
              v-model="form.param"
              type="textarea"
              :rows="isMobile ? 8 : 12"
              placeholder="请输入JSON格式的参数"
              class="json-textarea"
            />
            <div class="json-button-group">
              <el-button size="small" @click="formatJson" :disabled="!form.param">
                <el-icon><MagicStick /></el-icon> 格式化
              </el-button>
              <el-button size="small" @click="compressJson" :disabled="!form.param">
                <el-icon><Fold /></el-icon> 压缩
              </el-button>
              <el-button size="small" @click="validateJson" :disabled="!form.param">
                <el-icon><Check /></el-icon> 校验
              </el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看任务详情对话框 -->
    <el-dialog v-model="viewDialogVisible" title="任务详情" :width="dialogWidth" :fullscreen="isMobile" destroy-on-close>
      <el-descriptions :column="isMobile ? 1 : 2" border size="small">
        <el-descriptions-item label="任务ID">{{ viewForm.id }}</el-descriptions-item>
        <el-descriptions-item label="任务编码">{{ viewForm.taskCode }}</el-descriptions-item>
        <el-descriptions-item label="任务描述" :span="isMobile ? 1 : 2">{{ viewForm.taskDesc }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="getStatusType(viewForm.status)" size="small">
            {{ getStatusText(viewForm.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="剩余重试次数">{{ viewForm.retryNum }}</el-descriptions-item>
        <el-descriptions-item label="初始重试次数">{{ viewForm.originRetryNum }}</el-descriptions-item>
        <el-descriptions-item label="延迟时间(秒)">{{ viewForm.delaySecond }}</el-descriptions-item>
        <el-descriptions-item label="执行间隔(秒)">{{ viewForm.intervalSecond }}</el-descriptions-item>
        <el-descriptions-item label="下次执行时间">{{ formatTime(viewForm.nextPlanTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建者">{{ viewForm.creator }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(viewForm.gmtCreate) }}</el-descriptions-item>
        <el-descriptions-item label="修改时间">{{ formatTime(viewForm.gmtModified) }}</el-descriptions-item>
        <el-descriptions-item label="分片信息">{{ viewForm.shardingInfo }}</el-descriptions-item>
        <el-descriptions-item label="执行器">{{ viewForm.executor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="唯一Key">{{ viewForm.uniqueKey || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下次执行时间策略">{{ viewForm.nextPlanTimeStrategy }}</el-descriptions-item>
        <el-descriptions-item label="当前日志ID">{{ viewForm.currentLogId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="属性">{{ viewForm.attribute || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">任务参数(JSON)</el-divider>
      <div class="json-preview">
        <pre>{{ formattedViewParam }}</pre>
      </div>

      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, onUnmounted } from 'vue'
import { queryTasks, createTask, updateTask, deleteTask, batchDeleteTasks, getShardingOptions } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick, Fold, Check } from '@element-plus/icons-vue'

const MOBILE_BREAKPOINT = 768

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const isCreate = ref(true)
const formRef = ref(null)
const selectedIds = ref([])
const shardingOptions = ref([])
const createTimeRange = ref(null)
const isSmallScreen = ref(false)
const isMobile = ref(false)

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  id: null,
  taskCode: '',
  taskDesc: '',
  status: null
})

const form = reactive({
  id: null,
  taskCode: '',
  taskDesc: '',
  retryNum: 1,
  delaySecond: 100,
  intervalSecond: 600,
  nextPlanTimeStrategy: 1,
  param: '{}',
  shardingKey: null,
  nextPlanTime: null,
  status: null
})

const viewForm = reactive({
  id: null,
  taskCode: '',
  taskDesc: '',
  retryNum: 0,
  originRetryNum: 0,
  delaySecond: 0,
  intervalSecond: 0,
  param: '{}',
  shardingKey: null,
  shardingInfo: '',
  nextPlanTime: null,
  status: null,
  creator: '',
  gmtCreate: null,
  gmtModified: null,
  executor: '',
  uniqueKey: '',
  nextPlanTimeStrategy: null,
  currentLogId: null,
  attribute: ''
})

// 响应式计算
const isStatusDisabled = computed(() => form.status === 1)

const statusDisableReason = computed(() => {
  if (form.status === 1) return '执行中的任务不能修改状态'
  return ''
})

// 分页 layout 响应式：小屏去掉 sizes 和 jumper
const paginationLayout = computed(() => {
  return isSmallScreen.value
    ? 'total, prev, pager, next'
    : 'total, sizes, prev, pager, next, jumper'
})

// 弹窗宽度响应式
const dialogWidth = computed(() => {
  return isMobile.value ? '100%' : '55%'
})

const rules = {
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  taskDesc: [{ required: true, message: '请输入任务描述', trigger: 'blur' }],
  retryNum: [{ required: true, message: '请输入重试次数', trigger: 'blur' }],
  param: [
    { required: true, message: '请输入参数', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        try {
          JSON.parse(value)
          callback()
        } catch (e) {
          callback(new Error('参数必须是有效的JSON格式'))
        }
      },
      trigger: 'blur'
    }
  ],
  shardingKey: [{ required: true, message: '请选择执行实例', trigger: 'change' }]
}

const dialogTitle = computed(() => isCreate.value ? '创建任务' : '编辑任务')

const formattedViewParam = computed(() => {
  try {
    const obj = JSON.parse(viewForm.param)
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return viewForm.param
  }
})

const getStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 0: '待执行', 1: '执行中', 2: '成功', 3: '失败' }
  return texts[status] || '未知'
}

// 窗口大小检测
const handleResize = () => {
  isSmallScreen.value = window.innerWidth < 992
  isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
}

// 查询列表
const handleQuery = async () => {
  loading.value = true
  try {
    const params = {
      ...queryForm,
      gmtCreateStart: createTimeRange.value ? createTimeRange.value[0] : null,
      gmtCreateEnd: createTimeRange.value ? createTimeRange.value[1] : null
    }
    const result = await queryTasks(params)
    tableData.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

// 重置
const handleReset = () => {
  queryForm.id = null
  queryForm.taskCode = ''
  queryForm.taskDesc = ''
  queryForm.status = null
  queryForm.pageNum = 1
  createTimeRange.value = null
  handleQuery()
}

// 创建
const handleCreate = () => {
  isCreate.value = true
  resetForm()
  dialogVisible.value = true
  loadShardingOptions()
}

// 编辑
const handleEdit = (row) => {
  isCreate.value = false
  form.id = row.id
  form.retryNum = row.retryNum
  form.param = row.parameters || '{}'
  form.status = row.status
  form.shardingKey = row.shardingKey

  if (row.nextPlanTime) {
    const date = new Date(row.nextPlanTime)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    form.nextPlanTime = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  } else {
    form.nextPlanTime = null
  }

  dialogVisible.value = true
  loadShardingOptions()
}

// 查看
const handleView = (row) => {
  viewForm.id = row.id
  viewForm.taskCode = row.taskCode
  viewForm.taskDesc = row.taskDesc
  viewForm.retryNum = row.retryNum
  viewForm.originRetryNum = row.originRetryNum
  viewForm.delaySecond = row.delaySecond
  viewForm.intervalSecond = row.intervalSecond
  viewForm.param = row.parameters || '{}'
  viewForm.shardingKey = row.shardingKey
  viewForm.shardingInfo = row.shardingInfo || '-'
  viewForm.nextPlanTime = row.nextPlanTime
  viewForm.status = row.status
  viewForm.creator = row.creator
  viewForm.gmtCreate = row.gmtCreate
  viewForm.gmtModified = row.gmtModified
  viewForm.executor = row.executor
  viewForm.uniqueKey = row.uniqueKey
  viewForm.nextPlanTimeStrategy = row.nextPlanTimeStrategy
  viewForm.currentLogId = row.currentLogId
  viewForm.attribute = row.attribute

  viewDialogVisible.value = true
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (isCreate.value) {
        await createTask(form)
        ElMessage.success('创建成功')
      } else {
        await updateTask(form)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      handleQuery()
    } catch (error) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 删除
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除任务 "${row.taskDesc || row.taskCode}" 吗？此操作不可恢复！`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
      distinguishCancelAndClose: true,
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          instance.confirmButtonLoading = true
          instance.confirmButtonText = '删除中...'
          deleteTask(row.id)
            .then(() => {
              ElMessage.success('删除成功')
              handleQuery()
              done()
            })
            .catch((error) => {
              ElMessage.error(error.message || '删除失败')
              instance.confirmButtonLoading = false
              instance.confirmButtonText = '确定删除'
            })
        } else {
          done()
        }
      }
    }
  ).catch(() => {})
}

// 批量删除
const handleBatchDelete = () => {
  ElMessageBox.confirm(
    `确定要删除选中的 ${selectedIds.value.length} 个任务吗？此操作不可恢复！`,
    '批量删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
      distinguishCancelAndClose: true,
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          instance.confirmButtonLoading = true
          instance.confirmButtonText = '删除中...'
          batchDeleteTasks(selectedIds.value)
            .then(() => {
              ElMessage.success('删除成功')
              handleQuery()
              done()
            })
            .catch((error) => {
              ElMessage.error(error.message || '删除失败')
              instance.confirmButtonLoading = false
              instance.confirmButtonText = '确定删除'
            })
        } else {
          done()
        }
      }
    }
  ).catch(() => {})
}

// 选择变化
const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id)
}

// 加载分片选项
const loadShardingOptions = async () => {
  try {
    shardingOptions.value = await getShardingOptions()
  } catch (error) {
    ElMessage.error('获取分片列表失败')
  }
}

// 重置表单
const resetForm = () => {
  form.id = null
  form.taskCode = ''
  form.taskDesc = ''
  form.retryNum = 1
  form.delaySecond = 100
  form.intervalSecond = 600
  form.nextPlanTimeStrategy = 1
  form.param = '{}'
  form.shardingKey = null
  form.nextPlanTime = null
  form.status = null
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

// 格式化JSON
const formatJson = () => {
  try {
    const obj = JSON.parse(form.param)
    form.param = JSON.stringify(obj, null, 2)
    ElMessage.success('JSON格式化成功')
  } catch (e) {
    ElMessage.error('JSON格式错误: ' + e.message)
  }
}

// 压缩JSON
const compressJson = () => {
  try {
    const obj = JSON.parse(form.param)
    form.param = JSON.stringify(obj)
    ElMessage.success('JSON压缩成功')
  } catch (e) {
    ElMessage.error('JSON格式错误: ' + e.message)
  }
}

// 校验JSON
const validateJson = () => {
  try {
    JSON.parse(form.param)
    ElMessage.success('JSON格式正确')
  } catch (e) {
    ElMessage.error('JSON格式错误: ' + e.message)
  }
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
  handleQuery()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.task-management {
  padding: 0;
}

.table-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.json-button-group {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.json-preview {
  background-color: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  max-height: 400px;
  overflow: auto;
}

.json-preview pre {
  margin: 0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* JSON编辑器样式 */
:deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  background-color: #f8f9fa;
  border: 1px solid #dcdfe6;
  transition: all 0.3s;
}

:deep(.el-textarea__inner):focus {
  background-color: #fff;
  border-color: #409eff;
}

/* 手机端适配 */
@media (max-width: 768px) {
  .task-management h2 {
    font-size: 18px;
  }

  .search-card :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  .table-toolbar {
    flex-direction: column;
  }

  .table-toolbar .el-button {
    width: 100%;
  }

  .pagination-wrapper {
    justify-content: center;
  }

  .pagination-wrapper :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }

  .json-button-group {
    flex-direction: column;
  }

  .json-button-group .el-button {
    width: 100%;
  }
}
</style>