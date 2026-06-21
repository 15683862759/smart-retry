<template>
  <el-container style="height: 100vh;">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '200px'" class="sidebar-transition">
      <div class="logo">
        <span v-if="!isCollapse" class="logo-text">Smart Retry</span>
        <span v-else class="logo-icon">SR</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="isCollapse"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        :collapse-transition="false"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>整体分布</template>
        </el-menu-item>
        <el-menu-item index="/instance">
          <el-icon><Monitor /></el-icon>
          <template #title>实例管理</template>
        </el-menu-item>
        <el-menu-item index="/task">
          <el-icon><List /></el-icon>
          <template #title>任务管理</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶部栏：汉堡菜单 + 面包屑 -->
      <el-header height="50px" class="top-header">
        <div class="header-left">
          <el-icon
            class="hamburger-btn"
            @click="toggleSidebar"
            :size="20"
          >
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <span class="header-title">{{ pageTitle }}</span>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
const isCollapse = ref(false)

// 根据路由获取页面标题
const pageTitle = computed(() => {
  const titleMap = {
    '/dashboard': '整体分布',
    '/instance': '实例管理',
    '/task': '任务管理'
  }
  return titleMap[route.path] || 'Smart Retry'
})

// 响应式断点：小于 768px 自动折叠
const MOBILE_BREAKPOINT = 768

const handleResize = () => {
  isCollapse.value = window.innerWidth < MOBILE_BREAKPOINT
}

const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.sidebar-transition {
  transition: width 0.3s ease;
  overflow: hidden;
}

.el-aside {
  background-color: #304156;
  color: #fff;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4c;
  overflow: hidden;
  white-space: nowrap;
}

.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  margin: 0;
}

.logo-icon {
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}

.top-header {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hamburger-btn {
  cursor: pointer;
  color: #606266;
  flex-shrink: 0;
}

.hamburger-btn:hover {
  color: #409EFF;
}

.header-title {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}

.el-menu {
  border-right: none;
}

.el-menu-vertical:not(.el-menu--collapse) {
  width: 200px;
}

.el-main {
  background-color: #f0f2f5;
  padding: 16px;
}

/* 移动端减小主内容区内边距 */
@media (max-width: 768px) {
  .el-main {
    padding: 10px;
  }
}
</style>