<template>
  <div v-loading="loading">
    <el-result v-if="!loading && !record" icon="info" title="暂无考试记录" sub-title="您还未参加该考试">
      <template #extra>
        <el-button type="primary" @click="$router.push(`/exams/${examId}/take`)">参加考试</el-button>
      </template>
    </el-result>

    <template v-else-if="record">
      <el-card style="margin-bottom:16px">
        <el-descriptions :title="`考试结果 - ${exam?.title || ''}`" :column="3" border>
          <el-descriptions-item label="得分">
            <span v-if="record.essay_graded" style="font-size:24px;font-weight:bold;color:#409EFF">
              {{ record.total_score ?? '-' }}
            </span>
            <span v-else style="font-size:16px;color:#E6A23C">
              {{ objectiveScore }}
              <span style="font-size:12px;color:#909399">（主观题待批改，暂不计入总分）</span>
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="record.essay_graded ? 'success' : 'warning'">
              {{ record.essay_graded ? '已出分' : '主观题待批改' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ record.submitted_at ? new Date(record.submitted_at).toLocaleString() : '-' }}
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
            <el-tag v-if="row.is_correct === 'Y'" type="success" size="small">正确</el-tag>
            <el-tag v-else-if="row.is_correct === 'N'" type="danger" size="small">错误</el-tag>
            <el-tag v-else-if="row.is_correct === 'P' && row.type === 'essay' && !record.essay_graded" type="warning" size="small">待批改</el-tag>
            <el-tag v-else-if="row.is_correct === 'P' && row.type === 'essay'" type="warning" size="small">部分得分</el-tag>
            <el-tag v-else-if="row.is_correct === 'P'" type="warning" size="small">部分正确</el-tag>
            <span style="flex-shrink:0;font-size:13px;color:#409EFF;font-weight:bold">
              <template v-if="row.score_got !== null">{{ row.score_got }}/{{ row.max_score }}分</template>
              <template v-else><span style="color:#E6A23C">待批改</span></template>
            </span>
            <el-button
              v-if="store.isStudent && row.is_correct === 'N' && row.user_answer"
              link
              :type="wrongBookIds.has(row.question_id) ? 'warning' : 'info'"
              size="small"
              style="flex-shrink:0"
              @click="toggleWrong(row.question_id)"
            >{{ wrongBookIds.has(row.question_id) ? '移出错题本' : '加入错题本' }}</el-button>
          </div>
          <div style="font-size:13px;color:#606266;line-height:1.8;padding-left:18px">
            <div><span style="color:#909399">我的答案：</span>{{ row.user_answer || '（未作答）' }}</div>
            <div v-if="row.type !== 'essay' || record.essay_graded">
              <span style="color:#909399">正确答案：</span>
              <span :style="row.is_correct !== 'Y' ? 'color:#67C23A;font-weight:bold' : ''">{{ row.correct_answer }}</span>
            </div>
            <div v-if="row.explanation" style="margin-top:4px;background:#F5F7FA;padding:6px 10px;border-radius:4px">
              <span style="color:#909399">解析：</span>{{ row.explanation }}
            </div>
          </div>
        </div>
      </el-card>

      <el-button type="primary" plain @click="$router.push(`/exams/${examId}/leaderboard`)">查看排行榜</el-button>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { examApi, studentApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const store = useUserStore()
const examId = computed(() => route.params.id)
const loading = ref(true)
const exam = ref(null)
const record = ref(null)
const wrongBookIds = ref(new Set())

function typeTag(type) {
  return { single: '', multiple: 'warning', truefalse: 'success', essay: 'info' }[type] || ''
}

function typeLabel(type) {
  return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[type] || type
}

const answerRows = computed(() => {
  if (!record.value?.answers) return []
  return record.value.answers.map((answer) => ({
    ...answer,
    title: answer.question_title || `题目#${answer.question_id}`,
    type: answer.question_type || '',
    max_score: answer.max_score ?? '-',
    correct_answer: answer.correct_answer || '-',
    explanation: answer.explanation || '',
  }))
})

const objectiveScore = computed(() => {
  if (!record.value?.answers) return 0
  return record.value.answers
    .filter((answer) => answer.question_type !== 'essay')
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

onMounted(async () => {
  try {
    exam.value = await examApi.get(examId.value).catch(() => null)
    record.value = await examApi.myRecord(examId.value).catch(() => null)

    if (store.isStudent) {
      const response = await studentApi.wrongBookIds().catch(() => ({ ids: [] }))
      wrongBookIds.value = new Set(response.ids || [])
    }
  } finally {
    loading.value = false
  }
})
</script>
