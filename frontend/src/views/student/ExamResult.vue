<template>
  <div class="workflow-page exam-result">
    <div class="workflow-context"><el-button link :icon="Back" @click="$router.push(store.isStudent ? '/my-records' : '/exams')">{{ store.isStudent ? '返回我的成绩' : '返回考试列表' }}</el-button></div>
    <el-skeleton v-if="loading" :rows="7" animated />
    <el-result v-else-if="loadError" icon="error" title="考试结果加载失败" sub-title="请检查网络或后端服务后重试"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
    <el-result v-else-if="!record" icon="info" title="暂无考试记录" sub-title="你还未参加该考试"><template #extra><el-button type="primary" @click="$router.push('/exams/' + examId + '/take')">参加考试</el-button></template></el-result>
    <template v-else>
      <el-card><template #header><div class="workflow-heading"><div><h2>{{ exam?.title || '考试结果' }}</h2><p class="workflow-caption">回顾本次作答，了解知识掌握情况</p></div><el-tag :type="resultStatus.tone">{{ resultStatus.label }}</el-tag></div></template>
        <div class="result-overview"><div class="result-score"><span class="workflow-secondary">{{ record.result_released && !record.essay_graded ? '客观题得分' : '本次成绩' }}</span><strong :class="{ 'is-message': typeof displayedScore !== 'number' }">{{ displayedScore }}<small v-if="typeof displayedScore === 'number'"> 分</small></strong><span class="workflow-secondary">卷面满分 {{ exam?.total_score ?? '未提供' }} 分 · 及格 {{ exam?.pass_score ?? '未提供' }} 分</span></div><div class="result-facts"><div><span>提交时间</span><strong>{{ formatAppDateTime(record.submitted_at) }}</strong></div><div><span>成绩状态</span><strong>{{ resultNote }}</strong></div></div></div>
        <div class="result-links workflow-actions"><el-button v-if="record.status === 'in_progress'" type="primary" @click="$router.push('/exams/' + examId + '/take')">继续考试</el-button><el-button v-else-if="record.result_released" @click="$router.push('/exams/' + examId + '/leaderboard')">查看排行榜</el-button></div>
      </el-card>
      <el-card><template #header><div class="workflow-heading"><h2>答题详情<span class="workflow-count">{{ answerRows.length }} 题</span></h2><span v-if="!record.result_released" class="workflow-secondary">答案与解析将在成绩公布后显示</span></div></template>
        <el-alert v-if="wrongBookError" class="workflow-error" type="warning" :closable="false" title="错题本状态加载失败，暂时不可操作"><el-button link type="primary" @click="loadWrongIds">重试</el-button></el-alert>
        <AnswerReview v-for="(row, index) in answerRows" :key="row.question_id" :question="row" :index="index" :reveal-answers="!!record.result_released" :pending="row.question_type === 'essay' && !record.essay_graded">
          <template v-if="store.isStudent && record.result_released && row.is_correct === 'N' && row.user_answer"><span class="workflow-secondary">将这道题加入复习计划</span><el-button link :type="wrongBookIds.has(row.question_id) ? 'info' : 'primary'" :disabled="wrongBookError || wrongBusy.has(row.question_id)" @click="toggleWrong(row.question_id)">{{ wrongBookIds.has(row.question_id) ? '移出错题本' : '加入错题本' }}</el-button></template>
        </AnswerReview>
        <el-empty v-if="!answerRows.length" :image-size="64" description="暂无答题明细" />
      </el-card>
    </template>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { examApi, studentApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatAppDateTime } from '@/utils/examTime'
import { recordState, reviewQuestion } from '@/utils/examPresentation'
import AnswerReview from '@/components/lists/AnswerReview.vue'
import '@/styles/workflow.css'
const route = useRoute(), store = useUserStore()
const examId = computed(() => route.params.id), requestedRecordId = computed(() => route.query.recordId)
const loading = ref(true), loadError = ref(false), exam = ref(null), record = ref(null)
const wrongBookIds = ref(new Set()), wrongBusy = ref(new Set()), wrongBookError = ref(false)
let requestId = 0, wrongRequestId = 0, disposed = false
const resultStatus = computed(() => recordState(record.value || {}))
const answerRows = computed(() => (record.value?.answers || record.value?.questions || []).map(reviewQuestion))
const objectiveScore = computed(() => answerRows.value.filter(row => row.question_type !== 'essay').reduce((sum, row) => sum + (row.score_got ?? 0), 0))
const displayedScore = computed(() => record.value?.status === 'in_progress' ? '尚未提交' : !record.value?.result_released ? '暂未公布' : record.value?.essay_graded ? (record.value.total_score ?? '未提供') : objectiveScore.value)
const resultNote = computed(() => record.value?.status === 'in_progress' ? '考试尚未提交，可以继续作答' : !record.value?.essay_graded ? '主观题待批改，当前成绩并非最终成绩' : !record.value?.result_released ? '成绩暂未公布，请等待考试结束' : '成绩已公布，可查看答案与解析')
async function loadWrongIds() {
  const version = ++wrongRequestId
  wrongBookError.value = true
  try { const response = await studentApi.wrongBookIds(); if (version === wrongRequestId && !disposed) { wrongBookIds.value = new Set(response.ids || []); wrongBookError.value = false } }
  catch { /* Disable actions until the server's membership state is known. */ }
}
async function toggleWrong(id) {
  if (wrongBookError.value || wrongBusy.value.has(id) || !store.isStudent || !record.value?.result_released) return
  wrongBusy.value.add(id)
  try {
    if (wrongBookIds.value.has(id)) { await studentApi.removeWrong(id); wrongBookIds.value.delete(id); ElMessage.success('已从错题本移出') }
    else { await studentApi.addWrong(id); wrongBookIds.value.add(id); ElMessage.success('已加入错题本') }
  } catch { /* Retain the previous membership on request failure. */ }
  finally { wrongBusy.value.delete(id) }
}
async function load() {
  const version = ++requestId
  ++wrongRequestId
  loading.value = true; loadError.value = false
  wrongBookIds.value = new Set()
  try {
    const [examInfo, response] = await Promise.all([examApi.get(examId.value), requestedRecordId.value ? examApi.record(examId.value, requestedRecordId.value) : examApi.myRecord(examId.value)])
    if (version !== requestId || disposed) return
    exam.value = examInfo
    record.value = requestedRecordId.value ? response : (response?.exists ? response : null)
    if (store.isStudent && record.value?.result_released) await loadWrongIds()
  } catch { if (version === requestId && !disposed) loadError.value = true }
  finally { if (version === requestId && !disposed) loading.value = false }
}
watch([examId, requestedRecordId], load, { immediate: true })
onBeforeUnmount(() => { disposed = true; ++requestId; ++wrongRequestId })
</script>
<style scoped>
.result-overview { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1.3fr); gap: 32px; align-items: center; }
.result-score { display: grid; gap: 10px; }
.result-score > strong { font-size: 40px; line-height: 1.4; font-weight: 600; letter-spacing: -1px; font-variant-numeric: tabular-nums; }
.result-score > strong.is-message { font-size: 26px; letter-spacing: 0; }
.result-score small { font-size: 13px; font-weight: 400; color: var(--app-text-secondary); }
.result-facts { display: grid; gap: 20px; }
.result-facts > div { display: grid; gap: 7px; font-size: 13px; }
.result-facts span { color: var(--app-text-secondary); font-size: 12px; }
.result-facts strong { font-weight: 400; overflow-wrap: anywhere; }
.result-links:empty { display: none; } .result-links { margin-top: 24px; }
@media (max-width: 767px) { .result-overview { grid-template-columns: minmax(0,1fr); gap: 24px; } .result-facts { padding-top: 20px; border-top: 1px solid var(--app-border); } }
</style>
