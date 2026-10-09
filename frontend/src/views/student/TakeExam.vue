<template>
  <div class="exam-page" v-loading="loading">
    <div v-if="exam && !submitted" class="exam-layout">
      <!-- 左侧进度导航 -->
      <div class="progress-nav">
        <div class="nav-title">答题进度</div>
        <div class="nav-grid">
          <button
            type="button"
            v-for="(eq, idx) in exam.exam_questions"
            :key="eq.question_id"
            class="nav-item"
            :class="isAnswered(eq) ? 'answered' : 'unanswered'"
            @click="scrollTo(idx)"
            :aria-label="'跳到第 ' + (idx + 1) + ' 题'"
          >{{ idx + 1 }}</button>
        </div>
        <div class="nav-legend">
          <span class="legend-dot answered"></span>已答
          <span class="legend-dot unanswered" style="margin-left:8px"></span>未答
        </div>
        <div class="nav-stats">{{ answeredCount }}/{{ exam.exam_questions.length }}</div>
      </div>

      <!-- 右侧答题区 -->
      <div class="exam-main">
        <div class="exam-header">
          <div>
            <h2>{{ exam.title }}</h2>
            <el-text type="info">{{ exam.description }}</el-text>
          </div>
          <div class="timer" :class="{ urgent: timeLeft < 300 }">
            <el-icon><Timer /></el-icon>
            剩余时间：{{ formatTime(timeLeft) }}
          </div>
        </div>

        <div class="questions-area">
          <el-card
            v-for="(eq, idx) in exam.exam_questions"
            :key="eq.question_id"
            style="margin-bottom:16px"
            :id="`q-${idx}`"
          >
            <div class="q-header">
              <span class="q-num">第 {{ idx + 1 }} 题</span>
              <el-tag size="small" :type="typeTag(eq.question_type)">{{ typeLabel(eq.question_type) }}</el-tag>
              <span class="q-score">（{{ eq.score }} 分）</span>
            </div>
            <div class="q-title">{{ eq.question_title }}</div>

            <el-radio-group v-if="eq.question_type === 'single'" v-model="answers[eq.question_id]" class="options">
              <el-radio v-for="opt in parseOptions(eq.options)" :key="opt" :value="opt.charAt(0)" class="option-item">
                {{ opt }}
              </el-radio>
            </el-radio-group>

            <el-checkbox-group v-else-if="eq.question_type === 'multiple'" v-model="multiAnswers[eq.question_id]" class="options">
              <el-checkbox v-for="opt in parseOptions(eq.options)" :key="opt" :value="opt.charAt(0)" class="option-item">
                {{ opt }}
              </el-checkbox>
            </el-checkbox-group>

            <el-radio-group v-else-if="eq.question_type === 'truefalse'" v-model="answers[eq.question_id]" class="options">
              <el-radio value="True">正确</el-radio>
              <el-radio value="False">错误</el-radio>
            </el-radio-group>

            <el-input v-else v-model="answers[eq.question_id]" type="textarea" :rows="4" placeholder="请输入你的答案" style="margin-top:12px" />
          </el-card>

          <div style="text-align:center;margin-top:20px">
            <el-button type="primary" size="large" @click="confirmSubmit" :loading="submitting">提交答卷</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-result v-if="submitted" icon="success" title="答卷已提交" :sub-title="`得分：${recordResult?.total_score ?? '-'} 分`">
      <template #extra>
        <el-button type="primary" @click="$router.push(`/exams/${examId}/result`)">查看详情</el-button>
        <el-button @click="$router.push(`/exams/${examId}/leaderboard`)">排行榜</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Timer } from '@element-plus/icons-vue'
import { examApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const examId = computed(() => route.params.id)
const loading = ref(true)
const submitting = ref(false)
const submitted = ref(false)
const exam = ref(null)
const record = ref(null)
const recordResult = ref(null)
const answers = reactive({})
const multiAnswers = reactive({})
const timeLeft = ref(0)
let timer = null
let warnedFiveMin = false

function draftKey() { return `exam_draft_${store.user?.id || 'guest'}_${examId.value}` }
function saveDraft() {
  sessionStorage.setItem(draftKey(), JSON.stringify({ answers: { ...answers }, multiAnswers: { ...multiAnswers } }))
}
function clearDraft() { sessionStorage.removeItem(draftKey()) }
function restoreDraft() {
  try {
    const raw = sessionStorage.getItem(draftKey())
    if (!raw) return
    const { answers: a, multiAnswers: m } = JSON.parse(raw)
    Object.assign(answers, a)
    Object.assign(multiAnswers, m)
  } catch {}
}

function formatTime(secs) {
  const m = Math.floor(secs / 60).toString().padStart(2, '0')
  const s = (secs % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

function typeTag(t) { return { single: '', multiple: 'warning', truefalse: 'success', essay: 'info' }[t] || '' }
function typeLabel(t) { return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[t] || t }
function parseOptions(optStr) {
  if (!optStr) return []
  try { return JSON.parse(optStr) } catch { return [] }
}

function isAnswered(eq) {
  if (eq.question_type === 'multiple') return (multiAnswers[eq.question_id] || []).length > 0
  return !!answers[eq.question_id]
}

const answeredCount = computed(() => {
  if (!exam.value) return 0
  return exam.value.exam_questions.filter(eq => isAnswered(eq)).length
})

function scrollTo(idx) {
  const el = document.getElementById(`q-${idx}`)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function confirmSubmit() {
  const unanswered = exam.value.exam_questions.filter(eq => !isAnswered(eq))
  if (unanswered.length > 0) {
    try {
      await ElMessageBox.confirm(
        `还有 ${unanswered.length} 道题未作答（第 ${unanswered.map((_, i) => exam.value.exam_questions.indexOf(unanswered[i]) + 1).join('、')} 题），确认提交？`,
        '有题目未作答',
        { type: 'warning', confirmButtonText: '仍然提交', cancelButtonText: '继续作答' }
      )
    } catch { return }
  } else {
    try {
      await ElMessageBox.confirm('确认提交答卷？提交后无法修改。', '提示', { type: 'warning' })
    } catch { return }
  }
  await doSubmit()
}

async function doSubmit() {
  if (submitting.value || !record.value) return
  submitting.value = true
  try {
    const answerList = exam.value.exam_questions.map(eq => {
      let userAnswer = ''
      if (eq.question_type === 'multiple') {
        userAnswer = (multiAnswers[eq.question_id] || []).sort().join(',')
      } else {
        userAnswer = answers[eq.question_id] || ''
      }
      return { question_id: eq.question_id, user_answer: userAnswer }
    })
    const res = await examApi.submit(examId.value, { record_id: record.value.id, answers: answerList })
    recordResult.value = res
    submitted.value = true
    clearInterval(timer)
    clearDraft()
    ElMessage.success(`提交成功，得分：${res.total_score} 分`)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    exam.value = await examApi.get(examId.value)
    record.value = await examApi.start(examId.value).catch(err => {
      const msg = err?.response?.data?.detail || '无法开始考试'
      ElMessage.error(msg)
      return null
    })

    if (!record.value) return

    if (record.value.status === 'completed') {
      submitted.value = true
      recordResult.value = record.value
      return
    }

    const totalSecs = exam.value.duration_minutes * 60
    const rawStarted = record.value.started_at || ''
    const startedAt = new Date(rawStarted.includes('Z') || rawStarted.includes('+') ? rawStarted : rawStarted + 'Z')
    const elapsed = Math.floor((Date.now() - startedAt.getTime()) / 1000)
    timeLeft.value = Math.max(totalSecs - elapsed, 0)

    if (timeLeft.value <= 0) {
      ElMessage.warning('时间到！自动提交答卷')
      doSubmit()
      return
    }

    restoreDraft()
    watch([answers, multiAnswers], saveDraft, { deep: true })

    if (timeLeft.value <= 300) warnedFiveMin = true

    timer = setInterval(() => {
      timeLeft.value--
      if (!warnedFiveMin && timeLeft.value <= 300) {
        warnedFiveMin = true
        ElNotification({ title: '时间提醒', message: '距离考试结束还有 5 分钟，请尽快完成！', type: 'warning', duration: 8000 })
      }
      if (timeLeft.value <= 0) {
        clearInterval(timer)
        ElMessage.warning('时间到！自动提交答卷')
        doSubmit()
      }
    }, 1000)
  } finally {
    loading.value = false
  }
})

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.exam-page { max-width: 1100px; margin: 0 auto; }
.exam-layout { display: flex; gap: 24px; align-items: flex-start; }
.progress-nav {
  width: 172px; flex-shrink: 0; position: sticky; top: 100px;
  background: var(--app-surface); border-radius: 16px; padding: 20px;
  border: 1px solid var(--app-border);
  max-height: calc(100dvh - 124px); overflow-y: auto;
}
.nav-title { font-weight: bold; font-size: 13px; color: #303133; margin-bottom: 12px; text-align: center; }
.nav-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; margin-bottom: 12px; }
.nav-item {
  width: 28px; height: 32px; border-radius: 6px; border: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; cursor: pointer; font-weight: bold;
}
.nav-item.answered { background: var(--app-accent); color: white; }
.nav-item.unanswered { background: #eeeef2; color: var(--app-text-secondary); }
.nav-item:hover { opacity: 0.8; }
.nav-legend { display: flex; align-items: center; font-size: 11px; color: #909399; margin-bottom: 6px; }
.legend-dot { width: 10px; height: 10px; border-radius: 2px; margin-right: 3px; display: inline-block; }
.legend-dot.answered { background: var(--app-accent); }
.legend-dot.unanswered { background: #DCDFE6; }
.nav-stats { text-align: center; font-size: 13px; color: #409EFF; font-weight: bold; }
.exam-main { flex: 1; min-width: 0; }
.exam-header { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.timer { font-size: 20px; font-weight: bold; color: #409EFF; display: flex; align-items: center; gap: 6px; }
.timer.urgent { color: #F56C6C; animation: pulse 1s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.5} }
.q-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.q-num { font-weight: bold; color: #409EFF; }
.q-score { color: #909399; font-size: 13px; }
.q-title { font-size: 16px; margin-bottom: 12px; line-height: 1.6; }
.options { display: flex !important; flex-direction: column; align-items: flex-start; gap: 10px; width: 100%; }
.option-item { margin: 0 !important; margin-left: 0 !important; }
.options :deep(.el-radio), .options :deep(.el-checkbox) { width: 100%; height: auto; min-height: 44px; padding: 12px; border: 1px solid var(--app-border); border-radius: 9px; margin: 0; }
.options :deep(.is-checked) { border-color: var(--app-accent); background: var(--app-accent-soft); }
.options :deep(.el-radio__label), .options :deep(.el-checkbox__label) { white-space: normal; overflow-wrap: anywhere; line-height: 1.65; }
.questions-area .el-card { scroll-margin-top: 100px; }
@media (max-width: 767px) {
  .exam-layout { flex-direction: column; gap: 20px; }
  .progress-nav { position: static; width: 100%; max-height: none; }
  .nav-grid { grid-template-columns: repeat(auto-fill, minmax(32px, 1fr)); }
  .nav-item { width: 100%; min-height: 36px; }
  .exam-main { width: 100%; }
  .timer { font-size: 16px; }
}
</style>
