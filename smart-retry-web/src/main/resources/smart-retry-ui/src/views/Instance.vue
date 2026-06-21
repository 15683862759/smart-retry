<template>
  <div class="instance-management">
    <h2>实例管理</h2>

    <!-- 搜索区域 -->
    <el-card style="margin-top: 20px;" class="search-card">
      <el-form :model="queryForm" label-width="auto">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label="创建者ID">
              <el-input v-model="queryForm.creatorId" placeholder="请输入创建者ID" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label="实例ID">
              <el-input v-model="queryForm.instanceId" placeholder="请输入实例ID" clearable />
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
      <div class="table-scroll">
        <el-table :data="tableData" border v-loading="loading" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="creatorId" label="创建者ID" min-width="140" />
          <el-table-column prop="instanceId" label="实例ID" min-width="160" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '已分配' : '未分配' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastHeartbeat" label="最后心跳时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.lastHeartbeat) }}
            </template>
          </el-table-column>
          <el-table-column prop="gmtCreate" label="创建时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.gmtCreate) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="160">
            <template #default="{ row }">
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

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑实例" :width="dialogWidth" :fullscreen="isMobile">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-form-item label="实例ID" prop="instanceId">
          <el-input v-model="editForm.instanceId" placeholder="格式: ip:port" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitEdit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, onUnmounted } from 'vue'
import { queryInstances, updateInstance, deleteInstance } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const editDialogVisible = ref(false)
const editFormRef = ref(null)
const isSmallScreen = ref(false)
const isMobile = ref(false)

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  creatorId: '',
  instanceId: ''
})

const editForm = reactive({
  id: null,
  instanceId: ''
})

const editRules = {
  instanceId: [
    { required: true, message: '请输入实例ID', trigger: 'blur' },
    { pattern: /^\d+\.\d+\.\d+\.\d+:\d+$/, message: '格式必须为ip:port', trigger: 'blur' }
  ]
}

// 分页 layout 响应式
const paginationLayout = computed(() => {
  return isSmallScreen.value
    ? 'total, prev, pager, next'
    : 'total, sizes, prev, pager, next, jumper'
})

// 弹窗宽度响应式
const dialogWidth = computed(() => {
  return isMobile.value ? '100%' : '500px'
})

// 窗口大小检测
const handleResize = () => {
  isSmallScreen.value = window.innerWidth < 992
  isMobile.value = window.innerWidth < 768
}

// 查询列表
const handleQuery = async () => {
  loading.value = true
  try {
    const result = await queryInstances(queryForm)
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
  queryForm.creatorId = ''
  queryForm.instanceId = ''
  queryForm.pageNum = 1
  handleQuery()
}

// 编辑
const handleEdit = (row) => {
  editForm.id = row.id
  editForm.instanceId = row.instanceId
  editDialogVisible.value = true
}

// 提交编辑
const handleSubmitEdit = async () => {
  if (!editFormRef.value) return

  await editFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      await updateInstance(editForm)
      ElMessage.success('更新成功')
      editDialogVisible.value = false
      handleQuery()
    } catch (error) {
      ElMessage.error(error.message || '更新失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 删除
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除实例 "${row.instanceId}" 吗？此操作不可恢复！`,
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
          deleteInstance(row.id)
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

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
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
.instance-management {
  padding: 0;
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

/* 手机端适配 */
@media (max-width: 768px) {
  .instance-management h2 {
    font-size: 18px;
  }

  .search-card :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  .pagination-wrapper {
    justify-content: center;
  }

  .pagination-wrapper :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>