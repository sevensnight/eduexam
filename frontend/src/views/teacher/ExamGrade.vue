<template>
  <el-card v-loading="loading">
    <template #header>
      <span>简答题批改 - {{ examTitle }}</span>
    </template>

    <el-empty v-if="!loading && !records.length" description="暂无需要批改的答卷" />

    <el-collapse v-else accordion @change="loadRecordDetails">
      <el-collapse-item v-for="record in records" :key="record.id" :name="record.id">
        <template #title>
          <span>{{ record.student_name }}</span>
          <el-tag style="margin-left:12px" :type="record.has_pending ? 'warning' : 'success'" size="small">
            {{ record.has_pending ? '待批改' : '已批改' }}
          </el-tag>
          <span style="margin-left:12px;color:#909399;font-size:13px">总分 {{ record.total_score ?? '-' }}</span>
        </template>

        <div v-loading="record.details_loading" style="min-height:80px">
          <template v-if="record.details_loaded">
        <div
          v-for="answer in record.essay_answers"
          :key="answer.id"
          style="margin-bottom:20px;padding:12px;background:#fafafa;border-radius:6px"
        >
          <div style="font-weight:bold;margin-bottom:6px">{{ answer.question_title || `题目#${answer.question_id}` }}</div>
          <div style="margin-bottom:8px">
            <el-tag size="small" type="info">参考答案</el-tag>
            <span style="margin-left:8px;color:#606266">{{ answer.correct_answer || '-' }}</span>
          </div>
          <div style="margin-bottom:8px">
            <el-tag size="small">学生作答</el-tag>
            <span style="margin-left:8px">{{ answer.user_answer || '（未作答）' }}</span>
          </div>
          <div style="display:flex;align-items:center;gap:12px">
            <span>得分：</span>
            <el-input-number
              v-model="answer.score_got"
              :min="0"
              :max="answer.max_score ?? 10"
              size="small"
              style="width:100px"
              @change="onScoreChange(answer)"
            />
            <span style="color:#909399">/ {{ answer.max_score ?? 10 }} 分</span>
            <el-tag :type="correctnessTag(answer.is_correct)">
              {{ correctnessLabel(answer.is_correct) }}
            </el-tag>
          </div>
        </div>

        <el-button type="primary" :loading="record.saving" @click="saveGrade(record)">保存批改</el-button>
          </template>
        </div>
      </el-collapse-item>
    </el-collapse>

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next, total"
      style="margin-top:20px;justify-content:flex-end"
      @current-change="loadRecords"
    />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { examApi } from '@/api'
import { correctnessForScore } from '@/utils/grading'

const route = useRoute()
const examId = route.params.id
const loading = ref(true)
const examTitle = ref('')
const records = ref([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
let recordsRequestId = 0

function onScoreChange(answer) {
  const maxScore = answer.max_score ?? 10
  answer.is_correct = correctnessForScore(answer.score_got, maxScore)
}

function correctnessLabel(value) {
  return { Y: '正确', P: '部分得分', N: '错误' }[value] || '未评分'
}

function correctnessTag(value) {
  return { Y: 'success', P: 'warning', N: 'danger' }[value] || 'info'
}

async function saveGrade(record) {
  record.saving = true
  try {
    const response = await examApi.grade(examId, record.id, {
      grades: record.essay_answers.map((answer) => ({
        answer_id: answer.id,
        score_got: answer.score_got,
        is_correct: answer.is_correct,
      })),
    })
    record.total_score = response.total_score
    record.essay_graded = response.essay_graded
    ElMessage.success('批改已保存')
  } catch {
    ElMessage.error('批改未保存，请重试')
    return
  } finally {
    record.saving = false
  }
  total.value = Math.max(0, total.value - 1)
  if (records.value.length === 1 && page.value > 1) page.value--
  try {
    await loadRecords(page.value)
  } catch {
    ElMessage.warning('批改已保存，但列表刷新失败，请手动刷新页面')
  }
}

function mapRecord(record) {
  return reactive({
    ...record,
    saving: false,
    details_loaded: false,
    details_loading: false,
    essay_answers: [],
    get has_pending() {
      return !this.essay_graded
    },
  })
}

async function loadRecordDetails(activeId) {
  if (!activeId) return
  const record = records.value.find(item => String(item.id) === String(activeId))
  if (!record || record.details_loaded || record.details_loading) return
  // Keep already loaded rows on this bounded page. Clearing them when another
  // accordion item opens would silently discard unsaved grading edits.
  record.details_loading = true
  try {
    const detail = await examApi.record(examId, record.id)
    record.essay_answers = (detail.questions || [])
      .filter(question => question.question_type === 'essay')
      .map(question => reactive({
        id: question.answer_id,
        question_id: question.question_id,
        question_title: question.title,
        correct_answer: question.answer,
        user_answer: question.user_answer,
        score_got: question.score_got,
        max_score: question.score,
        is_correct: correctnessForScore(question.score_got, question.score ?? 10),
      }))
    record.details_loaded = true
  } finally {
    record.details_loading = false
  }
}

async function loadRecords(nextPage = page.value) {
  const requestId = ++recordsRequestId
  loading.value = true
  try {
    const response = await examApi.records(examId, {
      status: 'submitted', page: nextPage, size: pageSize,
    })
    if (requestId === recordsRequestId) {
      page.value = response.page || nextPage
      total.value = response.total || 0
      records.value = (response.items || []).map(mapRecord)
    }
  } finally {
    if (requestId === recordsRequestId) loading.value = false
  }
}

onMounted(async () => {
  const exam = await examApi.get(examId)
  examTitle.value = exam.title
  await loadRecords(1)
})
</script>
