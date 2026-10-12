<template>
  <div v-loading="loading">
    <el-result v-if="!loading && loadError" icon="error" title="考试结果加载失败" sub-title="请检查网络或后端服务后重试">
      <template #extra>
        <el-button type="primary" @click="load">重新加载</el-button>
      </template>
    </el-result>

    <el-result v-else-if="!loading && !record" icon="info" title="暂无考试记录" sub-title="您还未参加该考试">
      <template #extra>
        <el-button type="primary" @click="$router.push(`/exams/${examId}/take`)">参加考试</el-button>
      </template>
    </el-result>

    <template v-else-if="record">
      <el-card style="margin-bottom:16px">
        <el-descriptions :title="`考试结果 - ${exam?.title || ''}`" :column="3" border>
          <el-descriptions-item label="得分">
            <span v-if="record.status === 'in_progress'" style="font-size:16px;color:#909399">
              尚未提交
            </span>
            <span v-else-if="!record.result_released" style="font-size:16px;color:#909399">
              成绩暂未公布
            </span>
            <span v-else-if="record.essay_graded" style="font-size:24px;font-weight:bold;color:#409EFF">
              {{ record.total_score ?? '-' }}
            </span>
            <span v-else style="font-size:16px;color:#E6A23C">
              {{ objectiveScore }}
              <span style="font-size:12px;color:#909399">（主观题待批改，暂不计入总分）</span>
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="resultStatus.type">
              {{ resultStatus.text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatAppDateTime(record.submitted_at) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card style="margin-bottom:16px">
        <template #header>答题详情</template>
        <div
          v-for="(row, idx) in answerRows"
          :key="row.question_id"
          style="border:1px solid #EBEEF5;border-radius:6px;padding:14px;margin-bottom:12px"
        >
          <div style="display:flex;align-items:flex-start;gap:10px;margin-bottom:8px">
            <span style="flex-shrink:0;font-weight:bold;color:#606266">{{ idx + 1 }}.</span>
            <span style="flex:1">{{ row.title }}</span>
            <el-tag size="small" :type="typeTag(row.type)" style="flex-shrink:0">{{ typeLabel(row.type) }}</el-tag>
            <el-tag v-if="record.result_released && row.is_correct === 'Y'" type="success" size="small">正确</el-tag>
            <el-tag v-else-if="record.result_released && row.is_correct === 'N'" type="danger" size="small">错误</el-tag>
            <el-tag v-else-if="record.result_released && row.is_correct === 'P' && row.type === 'essay' && !record.essay_graded" type="warning" size="small">待批改</el-tag>
            <el-tag v-else-if="record.result_released && row.is_correct === 'P' && row.type === 'essay'" type="warning" size="small">部分得分</el-tag>
            <el-tag v-else-if="record.result_released && row.is_correct === 'P'" type="warning" size="small">部分正确</el-tag>
            <span v-if="record.result_released" style="flex-shrink:0;font-size:13px;color:#409EFF;font-weight:bold">
              <template v-if="row.score_got !== null">{{ row.score_got }}/{{ row.max_score }}分</template>
              <template v-else><span style="color:#E6A23C">待批改</span></template>
            </span>
            <el-button
              v-if="store.isStudent && record.result_released && row.is_correct === 'N' && row.user_answer"
              link
              :type="wrongBookIds.has(row.question_id) ? 'warning' : 'info'"
              size="small"
              style="flex-shrink:0"
              @click="toggleWrong(row.question_id)"
            >{{ wrongBookIds.has(row.question_id) ? '移出错题本' : '加入错题本' }}</el-button>
          </div>
          <div style="font-size:13px;color:#606266;line-height:1.8;padding-left:18px">
            <div><span style="color:#909399">我的答案：</span>{{ row.user_answer || '（未作答）' }}</div>
            <div v-if="record.result_released && row.correct_answer != null">
              <span style="color:#909399">正确答案：</span>
              <span :style="row.is_correct !== 'Y' ? 'color:#67C23A;font-weight:bold' : ''">{{ row.correct_answer }}</span>
            </div>
            <div v-if="record.result_released && row.explanation" style="margin-top:4px;background:#F5F7FA;padding:6px 10px;border-radius:4px">
              <span style="color:#909399">解析：</span>{{ row.explanation }}
            </div>
          </div>
        </div>
      </el-card>

      <el-button v-if="record.status === 'in_progress'" type="primary" @click="$router.push(`/exams/${examId}/take`)">继续考试</el-button>
      <el-button v-else-if="record.result_released" type="primary" plain @click="$router.push(`/exams/${examId}/leaderboard`)">查看排行榜</el-button>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { examApi, studentApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatAppDateTime } from '@/utils/examTime'

const route = useRoute()
const store = useUserStore()
const examId = computed(() => route.params.id)
const requestedRecordId = computed(() => route.query.recordId)
const loading = ref(true)
const exam = ref(null)
const record = ref(null)
const wrongBookIds = ref(new Set())
const loadError = ref(false)

const resultStatus = computed(() => {
  if (record.value?.status === 'in_progress') return { type: 'primary', text: '进行中' }
  if (record.value?.status === 'submitted' || !record.value?.essay_graded) return { type: 'warning', text: '主观题待批改' }
  if (!record.value?.result_released) return { type: 'info', text: '成绩暂未公布' }
  return { type: 'success', text: '已出分' }
})

function typeTag(type) {
  return { single: '', multiple: 'warning', truefalse: 'success', essay: 'info' }[type] || ''
}

function typeLabel(type) {
  return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[type] || type
}

const answerRows = computed(() => {
  const answers = record.value?.answers || record.value?.questions
  if (!answers) return []
  return answers.map((answer) => ({
    ...answer,
    title: answer.question_title || answer.title || `题目#${answer.question_id}`,
    type: answer.question_type || '',
    max_score: answer.max_score ?? answer.score ?? '-',
    correct_answer: answer.correct_answer ?? answer.answer ?? null,
    explanation: answer.explanation || '',
  }))
})

const objectiveScore = computed(() => {
  return answerRows.value
    .filter((answer) => answer.type !== 'essay')
    .reduce((sum, answer) => sum + (answer.score_got ?? 0), 0)
})

async function toggleWrong(questionId) {
  if (wrongBookIds.value.has(questionId)) {
    await studentApi.removeWrong(questionId)
    wrongBookIds.value.delete(questionId)
    ElMessage.success('已从错题本移出')
  } else {
    await studentApi.addWrong(questionId)
    wrongBookIds.value.add(questionId)
    ElMessage.success('已加入错题本')
  }
  wrongBookIds.value = new Set(wrongBookIds.value)
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const recordRequest = requestedRecordId.value
      ? examApi.record(examId.value, requestedRecordId.value)
      : examApi.myRecord(examId.value)
    const [examInfo, response] = await Promise.all([
      examApi.get(examId.value),
      recordRequest,
    ])
    exam.value = examInfo
    record.value = requestedRecordId.value ? response : (response?.exists ? response : null)

    if (store.isStudent && record.value?.result_released) {
      const response = await studentApi.wrongBookIds().catch(() => ({ ids: [] }))
      wrongBookIds.value = new Set(response.ids || [])
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
