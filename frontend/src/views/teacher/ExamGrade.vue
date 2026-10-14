<template>
  <div class="workflow-page exam-grading">
    <div class="workflow-context"><el-button link :icon="Back" @click="$router.push('/exams')">返回考试列表</el-button><el-button @click="$router.push('/exams/' + examId + '/stats')">考试统计</el-button></div>
    <el-card>
      <template #header><div class="workflow-heading"><div><h2>{{ examTitle || '试卷批改' }}</h2><p class="workflow-caption">查看学生作答，为简答题评分并保存</p></div><span class="workflow-secondary">待批改 {{ total }} 份答卷</span></div></template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-alert v-else-if="loadError" type="error" :closable="false" title="待批改答卷加载失败"><el-button link type="primary" @click="initialize">重新加载</el-button></el-alert>
      <el-empty v-else-if="!records.length" :image-size="72" description="暂无需要批改的答卷" />
      <el-collapse v-else v-model="activeRecord" accordion class="grading-records" @change="loadRecordDetails">
        <el-collapse-item v-for="record in records" :key="record.id" :name="record.id">
          <template #title><div class="grading-record-heading"><span class="grading-student">{{ record.student_name || record.username || '学生 #' + record.student_id }}</span><el-tag :type="record.has_pending ? 'warning' : 'success'" size="small">{{ record.has_pending ? '待批改' : '已批改' }}</el-tag><span class="workflow-secondary">当前总分 {{ record.total_score ?? '未评分' }}</span></div></template>
          <div class="grading-detail">
            <el-skeleton v-if="record.details_loading" :rows="5" animated />
            <el-alert v-else-if="record.details_error" type="error" :closable="false" title="答题内容加载失败"><el-button link type="primary" @click="loadRecordDetails(record.id)">重新加载</el-button></el-alert>
            <template v-else-if="record.details_loaded">
              <AnswerReview v-for="answer in record.essay_answers" :key="answer.id" :question="answer" :index="answer.question_index" reveal-answers pending answer-label="学生作答">
                <div class="grade-score-control"><label :for="'grade-answer-' + answer.id">本题评分</label><el-input-number :id="'grade-answer-' + answer.id" v-model="answer.score_got" class="number-stepper" :min="0" :max="answer.max_score ?? 10" :disabled="record.saving" @change="onScoreChange(answer)" /><span>/ {{ answer.max_score ?? 10 }} 分</span><el-tag :type="correctnessTag(answer.is_correct)" size="small">{{ correctnessLabel(answer.is_correct) }}</el-tag></div>
              </AnswerReview>
              <div class="grading-save"><span class="workflow-secondary">修改仅在保存后生效</span><el-button type="primary" :loading="record.saving" :disabled="!record.essay_answers.length" @click="saveGrade(record)">保存批改</el-button></div>
            </template>
          </div>
        </el-collapse-item>
      </el-collapse>
      <footer v-if="!loadError && total" class="workflow-pagination"><span class="workflow-secondary">共 {{ total }} 份待批改答卷</span><el-pagination v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize" :total="total" :pager-count="5" :disabled="records.some(record => record.saving)" layout="prev, pager, next" small @current-change="loadRecords" /></footer>
    </el-card>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back } from '@element-plus/icons-vue'
import { examApi } from '@/api'
import { correctnessForScore } from '@/utils/grading'
import AnswerReview from '@/components/lists/AnswerReview.vue'
import '@/styles/workflow.css'
const route = useRoute(), examId = computed(() => route.params.id)
const loading = ref(true), loadError = ref(false), examTitle = ref(''), records = ref([]), page = ref(1), total = ref(0), activeRecord = ref('')
const pageSize = 10
let recordsRequestId = 0, initRequestId = 0, disposed = false
function onScoreChange(answer) { answer.is_correct = correctnessForScore(answer.score_got, answer.max_score ?? 10) }
function correctnessLabel(value) { return { Y: '正确', P: '部分得分', N: '错误' }[value] || '未评分' }
function correctnessTag(value) { return { Y: 'success', P: 'warning', N: 'danger' }[value] || 'info' }
async function saveGrade(record) {
  if (record.saving || !record.details_loaded) return
  if (record.essay_answers.some(answer => !Number.isFinite(answer.score_got) || answer.score_got < 0 || answer.score_got > (answer.max_score ?? 10))) return ElMessage.warning('请为每道简答题填写有效分数')
  record.saving = true
  const id = examId.value
  try {
    const response = await examApi.grade(id, record.id, { grades: record.essay_answers.map(answer => ({ answer_id: answer.id, score_got: answer.score_got, is_correct: correctnessForScore(answer.score_got, answer.max_score ?? 10) })) })
    if (disposed || id !== examId.value) return
    record.total_score = response.total_score
    record.essay_graded = response.essay_graded
    ElMessage.success('批改已保存')
  } catch { ElMessage.error('批改未保存，请重试'); return }
  finally { record.saving = false }
  if (records.value.length === 1 && page.value > 1) page.value--
  await loadRecords(page.value)
  if (loadError.value) ElMessage.warning('批改已保存，但列表刷新失败，请重新加载')
}
function mapRecord(record) {
  return reactive({ ...record, saving: false, details_loaded: false, details_loading: false, details_error: false, essay_answers: [], get has_pending() { return !this.essay_graded } })
}
async function loadRecordDetails(id) {
  if (!id) return
  const record = records.value.find(item => String(item.id) === String(id))
  if (!record || record.details_loaded || record.details_loading) return
  record.details_loading = true
  record.details_error = false
  try {
    const detail = await examApi.record(examId.value, record.id)
    if (disposed || !records.value.includes(record)) return
    record.essay_answers = (detail.questions || []).map((question, index) => ({ ...question, question_index: index })).filter(q => q.question_type === 'essay').map(question => reactive({
      id: question.answer_id, question_id: question.question_id, question_index: question.question_index, question_type: 'essay', question_title: question.title,
      correct_answer: question.answer, user_answer: question.user_answer, score_got: question.score_got, max_score: question.score,
      is_correct: question.score_got == null ? null : correctnessForScore(question.score_got, question.score ?? 10),
    }))
    record.details_loaded = true
  } catch { record.details_error = true }
  finally { record.details_loading = false }
}
async function loadRecords(nextPage = page.value) {
  const version = ++recordsRequestId
  loading.value = true; loadError.value = false
  try {
    const response = await examApi.records(examId.value, { status: 'submitted', page: nextPage, size: pageSize })
    if (version !== recordsRequestId || disposed) return
    page.value = response.page || nextPage
    total.value = response.total || 0
    records.value = (response.items || []).map(mapRecord)
    activeRecord.value = ''
  } catch { if (version === recordsRequestId && !disposed) loadError.value = true }
  finally { if (version === recordsRequestId && !disposed) loading.value = false }
}
async function initialize() {
  const version = ++initRequestId
  ++recordsRequestId
  loading.value = true; loadError.value = false
  try {
    const exam = await examApi.get(examId.value)
    if (version !== initRequestId || disposed) return
    examTitle.value = exam.title
    await loadRecords(1)
  } catch { if (version === initRequestId && !disposed) { loadError.value = true; loading.value = false } }
}
watch(examId, initialize, { immediate: true })
onBeforeUnmount(() => { disposed = true; ++recordsRequestId; ++initRequestId })
</script>
<style scoped>
.grading-records { border-top: 0; }
.grading-records :deep(.el-collapse-item__header) { min-height: 76px; height: auto; line-height: 1.7; padding: 14px 4px; text-align: left; }
.grading-record-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 10px 16px; padding-right: 16px; }
.grading-student { font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
.grading-detail { padding: 8px 4px 4px; }
.grade-score-control { display: flex; align-items: center; flex-wrap: wrap; gap: 10px 14px; font-size: 13px; }
.grade-score-control .number-stepper { width: 144px; }
.grade-score-control > span { color: var(--app-text-secondary); }
.grading-save { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 14px; padding: 20px 0 0; border-top: 1px solid var(--app-border); }
</style>
