<template>
  <div class="management-list exam-list">
    <el-card>
      <template #header>
        <div class="list-heading">
          <div>
            <h2>全部考试</h2><span class="list-count-badge">{{ total }}</span>
          </div>
          <el-button v-if="store.canManage" type="primary" :icon="Plus" @click="$router.push('/exams/create')">创建考试</el-button>
        </div>
      </template>

      <div class="list-body">
        <el-alert v-if="loadError" class="list-error" type="error" :closable="false" show-icon title="考试列表加载失败">
          <template #default>请检查网络或后端服务。<el-button link type="primary" @click="load">重新加载</el-button></template>
        </el-alert>

        <div class="list-results" v-loading="loading" :aria-busy="loading">
          <el-table class="list-table" :data="exams" row-key="id" :empty-text="loadError ? '暂时无法获取考试' : '暂无考试'">
            <el-table-column label="考试信息" min-width="240">
              <template #default="{ row }">
                <div class="exam-title">{{ row.title }}</div>
                <div class="exam-scope">
                  <span v-if="row.course_name" class="course-name">{{ row.course_name }}</span>
                  <span v-if="row.target_class">{{ row.target_class }}</span>
                  <span v-if="!row.course_name && !row.target_class">全体学生</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="考试设置" width="160">
              <template #default="{ row }">
                <div class="exam-settings tabular">
                  <span class="duration"><strong>{{ row.duration_minutes }}</strong> 分钟</span>
                  <span>总分 {{ row.total_score }} / 及格 {{ row.pass_score }}</span>
                  <span class="secondary-copy">{{ row.question_count ?? 0 }} 道题 · 可作答 {{ row.allow_retake ? row.max_attempts || 1 : 1 }} 次</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="开放时间（北京时间）" width="214">
              <template #default="{ row }"><ExamSchedule :exam="row" /></template>
            </el-table-column>
            <el-table-column label="操作" width="136">
              <template #default="{ row }">
                <ExamActions :exam="row" :can-manage="store.canManage" :is-student="store.isStudent" :busy="statusUpdating.has(row.id)" :start-reason="studentStartReason(row)" @action="handleAction($event, row)" />
              </template>
            </el-table-column>
          </el-table>

          <div class="compact-list">
            <article v-for="row in exams" :key="row.id" class="compact-item">
              <div class="exam-card-heading">
                <div class="exam-title">{{ row.title }}</div>
                <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </div>
              <div class="exam-scope">
                <span v-if="row.course_name" class="course-name">{{ row.course_name }}</span>
                <span v-if="row.target_class">{{ row.target_class }}</span>
                <span v-if="!row.course_name && !row.target_class">全体学生</span>
              </div>
              <div class="compact-settings tabular">
                <span><strong>{{ row.duration_minutes }}</strong> 分钟</span>
                <span>总分 {{ row.total_score }}</span>
                <span>及格 {{ row.pass_score }}</span>
                <span>{{ row.question_count ?? 0 }} 道题</span>
                <span>可作答 {{ row.allow_retake ? row.max_attempts || 1 : 1 }} 次</span>
              </div>
              <ExamSchedule :exam="row" />
              <div class="compact-footer">
                <span class="secondary-copy">时间均为北京时间</span>
                <ExamActions :exam="row" :can-manage="store.canManage" :is-student="store.isStudent" :busy="statusUpdating.has(row.id)" :start-reason="studentStartReason(row)" @action="handleAction($event, row)" />
              </div>
            </article>
            <el-empty v-if="!exams.length && !loading && !loadError" description="暂无考试" :image-size="72" />
          </div>
        </div>

        <div class="list-pagination">
          <span class="secondary-copy" aria-live="polite">共 {{ total }} 场考试</span>
          <el-pagination v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize" :total="total" :pager-count="5" layout="prev, pager, next" @current-change="handlePageChange" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import { parseAppDateTimeMs } from '@/utils/examTime'
import ExamActions from '@/components/lists/ExamActions.vue'
import ExamSchedule from '@/components/lists/ExamSchedule.vue'
import '@/styles/managementLists.css'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const loadError = ref(false)
const exams = ref([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
let loadRequestId = 0
let disposed = false
const statusUpdating = ref(new Set())
const now = ref(Date.now())
let clockTimer

function statusType(status) { return { draft: 'info', published: 'success', closed: 'warning' }[status] || 'info' }
function statusLabel(status) { return { draft: '草稿', published: '已发布', closed: '已关闭' }[status] || status }
function studentStartReason(exam) {
  if (exam.status !== 'published') return '考试未开放'
  const openAt = exam.open_time ? parseAppDateTimeMs(exam.open_time) : null
  const closeAt = exam.close_time ? parseAppDateTimeMs(exam.close_time) : null
  if (openAt && now.value < openAt) return '未到开放时间'
  if (closeAt && now.value >= closeAt) return '考试已过截止时间'
  return ''
}

async function handleAction(command, exam) {
  if (store.canManage) {
    if (command === 'status') return toggleStatus(exam)
    if (command === 'delete') return confirmRemove(exam)
    if (command === 'grade' && !exam.has_essay) return
    if (['edit', 'stats', 'grade'].includes(command)) return router.push('/exams/' + exam.id + '/' + command)
  } else if (store.isStudent) {
    if (command === 'take' && !studentStartReason(exam)) return router.push('/exams/' + exam.id + '/take')
    if (command === 'result') return router.push('/exams/' + exam.id + '/result')
  }
}

async function toggleStatus(exam) {
  if (statusUpdating.value.has(exam.id)) return
  const newStatus = exam.status === 'published' ? 'closed' : 'published'
  const closing = newStatus === 'closed'
  try {
    await ElMessageBox.confirm(
      closing
        ? '确认关闭“' + exam.title + '”？系统将停止新的作答，并结算仍在进行中的答卷。'
        : '确认发布“' + exam.title + '”？发布后符合范围的学生即可参加考试。',
      closing ? '关闭考试' : '发布考试',
      { type: closing ? 'warning' : 'info', confirmButtonText: closing ? '确认关闭' : '确认发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  statusUpdating.value = new Set(statusUpdating.value).add(exam.id)
  try {
    await examApi.update(exam.id, { status: newStatus })
    exam.status = newStatus
    ElMessage.success(closing ? '考试已关闭' : '考试已发布')
  } finally {
    const next = new Set(statusUpdating.value)
    next.delete(exam.id)
    statusUpdating.value = next
  }
}

async function confirmRemove(exam) {
  if (statusUpdating.value.has(exam.id)) return
  try {
    await ElMessageBox.confirm('确认删除“' + exam.title + '”？', '删除考试', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  statusUpdating.value = new Set(statusUpdating.value).add(exam.id)
  try {
    await examApi.remove(exam.id)
    ElMessage.success('删除成功')
    await load()
    if (!exams.value.length && page.value > 1 && !loadError.value) {
      page.value--
      await load()
    }
  } finally {
    const next = new Set(statusUpdating.value)
    next.delete(exam.id)
    statusUpdating.value = next
  }
}

async function load() {
  const requestId = ++loadRequestId
  loading.value = true
  loadError.value = false
  try {
    const response = await examApi.list({ page: page.value, size: pageSize })
    if (requestId === loadRequestId && !disposed) {
      exams.value = response.items || []
      total.value = response.total || 0
    }
  } catch {
    if (requestId === loadRequestId && !disposed) loadError.value = true
  } finally {
    if (requestId === loadRequestId && !disposed) loading.value = false
  }
}

function handlePageChange(nextPage) {
  page.value = nextPage
  load()
}

onMounted(() => {
  load()
  clockTimer = setInterval(() => { now.value = Date.now() }, 30_000)
})
onBeforeUnmount(() => {
  disposed = true
  loadRequestId++
  clearInterval(clockTimer)
})
</script>

<style scoped>
.exam-title { color: var(--app-text); font-size: 14px; font-weight: 600; line-height: 1.65; overflow-wrap: anywhere; }
.exam-scope { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; margin-top: 9px; color: var(--app-text-secondary); font-size: 12px; overflow-wrap: anywhere; }
.course-name { padding: 1px 7px; border-radius: 5px; background: var(--app-accent-soft); color: var(--app-accent); }
.exam-settings { display: grid; gap: 5px; font-size: 12px; line-height: 1.6; }
.duration { color: var(--app-text-secondary); }
.duration strong { color: var(--app-text); font-size: 16px; font-weight: 600; }
.exam-card-heading { display: flex; align-items: flex-start; gap: 12px; }
.exam-card-heading .exam-title { flex: 1; min-width: 0; }
.exam-card-heading .el-tag { flex-shrink: 0; margin-top: 2px; }
.compact-settings { display: flex; flex-wrap: wrap; gap: 8px 16px; padding: 14px 0; color: var(--el-text-color-regular); font-size: 12px; }
.compact-settings strong { font-size: 16px; color: var(--app-text); }
.compact-footer { padding-top: 12px; border-top: 1px dashed var(--app-border); }
</style>
