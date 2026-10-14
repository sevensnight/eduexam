<template>
  <div class="workflow-page exam-editor">
    <div class="workflow-context"><el-button link :icon="Back" @click="router.push('/exams')">返回考试列表</el-button><span>设置考试信息，选题并调整分值</span></div>
    <el-skeleton v-if="pageLoading" :rows="8" animated />
    <el-alert v-else-if="loadError" type="error" :closable="false" title="考试设置加载失败，为避免覆盖原数据，暂不可编辑。"><el-button link type="primary" @click="initialize">重新加载</el-button></el-alert>
    <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top" class="workflow-form exam-editor-form" :disabled="loading" novalidate @submit.prevent="submit">
      <el-card>
        <template #header><div class="workflow-heading"><div><h2>考试设置</h2><p class="workflow-caption">基本信息、参与范围与时间安排</p></div><span class="workflow-secondary">时间均为北京时间（UTC+8）</span></div></template>
        <div class="form-grid">
          <el-form-item label="考试名称" prop="title"><el-input v-model="form.title" placeholder="输入考试名称" maxlength="256" /></el-form-item>
          <el-form-item label="考试时长（分钟）" prop="duration_minutes"><el-input-number v-model="form.duration_minutes" class="number-stepper" :min="1" :max="1440" aria-label="考试时长（分钟）" /></el-form-item>
          <el-form-item label="考试说明" class="form-wide"><el-input v-model="form.description" type="textarea" :rows="2" placeholder="考试内容、注意事项等（选填）" /></el-form-item>
          <el-form-item label="归属课程"><el-select v-model="form.course_id" clearable filterable placeholder="不限制课程"><el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" /></el-select></el-form-item>
          <el-form-item label="目标班级"><el-select v-model="form.target_class" clearable filterable allow-create placeholder="不限制班级"><el-option v-for="name in classes" :key="name" :label="name" :value="name" /></el-select></el-form-item>
          <el-form-item label="开放时间"><el-date-picker v-model="form.open_time" type="datetime" popper-class="exam-schedule-picker" placeholder="不设置则手动发布" /></el-form-item>
          <el-form-item label="关闭时间"><el-date-picker v-model="form.close_time" type="datetime" popper-class="exam-schedule-picker" placeholder="不设置则手动关闭" /></el-form-item>
        </div>
        <div class="exam-rule-grid">
          <div class="exam-rule"><div><strong>允许重考</strong><p>开启后可设置学生的参考次数</p></div><el-switch v-model="form.allow_retake" aria-label="允许重考" /></div>
          <div class="exam-rule"><div><strong>公开成绩</strong><p>考试结束并完成批改后公布</p></div><el-switch v-model="form.score_public" aria-label="公开成绩" /></div>
          <div class="exam-rule"><div><strong>参考次数</strong><p>包含首次考试</p></div><el-input-number v-model="form.max_attempts" class="number-stepper" :min="1" :max="100" :disabled="!form.allow_retake" aria-label="参考次数" /></div>
        </div>
      </el-card>

      <el-card>
        <template #header><div class="workflow-heading"><div><h2>试卷内容<span class="workflow-count">{{ form.questions.length }} 题</span></h2><p class="workflow-caption">从题库添加题目，在已选列表中设置分值与顺序</p></div><span class="editor-total">总分 <strong>{{ totalScore }}</strong> 分</span></div></template>
        <div class="workflow-body">
          <div class="question-picker">
            <section class="picker-pool" aria-label="题库选题">
              <div class="picker-heading"><h3>题库</h3><span>{{ poolTotal }} 道题</span></div>
              <div class="picker-filters"><el-input v-model="qSearch" :prefix-icon="Search" placeholder="搜索题目关键词" aria-label="搜索题库" clearable @input="queueQuestionSearch" /><el-select v-model="qCategory" placeholder="全部分类" aria-label="筛选题目分类" clearable @change="resetQuestionSearch"><el-option v-for="c in flatCategories" :key="c.id" :label="c.label" :value="c.id" /></el-select></div>
              <div v-loading="poolLoading" class="q-pool">
                <el-alert v-if="poolError" type="error" :closable="false" title="题库加载失败"><el-button link type="primary" @click="searchQuestions">重新加载</el-button></el-alert>
                <div v-for="q in poolQuestions" :key="q.id" class="pool-question" :class="{ 'is-selected': isSelected(q.id) }">
                  <div class="pool-question-copy"><span class="picker-type">{{ typeLabel(q.question_type) }}</span><button type="button" class="picker-title" @click="preview(q.id)">{{ q.title }}</button></div>
                  <el-button link type="primary" :icon="isSelected(q.id) ? Check : undefined" :disabled="isSelected(q.id)" :aria-label="(isSelected(q.id) ? '已添加：' : '添加题目：') + q.title" @click="addQuestion(q)">{{ isSelected(q.id) ? '已添加' : '添加' }}</el-button>
                </div>
                <el-empty v-if="!poolLoading && !poolError && !poolQuestions.length" :image-size="50" description="没有匹配的题目" />
              </div>
              <el-pagination v-if="poolTotal > poolPageSize" v-model:current-page="poolPage" :page-size="poolPageSize" :total="poolTotal" :pager-count="5" small layout="prev, pager, next" class="picker-pagination" @current-change="searchQuestions" />
            </section>
            <section class="picker-selected" aria-label="已选试题">
              <div class="picker-heading"><h3>已选题目</h3><span>拖拽或使用箭头调整顺序</span></div>
              <div ref="sortableEl" class="q-pool selected-pool">
                <div v-for="(item, idx) in form.questions" :key="item.question_id" class="selected-item" :data-question-id="item.question_id">
                  <div class="selected-question-heading"><el-icon class="sortable-handle" aria-hidden="true"><Rank /></el-icon><span class="order-num">{{ idx + 1 }}</span><span class="picker-type">{{ typeLabel(getQType(item.question_id)) }}</span><el-button link :icon="Close" :aria-label="'移除第 ' + (idx + 1) + ' 题'" @click="removeQuestion(idx)" /></div>
                  <button type="button" class="picker-title" @click="preview(item.question_id)">{{ getQTitle(item.question_id) }}</button>
                  <div class="selected-question-tools"><div class="score-control"><span>分值</span><el-input-number v-model="item.score" class="number-stepper" :min="0" size="small" :aria-label="'第 ' + (idx + 1) + ' 题分值'" /><span>分</span></div><div class="order-controls"><el-button link :icon="ArrowUp" :disabled="idx === 0" :aria-label="'上移第 ' + (idx + 1) + ' 题'" @click="moveQuestion(idx, idx - 1)" /><el-button link :icon="ArrowDown" :disabled="idx === form.questions.length - 1" :aria-label="'下移第 ' + (idx + 1) + ' 题'" @click="moveQuestion(idx, idx + 1)" /></div></div>
                </div>
                <el-empty v-if="!form.questions.length" :image-size="56" description="从题库添加题目，开始组卷" />
              </div>
            </section>
          </div>
          <div class="exam-scoring"><div><strong>及格分</strong><p class="form-note">按当前试卷总分计算，建议 {{ recommendedPassScore }} 分</p></div><div class="workflow-actions"><el-input-number v-model="form.pass_score" class="number-stepper" :min="0" :max="Math.max(totalScore, 0)" :step="1" aria-label="及格分" @change="passScoreTouched = true" /><el-button link type="primary" @click="useRecommendedPassScore">设为总分的 60%</el-button></div></div>
        </div>
      </el-card>
      <footer class="workflow-footer"><span class="workflow-secondary">已选 {{ form.questions.length }} 题 · 总分 {{ totalScore }} 分 · 及格 {{ form.pass_score ?? '未设置' }} 分</span><div class="workflow-actions"><el-button @click="router.push('/exams')">取消</el-button><el-button type="primary" :loading="loading" native-type="submit">保存考试</el-button></div></footer>
    </el-form>
    <QuestionPreviewDialog v-model="previewVisible" :question-id="previewQuestionId" />
  </div>
</template>
<script setup>
import { ref, computed, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowUp, ArrowDown, Back, Search, Close, Rank, Check } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { examApi, questionApi, categoryApi, courseApi } from '@/api'
import QuestionPreviewDialog from '@/components/lists/QuestionPreviewDialog.vue'

import { moveExamQuestion, validateExamSettings } from '@/utils/examPresentation'
import '@/styles/workflow.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const formRef = ref()
const loading = ref(false)
const pageLoading = ref(true)
const loadError = ref(false)
const previewQuestionId = ref(null)
const previewVisible = ref(false)
let initVersion = 0
let disposed = false
const qSearch = ref('')
const qCategory = ref(null)
const poolQuestions = ref([])
const poolTotal = ref(0)
const poolPage = ref(1)
const poolPageSize = 20
const poolLoading = ref(false)
const poolError = ref(false)
const allCategories = ref([])
const courses = ref([])
const classes = ref([])
const qTitleMap = ref({})
const qTypeMap = ref({})
const sortableEl = ref(null)
let sortableInstance = null
let searchTimer = null
let searchRequestVersion = 0

const DEFAULT_SCORES = { single: 5, multiple: 8, truefalse: 2, essay: 15 }

const totalScore = computed(() => form.value.questions.reduce((sum, q) => sum + (q.score || 0), 0))
const recommendedPassScore = computed(() => Math.ceil(totalScore.value * 0.6))
const passScoreTouched = ref(false)

function initSortable() {
  if (sortableInstance) sortableInstance.destroy()
  if (!sortableEl.value) return
  sortableInstance = Sortable.create(sortableEl.value, {
    animation: 150,
    handle: '.sortable-handle',
    draggable: '.selected-item',
    onEnd({ item, oldDraggableIndex, newDraggableIndex }) {
      const parent = item.parentNode
      parent.removeChild(item)
      parent.insertBefore(item, parent.children[oldDraggableIndex] || null)
      moveQuestion(oldDraggableIndex, newDraggableIndex)
    },
  })
}

const form = ref({
  title: '',
  description: '',
  duration_minutes: 60,
  course_id: null,
  target_class: '',
  open_time: null,
  close_time: null,
  max_attempts: 1,
  pass_score: 0,
  allow_retake: false,
  score_public: false,
  questions: [],
})

watch(totalScore, () => {
  if (!passScoreTouched.value) form.value.pass_score = recommendedPassScore.value
})

function useRecommendedPassScore() {
  form.value.pass_score = recommendedPassScore.value
  passScoreTouched.value = false
}

watch(() => form.value.questions.length, async () => {
  await nextTick()
  initSortable()
})
const rules = {
  title: [{ required: true, message: '请输入考试名称' }],
}

const flatCategories = computed(() => {
  const result = []
  function flatten(cats, prefix = '') {
    cats.forEach(c => {
      result.push({ id: c.id, label: prefix + c.name })
      if (c.children?.length) flatten(c.children, prefix + '  ')
    })
  }
  flatten(allCategories.value)
  return result
})

function typeLabel(t) { return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[t] || t }
function isSelected(id) { return form.value.questions.some(q => q.question_id === id) }
function getQTitle(id) { return qTitleMap.value[id] || `题目#${id}` }
function getQType(id) { return qTypeMap.value[id] || 'single' }

async function searchQuestions() {
  const requestVersion = ++searchRequestVersion
  const params = { page: poolPage.value, size: poolPageSize }
  if (qSearch.value) params.keyword = qSearch.value
  if (qCategory.value) params.category_id = qCategory.value
  poolLoading.value = true
  poolError.value = false
  try {
    const res = await questionApi.list(params)
    if (requestVersion !== searchRequestVersion) return
    poolQuestions.value = res.items || []
    poolTotal.value = res.total || 0
    poolQuestions.value.forEach(q => {
      qTitleMap.value[q.id] = q.title
      qTypeMap.value[q.id] = q.question_type
    })
  } catch {
    if (requestVersion !== searchRequestVersion) return
    poolQuestions.value = []
    poolTotal.value = 0
    poolError.value = true
  } finally {
    if (requestVersion === searchRequestVersion) poolLoading.value = false
  }
}

function queueQuestionSearch() {
  clearTimeout(searchTimer)
  // Invalidate an earlier response immediately; it must not flash stale
  // results while this debounced request is waiting to start.
  searchRequestVersion++
  searchTimer = setTimeout(() => {
    poolPage.value = 1
    searchQuestions()
  }, 300)
}

function resetQuestionSearch() {
  clearTimeout(searchTimer)
  poolPage.value = 1
  searchQuestions()
}

function addQuestion(q) {
  if (loading.value) return
  if (isSelected(q.id)) return ElMessage.warning('该题已添加')
  qTitleMap.value[q.id] = q.title
  qTypeMap.value[q.id] = q.question_type
  form.value.questions.push({
    question_id: q.id,
    order_num: form.value.questions.length + 1,
    score: DEFAULT_SCORES[q.question_type] ?? 5,
  })
}

function removeQuestion(idx) {
  if (loading.value) return
  form.value.questions.splice(idx, 1)
  form.value.questions.forEach((q, i) => q.order_num = i + 1)
}

async function submit() {
  if (loading.value || pageLoading.value || loadError.value) return
  if (!await formRef.value.validate().catch(() => false)) return
  if (loading.value) return
  const error = validateExamSettings(form.value)
  if (error) return ElMessage.warning(error)
  loading.value = true
  try {
    const payload = {
      ...form.value,
      title: form.value.title.trim(),
      questions: form.value.questions.map((q, index) => ({ ...q, order_num: index + 1 })),
      time_limit: form.value.duration_minutes,
      total_score: totalScore.value,
      pass_score: form.value.pass_score,
      course_id: form.value.course_id || null,
      target_class: form.value.target_class || null,
      open_time: toApiDate(form.value.open_time),
      close_time: toApiDate(form.value.close_time),
      max_attempts: form.value.allow_retake ? form.value.max_attempts : 1,
    }
    if (isEdit.value) {
      await examApi.update(route.params.id, payload)
      ElMessage.success('更新成功')
    } else {
      await examApi.create(payload)
      ElMessage.success('创建成功')
    }
    router.push('/exams')
  } catch {
    // Preserve the form when validation or server-side exam rules reject the save.
  } finally {
    loading.value = false
  }
}

function toApiDate(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  // The backend stores LocalDateTime in the application timezone
  // (Asia/Shanghai). Sending an ISO string with `Z` would shift the exam by
  // eight hours when Jackson parses it into LocalDateTime.
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function toPickerDate(value) {
  if (!value) return null
  const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/)
  if (!match) return null
  // The picker edits an Asia/Shanghai wall-clock value. Constructing a local
  // Date from its components prevents the browser timezone from shifting the
  // displayed hour before it is serialized back as a wall-clock value.
  return new Date(
    Number(match[1]), Number(match[2]) - 1, Number(match[3]),
    Number(match[4]), Number(match[5]), Number(match[6] || 0),
  )
}

function moveQuestion(from, to) {
  if (!loading.value) form.value.questions = moveExamQuestion(form.value.questions, from, to)
}
function preview(questionId) { previewQuestionId.value = questionId; previewVisible.value = true }

async function initialize() {
  const version = ++initVersion
  previewVisible.value = false
  previewQuestionId.value = null
  pageLoading.value = true
  loadError.value = false
  clearTimeout(searchTimer)
  ++searchRequestVersion
  qSearch.value = ''
  qCategory.value = null
  poolPage.value = 1
  try {
    const [categoryRes, courseRes, classRes, exam] = await Promise.all([
      categoryApi.list(), courseApi.list(), courseApi.classes(),
      isEdit.value ? examApi.get(route.params.id) : Promise.resolve(null),
    ])
    if (disposed || version !== initVersion) return
    allCategories.value = categoryRes
    courses.value = courseRes
    classes.value = classRes
    qTitleMap.value = {}
    qTypeMap.value = {}
    passScoreTouched.value = !!exam
    form.value = exam ? {
      title: exam.title, description: exam.description || '',
      duration_minutes: exam.duration_minutes,
      course_id: exam.course_id || null, target_class: exam.target_class || '',
      open_time: toPickerDate(exam.open_time), close_time: toPickerDate(exam.close_time),
      max_attempts: exam.max_attempts || 1,
      pass_score: exam.pass_score ?? Math.ceil((exam.total_score || 0) * 0.6),
      allow_retake: !!exam.allow_retake, score_public: !!exam.score_public,
      questions: (exam.exam_questions || []).map(eq => ({ question_id: eq.question_id, order_num: eq.order_num, score: eq.score })),
    } : { title: '', description: '', duration_minutes: 60, course_id: null, target_class: '', open_time: null, close_time: null, max_attempts: 1, pass_score: 0, allow_retake: false, score_public: false, questions: [] }
    for (const eq of exam?.exam_questions || []) {
      if (eq.question_title) qTitleMap.value[eq.question_id] = eq.question_title
      if (eq.question_type) qTypeMap.value[eq.question_id] = eq.question_type
    }
    searchQuestions()
  } catch {
    if (version === initVersion && !disposed) loadError.value = true
  } finally {
    if (version === initVersion && !disposed) {
      pageLoading.value = false
      await nextTick()
      initSortable()
    }
  }
}
watch(() => route.params.id, initialize, { immediate: true })

onUnmounted(() => {
  disposed = true
  ++initVersion
  clearTimeout(searchTimer)
  searchRequestVersion++
  sortableInstance?.destroy()
  sortableInstance = null
})
</script>

<style scoped>
.exam-editor-form { display: grid; gap: 20px; }
.exam-rule-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 16px; border-top: 1px solid var(--app-border); padding-top: 20px; }
.exam-rule { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; align-items: center; min-width: 0; }
.exam-rule strong, .exam-scoring strong { font-size: 13px; font-weight: 500; }
.exam-rule p { margin: 5px 0 0; font-size: 12px; color: var(--app-text-secondary); }
.exam-rule .number-stepper { width: 132px; flex-shrink: 0; }
.editor-total { color: var(--app-text-secondary); font-size: 12px; }
.editor-total strong { font-size: 24px; color: var(--app-text); margin-inline: 5px; font-variant-numeric: tabular-nums; }
.question-picker { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1.1fr); gap: 24px; }
.picker-heading { display: flex; justify-content: space-between; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 14px; }
.picker-heading h3 { font-size: 14px; font-weight: 500; margin: 0; }
.picker-heading > span { font-size: 12px; color: var(--app-text-secondary); }
.picker-filters { display: grid; grid-template-columns: minmax(0,1.3fr) minmax(0,1fr); gap: 10px; margin-bottom: 12px; }
.q-pool { max-height: 480px; min-height: 200px; overflow-y: auto; overscroll-behavior: contain; border: 1px solid var(--app-border); border-radius: 10px; }
.pool-question { display: flex; align-items: center; gap: 14px; padding: 16px; border-bottom: 1px solid var(--app-border); }
.pool-question:last-child { border: 0; } .pool-question.is-selected { background: var(--app-selection-bg); box-shadow: inset 3px 0 var(--app-selection-border); }
.pool-question.is-selected .picker-title { color: var(--app-selection-ink); font-weight: 500; }
.pool-question.is-selected :deep(.el-button.is-disabled) { color: var(--app-selection-ink); }
.pool-question-copy { min-width: 0; flex: 1; }
.picker-type { color: #627184; font-size: 11px; }
.picker-title { border: 0; background: transparent; text-align: left; padding: 0; color: var(--app-text); font-size: 13px; line-height: 1.75; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.picker-title:hover { color: var(--app-accent); }
.pool-question .picker-title { margin-top: 5px; }
.selected-item { padding: 16px; border-bottom: 1px solid var(--app-border); } .selected-item:last-child { border: 0; }
.selected-question-heading { display: flex; align-items: center; gap: 8px; margin-bottom: 9px; }
.selected-question-heading > .el-button { margin-left: auto; color: var(--app-text-secondary); }
.sortable-handle { cursor: grab; color: #858b95; touch-action: none; }
.order-num { color: var(--app-text-secondary); font-size: 12px; font-variant-numeric: tabular-nums; }
.selected-question-tools, .score-control, .order-controls { display: flex; align-items: center; gap: 9px; }
.selected-question-tools { flex-wrap: wrap; justify-content: space-between; margin-top: 13px; }
.score-control { color: var(--app-text-secondary); font-size: 12px; } .score-control .number-stepper { width: 132px; }
.order-controls .el-button { margin: 0; padding: 4px; }
.picker-pagination { justify-content: center; margin-top: 12px; padding: 0; }
.exam-scoring { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 16px; border-top: 1px solid var(--app-border); padding-top: 22px; margin-top: 24px; }
@container workflow (max-width: 700px) { .question-picker { grid-template-columns: minmax(0,1fr); } .q-pool { max-height: 420px; } }
@media (max-width: 1100px) { .exam-rule-grid { grid-template-columns: minmax(0,1fr); } }
@media (max-width: 540px) { .picker-filters { grid-template-columns: minmax(0,1fr); } .pool-question,.selected-item { padding: 12px; } }
</style>
