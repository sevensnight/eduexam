<template>
  <el-card class="management-list directory-management course-management">
    <template #header>
      <div class="list-heading">
        <div><h2>{{ hasFilters ? '筛选结果' : '全部课程' }}</h2><span class="list-count-badge">{{ filteredCourses.length }}</span></div>
        <el-button type="primary" :icon="Plus" @click="openForm()">新增课程</el-button>
      </div>
    </template>
    <div class="list-body">
      <form class="directory-filters" @submit.prevent="applyFilters">
        <div class="directory-field directory-keyword"><label for="course-search">搜索课程</label><el-input id="course-search" v-model="filters.keyword" placeholder="课程名称、说明或教师" clearable :prefix-icon="Search" /></div>
        <div v-if="store.isAdmin" class="directory-field"><label for="course-teacher">授课教师</label><el-select id="course-teacher" v-model="filters.teacherId" placeholder="全部教师" clearable filterable><el-option v-for="teacher in teacherOptions" :key="teacher.id" :label="teacher.name" :value="teacher.id" /></el-select></div>
        <div class="filter-buttons"><el-button type="primary" native-type="submit">查询</el-button><el-button :icon="RefreshLeft" @click="resetFilters">重置</el-button></div>
      </form>
      <div class="directory-context"><span>集中管理课程信息与选课名单</span><span>点击课程名称查看完整说明</span></div>
      <el-alert v-if="loadError" class="list-error" type="error" :closable="false" show-icon title="课程列表加载失败"><el-button link type="primary" @click="loadCourses">重新加载</el-button></el-alert>
      <div v-else v-loading="loading" class="list-results" :aria-busy="loading">
        <template v-if="pageCourses.length">
          <el-table :data="pageCourses" row-key="id" class="list-table">
            <el-table-column label="课程信息" min-width="250"><template #default="{ row }"><CourseSummary :course="row" @open="openDetails(row)" /></template></el-table-column>
            <el-table-column label="授课教师" width="130"><template #default="{ row }"><span class="wrap-copy">{{ row.teacher_name || '未设置' }}</span></template></el-table-column>
            <el-table-column label="选课人数" width="95"><template #default="{ row }"><span class="roster-count">{{ row.student_count || 0 }}</span><span class="secondary-copy"> 人</span></template></el-table-column>
            <el-table-column label="创建时间" width="132"><template #default="{ row }"><div class="date-copy"><span>{{ listDateTime(row.created_at).date }}</span><span>{{ listDateTime(row.created_at).time }}</span></div></template></el-table-column>
            <el-table-column label="操作" width="164"><template #default="{ row }"><ManagementActions :name="row.name" primary-label="选课学生" :busy="busyIds.has(row.id)" :actions="courseActions" @action="command => handleAction(row, command)" /></template></el-table-column>
          </el-table>
          <div class="compact-list">
            <article v-for="course in pageCourses" :key="course.id" class="compact-item">
              <CourseSummary :course="course" @open="openDetails(course)" />
              <div class="compact-metadata"><span>教师 · {{ course.teacher_name || '未设置' }}</span><span>选课 · {{ course.student_count || 0 }} 人</span></div>
              <div class="compact-footer"><span class="secondary-copy">创建于 {{ formatAppDateTime(course.created_at) }}</span><ManagementActions :name="course.name" primary-label="选课学生" :busy="busyIds.has(course.id)" :actions="courseActions" @action="command => handleAction(course, command)" /></div>
            </article>
          </div>
        </template>
        <el-empty v-else-if="!loading" :image-size="72" :description="hasFilters ? '没有匹配的课程，试试调整筛选条件' : '暂无课程，创建第一门课程吧'" />
      </div>
      <div v-if="!loadError && filteredCourses.length" class="list-pagination">
        <span class="secondary-copy">共 {{ filteredCourses.length }} 门课程 · 每页 {{ pageSize }} 门</span>
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filteredCourses.length" layout="prev, pager, next" :pager-count="5" small />
      </div>
    </div>
  </el-card>

  <el-dialog v-model="detailVisible" title="课程详情" width="560px" class="directory-detail-dialog course-dialog" append-to-body destroy-on-close>
    <dl v-if="detailCourse" class="detail-list"><dt>课程名称</dt><dd>{{ detailCourse.name }}</dd><dt>授课教师</dt><dd>{{ detailCourse.teacher_name || '未设置' }}</dd><dt>选课人数</dt><dd>{{ detailCourse.student_count || 0 }} 人</dd><dt>课程说明</dt><dd>{{ detailCourse.description || '暂无课程说明' }}</dd><dt>创建时间</dt><dd>{{ formatAppDateTime(detailCourse.created_at) }}</dd></dl>
    <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
  </el-dialog>

  <el-dialog v-model="formVisible" :title="form.id ? '编辑课程' : '新增课程'" width="540px" class="course-dialog" append-to-body destroy-on-close :before-close="closeForm" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <p class="directory-form-note">先完善课程信息，保存后可以继续维护选课学生。</p>
    <el-form :model="form" label-position="top" :disabled="saving" @submit.prevent="saveCourse">
      <el-form-item label="课程名称" required><el-input v-model="form.name" placeholder="例如：数据库系统" maxlength="128" show-word-limit /></el-form-item>
      <el-form-item v-if="store.isAdmin" label="授课教师" required>
        <el-select v-model="form.teacher_id" filterable placeholder="选择授课教师" style="width:100%" :loading="teachersLoading" :disabled="teachersLoading || teachersError">
          <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacherLabel(teacher)" :value="teacher.id" :disabled="!teacher.is_active" />
        </el-select>
        <span v-if="teachersError" class="teacher-note">教师列表加载失败。<el-button link type="primary" @click="loadTeachers">重新加载</el-button></span>
        <span v-else class="teacher-note">只可选择有效教师；有关联考试的课程可能无法更换教师。</span>
      </el-form-item>
      <el-form-item label="课程说明"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="10000" placeholder="介绍课程内容、教学目标或适用班级（选填）" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="formVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="store.isAdmin && (teachersLoading || teachersError)" @click="saveCourse">保存课程</el-button></template>
  </el-dialog>
  <EnrollmentDialog v-model="enrollVisible" :course="activeCourse" @saved="loadCourses" />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import { courseApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatAppDateTime } from '@/utils/examTime'
import { listDateTime } from '@/utils/listPresentation'
import { matchesKeyword, userDisplayName } from '@/utils/management'
import CourseSummary from '@/components/lists/CourseSummary.vue'
import ManagementActions from '@/components/lists/ManagementActions.vue'
import EnrollmentDialog from '@/components/lists/EnrollmentDialog.vue'
import '@/styles/managementLists.css'
import '@/styles/directoryLists.css'

const store = useUserStore()
const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const courses = ref([])
const teachers = ref([])
const teachersLoading = ref(false)
const teachersError = ref(false)
const formVisible = ref(false)
const enrollVisible = ref(false)
const activeCourse = ref(null)
const detailVisible = ref(false)
const detailCourse = ref(null)
const busyIds = ref(new Set())
const filters = reactive({ keyword: '', teacherId: '' })
const applied = ref({ ...filters })
const page = ref(1)
const pageSize = 10
let loadSequence = 0
let teacherSequence = 0
let disposed = false
const form = reactive({ id: null, name: '', description: '', teacher_id: null })
const courseActions = [
  { command: 'details', label: '课程详情' },
  { command: 'edit', label: '编辑课程' },
  { command: 'delete', label: '删除课程', divided: true, danger: true },
]
const teacherOptions = computed(() => [...new Map(courses.value.filter(c => c.teacher_id).map(c => [c.teacher_id, { id: c.teacher_id, name: c.teacher_name || '未设置' }])).values()])
const hasFilters = computed(() => !!(applied.value.keyword || applied.value.teacherId))
const filteredCourses = computed(() => courses.value.filter(course =>
  (!applied.value.teacherId || course.teacher_id === applied.value.teacherId) &&
  matchesKeyword([course.name, course.description, course.teacher_name], applied.value.keyword)))
const pageCourses = computed(() => filteredCourses.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(() => filteredCourses.value.length, count => { page.value = Math.min(page.value, Math.max(1, Math.ceil(count / pageSize))) })
function applyFilters() { applied.value = { keyword: filters.keyword.trim(), teacherId: filters.teacherId }; page.value = 1 }
function resetFilters() { Object.assign(filters, { keyword: '', teacherId: '' }); applyFilters() }

async function loadCourses() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = false
  try {
    const result = await courseApi.list()
    if (sequence === loadSequence && !disposed) courses.value = result
  } catch {
    if (sequence === loadSequence && !disposed) loadError.value = true
  } finally {
    if (sequence === loadSequence && !disposed) loading.value = false
  }
}
async function loadTeachers() {
  if (!store.isAdmin) return
  const sequence = ++teacherSequence
  teachersLoading.value = true
  teachersError.value = false
  try {
    const result = await userApi.list({ role: 'teacher' })
    if (sequence === teacherSequence && !disposed) teachers.value = result
  } catch {
    if (sequence === teacherSequence && !disposed) teachersError.value = true
  } finally {
    if (sequence === teacherSequence && !disposed) teachersLoading.value = false
  }
}
function teacherLabel(teacher) {
  return userDisplayName(teacher) + '（' + teacher.username + '）' + (teacher.is_active ? '' : ' · 已禁用')
}
function openForm(course = null) {
  Object.assign(form, { id: course?.id || null, name: course?.name || '', description: course?.description || '', teacher_id: course?.teacher_id || null })
  formVisible.value = true
  loadTeachers()
}
function closeForm(done) { if (!saving.value) done() }
function openDetails(course) { detailCourse.value = course; detailVisible.value = true }
function handleAction(course, command) {
  if (busyIds.value.has(course.id)) return
  if (command === 'primary') { activeCourse.value = course; enrollVisible.value = true }
  else if (command === 'details') openDetails(course)
  else if (command === 'edit') openForm(course)
  else if (command === 'delete') removeCourse(course)
}
async function saveCourse() {
  if (saving.value) return
  if (!form.name.trim()) return ElMessage.warning('请输入课程名称')
  if (store.isAdmin && (teachersLoading.value || teachersError.value)) return
  if (store.isAdmin && !teachers.value.some(t => t.id === form.teacher_id && t.is_active)) return ElMessage.warning('请选择有效的授课教师')
  saving.value = true
  try {
    const payload = { name: form.name.trim(), description: form.description.trim() || null, ...(store.isAdmin ? { teacher_id: form.teacher_id } : {}) }
    if (form.id) await courseApi.update(form.id, payload)
    else await courseApi.create(payload)
    if (disposed) return
    ElMessage.success('课程已保存')
    formVisible.value = false
    await loadCourses()
  } catch {
    // Keep the draft intact; the API interceptor explains the failed request.
  } finally { saving.value = false }
}
async function removeCourse(course) {
  if (busyIds.value.has(course.id)) return
  busyIds.value.add(course.id)
  try {
    await ElMessageBox.confirm('确认删除“' + course.name + '”？选课关系也会移除；有关联考试的课程无法删除。', '删除课程', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    if (disposed) return
    await courseApi.remove(course.id)
    if (disposed) return
    ElMessage.success('课程已删除')
    await loadCourses()
  } catch {
    // Cancellation and rejected deletes leave the course unchanged.
  } finally { busyIds.value.delete(course.id) }
}
onMounted(loadCourses)
onBeforeUnmount(() => { disposed = true; ++loadSequence; ++teacherSequence })
</script>

<style scoped>
:global(.course-dialog) { max-width: calc(100vw - 32px); }
.teacher-note { display: block; color: var(--app-text-secondary); font-size: 12px; line-height: 1.7; margin-top: 7px; }
</style>
