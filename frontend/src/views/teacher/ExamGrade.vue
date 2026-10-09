<template>
  <el-card v-loading="loading">
    <template #header>
      <span>简答题批改 - {{ examTitle }}</span>
    </template>

    <el-empty v-if="!loading && !records.length" description="暂无需要批改的答卷" />

    <el-collapse v-else accordion>
      <el-collapse-item v-for="record in records" :key="record.id" :name="record.id">
        <template #title>
          <span>{{ record.student_name }}</span>
          <el-tag style="margin-left:12px" :type="record.has_pending ? 'warning' : 'success'" size="small">
            {{ record.has_pending ? '待批改' : '已批改' }}
          </el-tag>
          <span style="margin-left:12px;color:#909399;font-size:13px">总分 {{ record.total_score ?? '-' }}</span>
        </template>

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
            <el-select v-model="answer.is_correct" size="small" style="width:90px" @change="onCorrectChange(answer)">
              <el-option label="正确" value="Y" />
              <el-option label="部分" value="P" />
              <el-option label="错误" value="N" />
            </el-select>
          </div>
        </div>

        <el-button type="primary" :loading="record.saving" @click="saveGrade(record)">保存批改</el-button>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { examApi } from '@/api'

const route = useRoute()
const examId = route.params.id
const loading = ref(true)
const examTitle = ref('')
const records = ref([])

function onCorrectChange(answer) {
  const maxScore = answer.max_score ?? 10
  if (answer.is_correct === 'Y') answer.score_got = maxScore
  else if (answer.is_correct === 'N') answer.score_got = 0
}

function onScoreChange(answer) {
  const maxScore = answer.max_score ?? 10
  if (answer.is_correct === 'Y' && answer.score_got < maxScore) {
    answer.is_correct = 'P'
  }
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
    ElMessage.error('保存失败')
  } finally {
    record.saving = false
  }
}

onMounted(async () => {
  try {
    const [exam, rawRecords] = await Promise.all([
      examApi.get(examId),
      examApi.records(examId),
    ])
    examTitle.value = exam.title
    records.value = rawRecords
      .map((record) => reactive({
        ...record,
        saving: false,
        essay_answers: record.answers
          .filter((answer) => answer.question_type === 'essay')
          .map((answer) => reactive({ ...answer })),
        get has_pending() {
          return !this.essay_graded
        },
      }))
      .filter((record) => record.essay_answers.length > 0)
  } finally {
    loading.value = false
  }
})
</script>
