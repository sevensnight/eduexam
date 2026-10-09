<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>全部考试 <span class="list-count">{{ exams.length }}</span></span>
          <el-button v-if="store.canManage" type="primary" @click="$router.push('/exams/create')" :icon="Plus">创建考试</el-button>
        </div>
      </template>
      <el-table :data="exams" v-loading="loading">
        <el-table-column prop="title" label="考试名称" min-width="200" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration_minutes" label="时长(分钟)" width="110" />
        <el-table-column prop="total_score" label="总分" width="80" />
        <el-table-column prop="pass_score" label="及格分" width="80" />
        <el-table-column label="分配范围" min-width="180">
          <template #default="{ row }">
            <div class="scope-cell">
              <el-tag v-if="row.course_name" size="small" type="success">{{ row.course_name }}</el-tag>
              <el-tag v-if="row.target_class" size="small" type="info">{{ row.target_class }}</el-tag>
              <span v-if="!row.course_name && !row.target_class" class="muted">全体学生</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间窗" min-width="220">
          <template #default="{ row }">
            <div class="time-cell">
              <div>开始：{{ formatDate(row.open_time) }}</div>
              <div>截止：{{ formatDate(row.close_time) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="次数" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.allow_retake ? 'warning' : 'info'">
              {{ row.allow_retake ? `${row.max_attempts || 1} 次` : '1 次' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="question_count" label="题目数" width="80" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <template v-if="store.canManage">
              <el-button link type="primary" @click="$router.push(`/exams/${row.id}/edit`)">编辑</el-button>
              <el-button link type="info" @click="$router.push(`/exams/${row.id}/stats`)">统计</el-button>
              <el-tooltip :content="row.has_essay ? '' : '该试卷没有需要批改的主观题'" placement="top" :disabled="row.has_essay">
                <el-button link type="warning" :disabled="!row.has_essay" @click="$router.push(`/exams/${row.id}/grade`)">批改</el-button>
              </el-tooltip>
              <el-button link :type="row.status === 'published' ? 'warning' : 'success'" @click="toggleStatus(row)">{{ row.status === 'published' ? '关闭' : '发布' }}</el-button>
              <el-popconfirm title="确认删除该考试？" @confirm="removeExam(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
            <template v-if="store.isStudent">
              <el-tooltip :content="studentStartReason(row)" placement="top" :disabled="!studentStartReason(row)">
                <span>
                  <el-button
                    link
                    type="primary"
                    :disabled="!!studentStartReason(row)"
                    @click="$router.push(`/exams/${row.id}/take`)"
                  >参加考试</el-button>
                </span>
              </el-tooltip>
              <el-button link @click="$router.push(`/exams/${row.id}/result`)">查看成绩</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElTooltip } from 'element-plus'
import { examApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const exams = ref([])

function statusType(s) { return { draft: 'info', published: 'success', closed: 'warning' }[s] || 'info' }
function statusLabel(s) { return { draft: '草稿', published: '进行中', closed: '已关闭' }[s] || s }
function formatDate(value) {
  if (!value) return '不限'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString()
}
function studentStartReason(exam) {
  if (exam.status !== 'published') return '考试未开放'
  const now = Date.now()
  const openAt = exam.open_time ? new Date(exam.open_time).getTime() : null
  const closeAt = exam.close_time ? new Date(exam.close_time).getTime() : null
  if (openAt && now < openAt) return '未到开放时间'
  if (closeAt && now >= closeAt) return '考试已过截止时间'
  return ''
}

async function toggleStatus(exam) {
  const newStatus = exam.status === 'published' ? 'closed' : 'published'
  await examApi.update(exam.id, { status: newStatus })
  exam.status = newStatus
  ElMessage.success('状态已更新')
}

async function removeExam(id) {
  await examApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

async function load() {
  loading.value = true
  try {
    exams.value = await examApi.list()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.scope-cell { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.time-cell { color: #606266; font-size: 12px; line-height: 1.6; }
.muted { color: #909399; font-size: 13px; }
</style>
