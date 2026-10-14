<template>
  <div class="exam-page workflow-page" v-loading="loading">
    <div v-if="exam && record && !submitted" class="exam-layout">
      <!-- 左侧进度导航 -->
      <div class="progress-nav">
        <div class="nav-title">答题进度<span>{{ answeredCount }} / {{ exam.exam_questions.length }}</span></div>
        <div class="timer" :class="{ urgent: timeLeft < 300 }"><span>剩余时间</span><strong>{{ formatTime(timeLeft) }}</strong></div>
        <button type="button" class="mobile-nav-toggle" :aria-expanded="navExpanded" aria-controls="answer-navigation" @click="navExpanded = !navExpanded">{{ navExpanded ? '收起答题卡' : '展开答题卡' }}</button>
        <div id="answer-navigation" class="nav-grid" :class="{ 'is-open': navExpanded }">
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
        <div class="nav-legend" :class="{ 'is-open': navExpanded }">
          <span class="legend-dot answered"></span>已答
          <span class="legend-dot unanswered" style="margin-left:8px"></span>未答
        </div>
        <p class="nav-save-note">答题内容会自动保存</p>
      </div>

      <!-- 右侧答题区 -->
      <div class="exam-main">
        <el-alert
          v-if="draftConflict"
          type="warning"
          :closable="false"
          show-icon
          title="检测到此答卷已在另一个标签页更新，当前页已暂停自动保存和提交。请只保留一个考试页面，刷新并确认服务器草稿后继续。"
          style="margin-bottom:16px"
        />
        <div class="exam-header">
          <div>
            <h2>{{ exam.title }}</h2>
            <el-text type="info">{{ exam.description }}</el-text>
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

            <el-radio-group v-if="eq.question_type === 'single'" v-model="answers[eq.question_id]" class="options" :disabled="submitting || draftConflict">
              <el-radio v-for="opt in parseOptions(eq.options)" :key="opt" :value="opt.charAt(0)" class="option-item">
                <span class="option-copy">{{ opt }}</span><AnswerStateBadge v-if="answers[eq.question_id] === opt.charAt(0)" />
              </el-radio>
            </el-radio-group>

            <el-checkbox-group v-else-if="eq.question_type === 'multiple'" v-model="multiAnswers[eq.question_id]" class="options" :disabled="submitting || draftConflict">
              <el-checkbox v-for="opt in parseOptions(eq.options)" :key="opt" :value="opt.charAt(0)" class="option-item">
                <span class="option-copy">{{ opt }}</span><AnswerStateBadge v-if="multiAnswers[eq.question_id]?.includes(opt.charAt(0))" />
              </el-checkbox>
            </el-checkbox-group>

            <el-radio-group v-else-if="eq.question_type === 'truefalse'" v-model="answers[eq.question_id]" class="options" :disabled="submitting || draftConflict">
              <el-radio value="True"><span class="option-copy">正确</span><AnswerStateBadge v-if="answers[eq.question_id] === 'True'" /></el-radio>
              <el-radio value="False"><span class="option-copy">错误</span><AnswerStateBadge v-if="answers[eq.question_id] === 'False'" /></el-radio>
            </el-radio-group>

            <el-input v-else v-model="answers[eq.question_id]" type="textarea" :rows="4" :disabled="submitting || draftConflict" placeholder="请输入你的答案" style="margin-top:12px" />
          </el-card>

          <div class="exam-submit-bar"><span>已完成 {{ answeredCount }} / {{ exam.exam_questions.length }} 题<span v-if="answeredCount < exam.exam_questions.length">，还有 {{ exam.exam_questions.length - answeredCount }} 题未答</span></span><el-button type="primary" @click="confirmSubmit" :loading="submitting" :disabled="draftConflict">提交答卷</el-button></div>
        </div>
      </div>
    </div>

    <el-result
      v-else-if="!loading && startError"
      icon="warning"
      title="无法开始考试"
      :sub-title="startError"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/exams')">返回考试列表</el-button>
        <el-button @click="router.push('/my-records')">查看考试记录</el-button>
      </template>
    </el-result>

    <el-result
      v-if="submitted"
      :icon="recordResult?.timed_out ? 'warning' : 'success'"
      :title="recordResult?.timed_out ? '考试已截止' : '答卷已提交'"
      :sub-title="submitResultText"
    >
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
import '@/styles/workflow.css'
import { examApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { calculateDeadlineMs, secondsUntil } from '@/utils/examTime'
import { answerListsMatch, classifyLocalDraft, createDraftSaveQueue } from '@/utils/draftSaveQueue'
import AnswerStateBadge from '@/components/lists/AnswerStateBadge.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const examId = computed(() => route.params.id)
const navExpanded = ref(false)
const loading = ref(true)
const submitting = ref(false)
const submitted = ref(false)
const exam = ref(null)
const record = ref(null)
const recordResult = ref(null)
const startError = ref('')
const draftConflict = ref(false)
const answers = reactive({})
const multiAnswers = reactive({})
const timeLeft = ref(0)
const deadlineMs = ref(Number.NaN)
let timer = null
let serverDraftTimer = null
let stopDraftWatch = null
let draftRetryCount = 0
let warnedFiveMin = false
let autoSubmitNotified = false
let readyForAutoSubmit = false
// Longer than the 2.5 s draft timeout, leaving time for the final POST after
// an in-flight autosave is drained.
const AUTO_SUBMIT_LEAD_SECONDS = 10
const draftSaveQueue = createDraftSaveQueue(async payload => {
  const response = await examApi.saveDraft(examId.value, {
    ...payload,
    // Read the version when the serialized task actually starts. Multiple
    // edits queued in this tab therefore use the version returned by the
    // preceding save, while another tab's stale write receives HTTP 409.
    draft_version: record.value?.draft_version,
  })
  if (record.value && response?.draft_version != null) {
    record.value.draft_version = response.draft_version
    // If the student edited again while this request was in flight, preserve
    // those newer local answers as dirty on top of the returned revision.
    if (!submitted.value && record.value.status === 'in_progress') {
      saveDraft(!answerListsMatch(payload.answers, buildAnswerList()))
    }
  }
  return response
})

const submitResultText = computed(() => {
  if (recordResult.value?.timed_out) return '已超过作答截止时间，系统已按最后保存的答题草稿结算'
  if (!recordResult.value?.result_released) return '提交成功，成绩将在考试结束并完成批改后公布'
  return `得分：${recordResult.value?.total_score ?? '-'} 分`
})

function draftKey() {
  return `exam_draft_${store.user?.id || 'guest'}_${examId.value}_${record.value?.id || 'no-record'}`
}
function saveDraft(dirty = true) {
  sessionStorage.setItem(draftKey(), JSON.stringify({
    record_id: record.value?.id,
    draft_version: record.value?.draft_version,
    dirty,
    answers: { ...answers },
    multiAnswers: { ...multiAnswers },
  }))
}
function clearDraft() { sessionStorage.removeItem(draftKey()) }
function restoreServerDraft() {
  for (const item of record.value?.draft_answers || []) {
    const question = exam.value?.exam_questions?.find(eq => eq.question_id === item.question_id)
    if (!question || !item.user_answer) continue
    if (question.question_type === 'multiple') {
      multiAnswers[item.question_id] = item.user_answer.split(',').filter(Boolean)
    } else {
      answers[item.question_id] = item.user_answer
    }
  }
}
function restoreDraft() {
  try {
    const raw = sessionStorage.getItem(draftKey())
    if (!raw) return false
    const saved = JSON.parse(raw)
    const decision = classifyLocalDraft(saved, record.value?.id, record.value?.draft_version)
    if (decision.discard) {
      clearDraft()
      ElMessage.warning('检测到其他标签页中更新的答卷，已忽略此页面的旧本地草稿')
      return false
    }
    if (!decision.restore) return false
    Object.assign(answers, saved.answers || {})
    Object.assign(multiAnswers, saved.multiAnswers || {})
    return true
  } catch {
    clearDraft()
    return false
  }
}

function buildAnswerList() {
  return exam.value.exam_questions.map(eq => {
    let userAnswer = ''
    if (eq.question_type === 'multiple') {
      userAnswer = [...(multiAnswers[eq.question_id] || [])].sort().join(',')
    } else {
      userAnswer = answers[eq.question_id] || ''
    }
    return { question_id: eq.question_id, user_answer: userAnswer }
  })
}

async function saveServerDraft() {
  if (!record.value || submitted.value || record.value.status !== 'in_progress' || draftSaveQueue.isLocked()) return
  try {
    await draftSaveQueue.enqueue({
      record_id: record.value.id,
      answers: buildAnswerList(),
    })
    draftRetryCount = 0
  } catch (error) {
    if (error?.response?.status === 409) {
      draftConflict.value = true
      draftSaveQueue.lock()
      ElNotification({
        title: '自动保存已暂停',
        message: error?.response?.data?.detail || '答卷已在另一个标签页更新，请刷新后继续',
        type: 'warning',
        duration: 0,
      })
      throw error
    }
    if (!submitted.value && !draftSaveQueue.isLocked() && draftRetryCount < 3) {
      const retryDelay = 1000 * (2 ** draftRetryCount)
      draftRetryCount++
      scheduleServerDraft(retryDelay)
    }
    throw error
  }
}

function scheduleServerDraft(delay) {
  if (draftSaveQueue.isLocked()) return
  clearTimeout(serverDraftTimer)
  serverDraftTimer = setTimeout(() => {
    saveServerDraft().catch(() => {})
  }, delay)
}

function queueServerDraft() {
  if (draftSaveQueue.isLocked()) return
  draftRetryCount = 0
  scheduleServerDraft(800)
}

function persistDraft() {
  saveDraft()
  queueServerDraft()
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
  navExpanded.value = false
  if (el) requestAnimationFrame(() => el.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'start' }))
}

async function confirmSubmit() {
  if (submitting.value || draftConflict.value) return
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
      await ElMessageBox.confirm('确认提交答卷？提交后无法修改。', '提交答卷', { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '继续检查' })
    } catch { return }
  }
  await doSubmit().catch(() => {})
}

async function doSubmit() {
  if (submitting.value || draftConflict.value || !record.value) return
  submitting.value = true
  try {
    clearTimeout(serverDraftTimer)
    // Stop scheduling new drafts, then let a request already in flight finish
    // so this tab submits the revision returned by that request. The backend
    // rejects a different tab's stale revision instead of overwriting it.
    draftSaveQueue.lock()
    await draftSaveQueue.waitForIdle()
    const answerList = buildAnswerList()
    const res = await examApi.submit(examId.value, {
      record_id: record.value.id,
      draft_version: record.value.draft_version,
      answers: answerList,
    })
    recordResult.value = res
    submitted.value = true
    clearInterval(timer)
    clearDraft()
    if (res.timed_out) {
      ElMessage.warning('考试已超时，系统已按最后保存的答题草稿结算')
    } else {
      ElMessage.success(res.result_released ? `提交成功，得分：${res.total_score} 分` : '提交成功，成绩将在考试结束并完成批改后公布')
    }
  } catch (error) {
    if (error?.response?.status === 409) {
      draftConflict.value = true
      ElNotification({
        title: '提交已阻止',
        message: error?.response?.data?.detail || '答卷已在另一个标签页更新，请刷新后确认再提交',
        type: 'warning',
        duration: 0,
      })
    }
    throw error
  } finally {
    submitting.value = false
    if (!submitted.value && !draftConflict.value) {
      draftSaveQueue.unlock()
      queueServerDraft()
    }
  }
}

function triggerAutoSubmit() {
  if (!readyForAutoSubmit || draftConflict.value) return
  if (!autoSubmitNotified) {
    autoSubmitNotified = true
    ElMessage.warning('考试即将截止，正在自动提交答卷')
  }
  if (!submitting.value && !submitted.value) {
    // Keep the countdown alive until submission succeeds. If a transient
    // request fails, the next tick retries while the local draft is retained.
    doSubmit().catch(() => {})
  }
}

function syncCountdown() {
  if (!record.value || submitted.value) return
  timeLeft.value = secondsUntil(deadlineMs.value)
  if (!warnedFiveMin && timeLeft.value <= 300) {
    warnedFiveMin = true
    ElNotification({ title: '时间提醒', message: '距离考试结束还有 5 分钟，请尽快完成！', type: 'warning', duration: 8000 })
  }
  if (timeLeft.value <= AUTO_SUBMIT_LEAD_SECONDS) triggerAutoSubmit()
}

function handleVisibilityChange() {
  if (!document.hidden) syncCountdown()
}

onMounted(async () => {
  try {
    // Starting/resuming is the only endpoint allowed to return question
    // content to a student. Public exam details are metadata-only so that
    // reading the page cannot reveal the paper before the timer starts.
    const startResponse = await examApi.start(examId.value).catch(err => {
      const msg = err?.response?.data?.detail || '无法开始考试'
      startError.value = msg
      return null
    })
    if (!startResponse) return

    const metadata = await examApi.get(examId.value).catch(() => ({}))
    record.value = {
      ...startResponse,
      id: startResponse.record_id ?? startResponse.id,
    }
    exam.value = {
      ...metadata,
      title: metadata.title ?? startResponse.title ?? '',
      description: metadata.description ?? startResponse.description ?? '',
      close_time: metadata.close_time ?? startResponse.close_time ?? null,
      duration_minutes: startResponse.duration_minutes
        ?? startResponse.time_limit
        ?? metadata.duration_minutes
        ?? metadata.time_limit,
      exam_questions: startResponse.exam_questions
        ?? startResponse.questions
        ?? metadata.exam_questions
        ?? [],
    }

    if (record.value.status !== 'in_progress') {
      recordResult.value = record.value
      submitted.value = true
      clearDraft()
      return
    }

    deadlineMs.value = calculateDeadlineMs({
      startedAt: record.value.started_at,
      durationMinutes: exam.value.duration_minutes,
      closeTime: exam.value.close_time,
    })
    // Restore persisted answers before any near-deadline auto-submit. A refresh
    // during the final seconds must not replace the student's draft with blanks.
    restoreServerDraft()
    const restoredDirtyLocalDraft = restoreDraft()
    stopDraftWatch = watch([answers, multiAnswers], persistDraft, { deep: true })
    // Only an unsynced local snapshot based on this exact server revision
    // needs reconciliation. A stale snapshot is discarded above.
    if (restoredDirtyLocalDraft) queueServerDraft()
    readyForAutoSubmit = true
    syncCountdown()
    timer = setInterval(syncCountdown, 1000)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener('focus', syncCountdown)
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(serverDraftTimer)
  stopDraftWatch?.()
  stopDraftWatch = null
  draftSaveQueue.dispose()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('focus', syncCountdown)
  draftRetryCount = 0
})
</script>


<style scoped>
.exam-page { max-width: 1180px; margin: 0 auto; }
.exam-layout { display: grid; grid-template-columns: 190px minmax(0,1fr); gap: 24px; align-items: start; }
.progress-nav { position: sticky; top: 100px; background: white; border-radius: 14px; padding: 20px; border: 1px solid var(--app-border); max-height: calc(100dvh - 124px); overflow-y: auto; }
.nav-title { display: flex; justify-content: space-between; gap: 12px; font-weight: 500; font-size: 13px; margin-bottom: 20px; }
.nav-title > span { color: var(--app-text-secondary); font-weight: 400; font-variant-numeric: tabular-nums; }
.timer { margin-bottom: 22px; padding-bottom: 20px; border-bottom: 1px solid var(--app-border); display: grid; gap: 5px; }
.timer > span { color: var(--app-text-secondary); font-size: 11px; }
.timer > strong { font-size: 28px; font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: 1px; color: var(--app-accent); }
.timer.urgent > strong { color: var(--el-color-danger); }
.nav-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 7px; margin-bottom: 16px; }
.nav-item { min-width: 0; height: 32px; border-radius: 6px; border: 0; font-size: 12px; cursor: pointer; font-variant-numeric: tabular-nums; }
.nav-item.answered { background: var(--app-accent); color: white; }
.nav-item.unanswered { background: #f0f2f6; color: var(--app-text-secondary); }
.nav-item:hover { box-shadow: inset 0 0 0 1px var(--app-accent); }
.nav-legend { display: flex; align-items: center; font-size: 11px; color: var(--app-text-secondary); }
.legend-dot { width: 9px; height: 9px; border-radius: 2px; margin-right: 5px; display: inline-block; }
.legend-dot.answered { background: var(--app-accent); } .legend-dot.unanswered { background: #e0e5ed; }
.nav-save-note { color: var(--app-text-secondary); font-size: 11px; margin: 16px 0 0; }
.mobile-nav-toggle { display: none; }
.exam-main { min-width: 0; }
.exam-header { margin-bottom: 24px; }
.exam-header h2 { font-size: 21px; line-height: 1.6; overflow-wrap: anywhere; margin: 0 0 8px; }
.exam-header .el-text { font-size: 13px; white-space: pre-wrap; overflow-wrap: anywhere; }
.q-header { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.q-num { font-size: 12px; color: var(--app-text-secondary); }
.q-score { margin-left: auto; color: var(--app-text-secondary); font-size: 12px; }
.q-title { font-size: 15px; margin-bottom: 20px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.options { display: grid !important; gap: 10px; width: 100%; }
.options :deep(.el-radio), .options :deep(.el-checkbox) { width: 100%; height: auto; min-height: 52px; padding: 12px 14px; border: 2px solid #e2e7ee; border-radius: 9px; margin: 0; align-items: flex-start; background: #f8f9fb; }
.options :deep(.el-radio:not(.is-disabled):hover), .options :deep(.el-checkbox:not(.is-disabled):hover) { border-color: #829dbf; }
.options :deep(.el-radio.is-checked), .options :deep(.el-checkbox.is-checked), .options :deep(.is-checked:not(.is-disabled):hover) { border-color: var(--app-selection-border); background: var(--app-selection-bg); }
.options :deep(.el-radio__input), .options :deep(.el-checkbox__input) { margin-top: 5px; }
.options :deep(.el-radio__label), .options :deep(.el-checkbox__label) { display: grid; grid-template-columns: minmax(0,1fr) auto; align-items: start; gap: 10px; width: 100%; white-space: normal; overflow-wrap: anywhere; line-height: 1.8; min-width: 0; color: #25364d; }
.options :deep(.is-checked .el-radio__label), .options :deep(.is-checked .el-checkbox__label) { color: var(--app-selection-ink); font-weight: 600; }
.options :deep(.el-radio:has(input:focus-visible)), .options :deep(.el-checkbox:has(input:focus-visible)) { outline: 3px solid #183e78; outline-offset: 3px; }
.option-copy { min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 540px) { .options :deep(.el-radio__label), .options :deep(.el-checkbox__label) { grid-template-columns: minmax(0,1fr); gap: 7px; } }
.questions-area .el-card { scroll-margin-top: 100px; }
.exam-submit-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; padding: 18px 20px; background: white; border: 1px solid var(--app-border); border-radius: 12px; }
.exam-submit-bar > span { font-size: 12px; color: var(--app-text-secondary); }
@media (max-width: 900px) {
  .exam-layout { grid-template-columns: minmax(0,1fr); gap: 20px; }
  /* Below the app's header, above the answer cards, below dialogs/popovers. */
  .progress-nav { position: sticky; top: 76px; z-index: 3; width: 100%; max-height: 50dvh; padding: 12px 16px; display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 8px 14px; }
  .nav-title { margin: 0; align-items: center; justify-content: flex-start; gap: 12px; }
  .timer { margin: 0; padding: 0; border: 0; grid-column: 2; grid-row: 1 / 3; align-content: center; gap: 0; text-align: right; }
  .timer > strong { font-size: 22px; }
  .mobile-nav-toggle { display: inline-block; justify-self: start; padding: 0; color: var(--app-accent); border: 0; background: transparent; font-size: 12px; }
  .nav-grid, .nav-legend, .nav-save-note { display: none; }
  .nav-grid.is-open { display: grid; grid-column: 1 / -1; grid-template-columns: repeat(auto-fill,minmax(32px,1fr)); margin: 8px 0 0; }
  .nav-legend.is-open { display: flex; grid-column: 1 / -1; }
  .questions-area .el-card { scroll-margin-top: 180px; }
}
@media (max-width: 767px) { .progress-nav { top: 64px; } .exam-header h2 { font-size: 18px; } }
</style>
