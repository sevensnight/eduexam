<template>
  <nav aria-label="主导航" class="app-navigation">
    <div class="nav-label">工作空间</div>
    <el-menu router :default-active="activePath" @select="$emit('navigate')">
      <el-menu-item v-for="item in items" :key="item.path" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </el-menu-item>
    </el-menu>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
defineEmits(['navigate'])
const route = useRoute()
const store = useUserStore()
const items = computed(() => [
  { path: '/dashboard', label: '控制台', icon: 'Odometer' },
  ...(store.canManage ? [
    { path: '/questions', label: '题库管理', icon: 'Document' },
    { path: '/categories', label: '分类管理', icon: 'FolderOpened' },
    { path: '/courses', label: '课程管理', icon: 'Collection' },
  ] : []),
  { path: '/exams', label: '考试列表', icon: 'Notebook' },
  ...(store.isStudent ? [
    { path: '/my-records', label: '我的成绩', icon: 'List' },
    { path: '/wrong-book', label: '错题本', icon: 'WarningFilled' },
  ] : []),
  { path: '/stats', label: '统计分析', icon: 'TrendCharts' },
  ...(store.isAdmin ? [{ path: '/admin/users', label: '用户管理', icon: 'User' }] : []),
])
const activePath = computed(() => items.value.find(item =>
  route.path === item.path || route.path.startsWith(item.path + '/'))?.path || '')
</script>

<style scoped>
.nav-label { padding: 0 16px 12px; font-size: 11px; color: var(--app-text-secondary); font-weight: 500; }
.el-menu { border: 0; background: transparent; }
.el-menu-item { height: 44px; line-height: 44px; padding: 0 16px !important; margin-bottom: 5px; border-radius: 9px; color: #55555b; font-size: 13px; }
.el-menu-item .el-icon { font-size: 18px; margin-right: 12px; }
.el-menu-item:hover { background: #eaeaec; }
.el-menu-item.is-active { background: #e6edf7; color: var(--app-accent); font-weight: 600; }
</style>
