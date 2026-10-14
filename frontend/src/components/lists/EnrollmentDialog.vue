<template>
  <el-dialog v-model="visible" title="选课学生" width="720px" class="enrollment-dialog" append-to-body destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <p class="directory-form-note">{{ course?.name }}<br>选择参与本课程的学生，所有更改在保存后生效。</p>
    <el-alert v-if="loadError" title="学生名单加载失败" type="error" :closable="false" show-icon>
      <el-button link type="primary" @click="loadRoster">重新加载</el-button>
    </el-alert>
    <template v-else>
      <div class="roster-filters">
        <el-input v-model="keyword" placeholder="搜索姓名、账号或邮箱" aria-label="搜索选课学生" clearable :prefix-icon="Search" :disabled="loading || saving" />
        <el-select v-model="classFilter" placeholder="全部班级" aria-label="筛选学生班级" clearable filterable :disabled="loading || saving">
          <el-option v-for="name in classes" :key="name" :label="name" :value="name" />
        </el-select>
      </div>
      <div class="roster-selection-bar">
        <el-checkbox :model-value="allVisibleSelected" :indeterminate="someVisibleSelected" :disabled="loading || saving || !selectableIds.length" @change="selectVisible">全选当前结果</el-checkbox>
        <el-checkbox v-model="onlySelected" :disabled="loading || saving">仅看已选</el-checkbox>
      </div>
      <div v-loading="loading" class="roster-list" aria-label="学生候选名单" :aria-busy="loading">
        <template v-if="!loading">
          <div v-for="student in filteredStudents" :key="student.id" class="roster-student" :class="{ 'is-selected': selectedSet.has(student.id) }">
            <el-checkbox :model-value="selectedSet.has(student.id)" :disabled="saving || (!student.available && !selectedSet.has(student.id))" :aria-label="'选择学生：' + userDisplayName(student)" @change="checked => selectStudent(student, checked)" />
            <span class="roster-identity"><strong>{{ userDisplayName(student) }}</strong><span>{{ student.username }}<span v-if="student.class_name"> · {{ student.class_name }}</span></span><span v-if="student.email">{{ student.email }}</span></span>
            <span v-if="!student.available" class="roster-unavailable">账号不可用</span>
          </div>
          <el-empty v-if="!filteredStudents.length" :image-size="60" :description="onlySelected ? '当前筛选下没有已选学生' : '没有匹配的学生'" />
        </template>
      </div>
      <el-alert v-if="unavailableIds.length" class="roster-warning" type="warning" :closable="false" show-icon :title="unavailableIds.length + ' 个已选账号已禁用或不再是学生'">
        保存前需要移除这些账号。<el-button link type="primary" :disabled="saving" @click="removeUnavailable">移除不可用学生</el-button>
      </el-alert>
    </template>
    <el-alert v-if="saveError" class="roster-warning" type="error" :closable="false" title="保存失败，名单尚未更新，请重试。" />
    <template #footer>
      <div class="roster-footer">
        <span>已选 <strong>{{ selectedIds.length }}</strong> 人<span v-if="!loading && !loadError"> · 当前显示 {{ filteredStudents.length }} 人</span></span>
        <div class="directory-dialog-actions"><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || loadError || !loaded || unavailableIds.length > 0" @click="save">保存选课</el-button></div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { courseApi } from '@/api'
import { matchesKeyword, mergeEnrollmentCandidates, updateVisibleSelection, userDisplayName } from '@/utils/management'

const props = defineProps({ modelValue: Boolean, course: Object })
const emit = defineEmits(['update:modelValue', 'saved'])
const saving = ref(false)
const visible = computed({ get: () => props.modelValue, set: value => { if (!saving.value) emit('update:modelValue', value) } })
const students = ref([])
const selectedIds = ref([])
const keyword = ref('')
const classFilter = ref('')
const onlySelected = ref(false)
const loading = ref(false)
const loaded = ref(false)
const loadError = ref(false)
const saveError = ref(false)
let requestId = 0
let disposed = false
const selectedSet = computed(() => new Set(selectedIds.value))
const classes = computed(() => [...new Set(students.value.map(s => s.class_name).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'zh-CN')))
const filteredStudents = computed(() => students.value.filter(student =>
  (!classFilter.value || student.class_name === classFilter.value) &&
  (!onlySelected.value || selectedSet.value.has(student.id)) &&
  matchesKeyword([student.real_name, student.username, student.email, student.class_name], keyword.value)))
const selectableIds = computed(() => filteredStudents.value.filter(s => s.available).map(s => s.id))
const selectedVisibleCount = computed(() => selectableIds.value.filter(id => selectedSet.value.has(id)).length)
const allVisibleSelected = computed(() => selectableIds.value.length > 0 && selectedVisibleCount.value === selectableIds.value.length)
const someVisibleSelected = computed(() => selectedVisibleCount.value > 0 && !allVisibleSelected.value)
const unavailableIds = computed(() => students.value.filter(s => !s.available && selectedSet.value.has(s.id)).map(s => s.id))

async function loadRoster() {
  if (!props.modelValue || !props.course?.id || saving.value) return
  const sequence = ++requestId
  const courseId = props.course.id
  loading.value = true
  loaded.value = false
  loadError.value = false
  saveError.value = false
  try {
    const [detail, candidates] = await Promise.all([courseApi.get(courseId), courseApi.students({ courseId })])
    if (sequence !== requestId || disposed) return
    selectedIds.value = [...(detail.course.student_ids || [])]
    students.value = mergeEnrollmentCandidates(candidates, detail.students || [], selectedIds.value)
    loaded.value = true
  } catch {
    if (sequence === requestId && !disposed) loadError.value = true
  } finally {
    if (sequence === requestId && !disposed) loading.value = false
  }
}

function selectStudent(student, checked) {
  if (saving.value || (!student.available && checked)) return
  selectedIds.value = updateVisibleSelection(selectedIds.value, [student.id], checked)
}
function selectVisible(checked) {
  if (!saving.value) selectedIds.value = updateVisibleSelection(selectedIds.value, selectableIds.value, checked)
}
function removeUnavailable() {
  if (!saving.value) selectedIds.value = selectedIds.value.filter(id => !unavailableIds.value.includes(id))
}
async function save() {
  if (saving.value || loading.value || !loaded.value || loadError.value || unavailableIds.value.length || !props.course?.id) return
  saving.value = true
  saveError.value = false
  try {
    await courseApi.enroll(props.course.id, [...selectedIds.value])
    if (disposed) return
    ElMessage.success('选课名单已更新')
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    if (!disposed) saveError.value = true
  } finally {
    saving.value = false
  }
}
watch(() => [props.modelValue, props.course?.id], ([open]) => {
  ++requestId
  if (!open) return
  students.value = []
  selectedIds.value = []
  keyword.value = ''
  classFilter.value = ''
  onlySelected.value = false
  loadRoster()
}, { immediate: true })
onBeforeUnmount(() => { disposed = true; ++requestId })
</script>

<style scoped>
:global(.enrollment-dialog) { max-width: calc(100vw - 32px); }
.roster-filters { display: grid; grid-template-columns: minmax(0, 1fr) 180px; gap: 12px; }
.roster-selection-bar { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; padding: 12px 0; }
.roster-selection-bar .el-checkbox { margin-right: 0; }
.roster-list { min-height: 160px; max-height: 340px; overflow-y: auto; overscroll-behavior: contain; border-block: 1px solid var(--app-border); }
.roster-student { display: flex; align-items: center; gap: 12px; padding: 13px 12px; border-bottom: 1px solid var(--app-border); cursor: pointer; }
.roster-student:last-child { border-bottom: 0; }
.roster-student:hover { background: var(--el-fill-color-light); }
.roster-student.is-selected { background: var(--app-selection-bg); box-shadow: inset 3px 0 var(--app-selection-border); }
.roster-student.is-selected .roster-identity strong { color: var(--app-selection-ink); font-weight: 600; }
.roster-student > .el-checkbox { margin-right: 0; flex-shrink: 0; }
.roster-identity { min-width: 0; flex: 1; display: grid; gap: 4px; font-size: 12px; color: var(--app-text-secondary); overflow-wrap: anywhere; }
.roster-identity strong { color: var(--app-text); font-size: 13px; font-weight: 500; }
.roster-unavailable { font-size: 11px; color: var(--el-color-warning); flex-shrink: 0; }
.roster-warning { margin-top: 14px; }
.roster-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.roster-footer > span { color: var(--app-text-secondary); font-size: 12px; }
.roster-footer strong { color: var(--app-text); font-variant-numeric: tabular-nums; }
.roster-footer .directory-dialog-actions { margin: 0 0 0 auto; }
@media (max-width: 540px) { .roster-filters { grid-template-columns: minmax(0, 1fr); } .roster-student { padding-inline: 6px; gap: 8px; } }
</style>
