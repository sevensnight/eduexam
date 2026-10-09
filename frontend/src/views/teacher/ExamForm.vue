<template>
  <el-card :header="isEdit ? '编辑考试' : '创建考试'">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" style="max-width:1100px">
      <el-row :gutter="20">
        <el-col :span="16">
          <el-form-item label="考试名称" prop="title">
            <el-input v-model="form.title" placeholder="输入考试名称" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="时长(分钟)" prop="duration_minutes">
            <el-input-number v-model="form.duration_minutes" :min="5" :max="300" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="考试说明">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>

      <el-divider>分配与时间控制</el-divider>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="归属课程">
            <el-select v-model="form.course_id" clearable filterable placeholder="不限制课程">
              <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="目标班级">
            <el-select v-model="form.target_class" clearable filterable allow-create placeholder="不限制班级">
              <el-option v-for="name in classes" :key="name" :label="name" :value="name" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="允许重考">
            <el-switch v-model="form.allow_retake" active-text="允许" inactive-text="不允许" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="公开成绩">
            <el-switch v-model="form.score_public" active-text="公开" inactive-text="不公开" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="开放时间">
            <el-date-picker v-model="form.open_time" type="datetime" placeholder="可空：手动发布" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="关闭时间">
            <el-date-picker v-model="form.close_time" type="datetime" placeholder="可空：手动关闭" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="参考次数">
            <el-input-number v-model="form.max_attempts" :min="1" :max="10" :disabled="!form.allow_retake" />
            <span class="score-hint">含首次考试</span>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider>分值设置</el-divider>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="总分">
            <span class="score-val">{{ totalScore }}</span>
            <span class="score-hint">分（按题自动累计）</span>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="及格分">
            <span class="score-val pass">{{ passScore }}</span>
            <span class="score-hint">分（总分×60%，向上取整）</span>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider>选题</el-divider>

      <!-- 搜索栏 -->
      <el-form-item label="搜索题目">
        <el-input v-model="qSearch" placeholder="关键词搜索" style="width:240px" @input="searchQuestions" />
        <el-select v-model="qCategory" placeholder="按分类筛选" clearable style="width:160px;margin-left:8px" @change="searchQuestions">
          <el-option v-for="c in flatCategories" :key="c.id" :label="c.label" :value="c.id" />
        </el-select>
      </el-form-item>

      <div class="q-section">
        <el-row :gutter="12">
          <el-col :span="11">
            <div class="q-pool-header">题库（点击添加）<span v-if="poolTotal > 0" style="font-weight:normal;color:#909399;font-size:12px;margin-left:6px">共 {{ poolTotal }} 题，显示前 {{ poolQuestions.length }} 条</span></div>
            <div class="q-pool">
              <div
                v-for="q in poolQuestions" :key="q.id"
                class="q-item"
                :class="{ selected: isSelected(q.id) }"
                @click="addQuestion(q)"
              >
                <el-tag size="small" :type="typeTag(q.question_type)" style="margin-right:6px">{{ typeLabel(q.question_type) }}</el-tag>
                <span>{{ q.title.slice(0, 60) }}{{ q.title.length > 60 ? '...' : '' }}</span>
              </div>
              <div v-if="!poolQuestions.length" style="padding:12px;color:#999;text-align:center">暂无题目</div>
            </div>
          </el-col>
          <el-col :span="13">
            <div class="q-pool-header">已选题目（可拖拽排序）共 {{ form.questions.length }} 题</div>
            <div class="q-pool" ref="sortableEl">
              <div v-for="(item, idx) in form.questions" :key="item.question_id" class="q-item selected-item">
                <el-icon class="sortable-handle" style="cursor:grab;color:#C0C4CC;margin-right:4px"><Rank /></el-icon>
                <span class="order-num">{{ idx + 1 }}</span>
                <el-tag size="small" :type="typeTag(getQType(item.question_id))" style="margin-right:6px;flex-shrink:0">{{ typeLabel(getQType(item.question_id)) }}</el-tag>
                <span style="flex:1">{{ getQTitle(item.question_id) }}</span>
                <el-input-number v-model="item.score" :min="1" :max="50" size="small" style="width:80px;margin:0 8px" />
                <span style="font-size:12px;color:#909399">分</span>
                <el-button circle :icon="Close" type="danger" plain size="small" @click="removeQuestion(idx)" style="margin-left:8px" />
              </div>
              <div v-if="!form.questions.length" style="padding:12px;color:#999;text-align:center">点击左侧题目添加</div>
            </div>
          </el-col>
        </el-row>
      </div>

      <div class="q-section" style="margin-top:20px">
        <el-button type="primary" :loading="loading" @click="submit">保存考试</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, Rank } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { examApi, questionApi, categoryApi, courseApi } from '@/api'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const formRef = ref()
const loading = ref(false)
const qSearch = ref('')
const qCategory = ref(null)
const poolQuestions = ref([])
const poolTotal = ref(0)
const allCategories = ref([])
const courses = ref([])
const classes = ref([])
const qTitleMap = ref({})
const qTypeMap = ref({})
const sortableEl = ref(null)
let sortableInstance = null

const DEFAULT_SCORES = { single: 5, multiple: 8, truefalse: 2, essay: 15 }

const totalScore = computed(() => form.value.questions.reduce((sum, q) => sum + (q.score || 0), 0))
const passScore = computed(() => Math.ceil(totalScore.value * 0.6))

function initSortable() {
  if (sortableInstance) sortableInstance.destroy()
  if (!sortableEl.value) return
  sortableInstance = Sortable.create(sortableEl.value, {
    animation: 150,
    handle: '.sortable-handle',
    onEnd({ oldIndex, newIndex }) {
      const list = form.value.questions
      const moved = list.splice(oldIndex, 1)[0]
      list.splice(newIndex, 0, moved)
      list.forEach((q, i) => q.order_num = i + 1)
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
  allow_retake: false,
  score_public: false,
  questions: [],
})

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

function typeTag(t) { return { single: '', multiple: 'warning', truefalse: 'success', essay: 'info' }[t] || '' }
function typeLabel(t) { return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[t] || t }
function isSelected(id) { return form.value.questions.some(q => q.question_id === id) }
function getQTitle(id) { return qTitleMap.value[id] || `题目#${id}` }
function getQType(id) { return qTypeMap.value[id] || 'single' }

async function searchQuestions() {
  const params = { page: 1, size: 100 }
  if (qSearch.value) params.keyword = qSearch.value
  if (qCategory.value) params.category_id = qCategory.value
  const res = await questionApi.list(params).catch(() => ({ items: [], total: 0 }))
  poolQuestions.value = res.items
  poolTotal.value = res.total || 0
  res.items.forEach(q => {
    qTitleMap.value[q.id] = q.title
    qTypeMap.value[q.id] = q.question_type
  })
}

function addQuestion(q) {
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
  form.value.questions.splice(idx, 1)
  form.value.questions.forEach((q, i) => q.order_num = i + 1)
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const payload = {
      ...form.value,
      total_score: totalScore.value,
      pass_score: passScore.value,
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
  } finally {
    loading.value = false
  }
}

function toApiDate(value) {
  if (!value) return null
  if (value instanceof Date) return value.toISOString()
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

onMounted(async () => {
  const [categoryRes, courseRes, classRes] = await Promise.all([
    categoryApi.list().catch(() => []),
    courseApi.list().catch(() => []),
    courseApi.classes().catch(() => []),
  ])
  allCategories.value = categoryRes
  courses.value = courseRes
  classes.value = classRes
  await searchQuestions()
  if (isEdit.value) {
    const exam = await examApi.get(route.params.id)
    form.value = {
      title: exam.title, description: exam.description || '',
      duration_minutes: exam.duration_minutes,
      course_id: exam.course_id || null,
      target_class: exam.target_class || '',
      open_time: exam.open_time ? new Date(exam.open_time) : null,
      close_time: exam.close_time ? new Date(exam.close_time) : null,
      max_attempts: exam.max_attempts || 1,
      allow_retake: !!exam.allow_retake,
      score_public: !!exam.score_public,
      questions: exam.exam_questions.map(eq => ({ question_id: eq.question_id, order_num: eq.order_num, score: eq.score })),
    }
    exam.exam_questions.forEach(eq => {
      if (eq.question_title) qTitleMap.value[eq.question_id] = eq.question_title
      if (eq.question_type) qTypeMap.value[eq.question_id] = eq.question_type
    })
  }
  await nextTick()
  initSortable()
})
</script>

<style scoped>
.q-pool-header { font-weight: bold; margin-bottom: 8px; color: #303133; font-size: 13px; }
.q-pool { border: 1px solid #DCDFE6; border-radius: 6px; max-height: 380px; overflow-y: auto; padding: 4px; }
.q-item { padding: 8px 10px; border-radius: 4px; cursor: pointer; font-size: 13px; display: flex; align-items: center; }
.q-item:hover { background: #F5F7FA; }
.q-item.selected { opacity: 0.4; cursor: not-allowed; }
.selected-item { background: #EEF6FF; margin-bottom: 4px; cursor: default; }
.selected-item:hover { background: #E1F0FF; }
.order-num { min-width: 22px; height: 22px; background: #409EFF; color: white; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; margin-right: 8px; }
.q-section { margin-left: 32px; }
@media (max-width: 767px) {
  .q-section { margin-left: 0; }
  .q-pool { max-height: 300px; }
}
.score-val { font-size: 20px; font-weight: bold; color: #409EFF; }
.score-val.pass { color: #67C23A; }
.score-hint { font-size: 12px; color: #909399; margin-left: 6px; }
</style>
