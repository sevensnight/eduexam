<template>
  <el-card header="用户管理" v-loading="loading">
    <div class="toolbar">
      <el-form inline :model="filters" @submit.prevent="fetchUsers">
        <el-form-item label="搜索">
          <el-input v-model="filters.keyword" placeholder="用户名 / 姓名 / 邮箱 / 班级" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="filters.class_name" placeholder="全部班级" clearable style="width: 180px">
            <el-option v-for="item in classOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyFilters">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar-actions">
        <el-upload
          :show-file-list="false"
          accept=".csv,.xlsx"
          :http-request="uploadStudents"
        >
          <el-button :icon="Upload">导入学生</el-button>
        </el-upload>
        <el-button :icon="Download" @click="downloadStudents('xlsx')">导出学生 Excel</el-button>
        <el-button :icon="Download" @click="downloadStudents('csv')">导出学生 CSV</el-button>
      </div>
    </div>

    <el-table :data="users" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column prop="real_name" label="姓名" width="120" />
      <el-table-column prop="class_name" label="班级" min-width="140" show-overflow-tooltip />
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column label="角色" width="130">
        <template #default="{ row }">
          <el-select
            v-model="row.role"
            size="small"
            @change="(value) => changeRole(row, value)"
            :disabled="row.id === currentUserId"
          >
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.is_active ? 'success' : 'danger'">{{ row.is_active ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            link
            :type="row.is_active ? 'danger' : 'success'"
            @click="toggleActive(row)"
            :disabled="row.id === currentUserId"
          >
            {{ row.is_active ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Download, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { createUploadForm, downloadBlob } from '@/utils/download'

const loading = ref(true)
const users = ref([])
const userStore = useUserStore()
const currentUserId = userStore.user?.id
const filters = ref({
  keyword: '',
  class_name: null,
})

const classOptions = computed(() => {
  const allClasses = users.value
    .map((item) => item.class_name)
    .filter(Boolean)
  return [...new Set(allClasses)].sort()
})

function buildParams() {
  const params = {}
  if (filters.value.keyword) params.keyword = filters.value.keyword
  if (filters.value.class_name) params.class_name = filters.value.class_name
  return params
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function fetchUsers() {
  loading.value = true
  try {
    users.value = await userApi.list(buildParams())
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  fetchUsers()
}

function resetFilters() {
  filters.value = {
    keyword: '',
    class_name: null,
  }
  fetchUsers()
}

async function changeRole(row, role) {
  const prev = row.role
  try {
    await userApi.setRole(row.id, role)
    ElMessage.success('角色已更新')
  } catch {
    row.role = prev
    ElMessage.error('角色更新失败')
  }
}

async function toggleActive(user) {
  const result = await userApi.toggleActive(user.id)
  user.is_active = result.is_active
  ElMessage.success(user.is_active ? '已启用' : '已禁用')
}

async function downloadStudents(format) {
  const blob = await userApi.exportStudents({ ...buildParams(), role: 'student', format })
  downloadBlob(blob, `students_export.${format}`)
}

async function uploadStudents(options) {
  try {
    const result = await userApi.importStudents(createUploadForm(options.file))
    options.onSuccess?.(result)
    const firstError = result.errors?.[0]
    if (firstError) {
      ElMessage.warning(`已新增 ${result.created} 人，更新 ${result.updated} 人；第 ${firstError.row} 行失败：${firstError.message}`)
    } else {
      ElMessage.success(`导入完成：新增 ${result.created} 人，更新 ${result.updated} 人`)
    }
    await fetchUsers()
  } catch (error) {
    options.onError?.(error)
    throw error
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
