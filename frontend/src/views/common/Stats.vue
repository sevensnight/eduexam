<template>
  <div class="stats-view">
    <div class="stats-toolbar">
      <div v-if="store.canManage" class="stats-views" role="group" aria-label="统计视图">
        <button type="button" data-view="bank" :aria-pressed="activeView === 'bank'" @click="setView('bank')">题库概览</button>
        <button type="button" data-view="exam" :aria-pressed="activeView === 'exam'" @click="setView('exam')">考试分析</button>
      </div>
      <span v-else class="student-view-label">考试表现概览</span>
      <div class="toolbar-actions">
        <span class="scope-label">{{ activeView === 'bank' ? (store.isTeacher ? '我的题库' : '全部题库') : '已批改成绩' }}</span>
        <span v-if="updatedLabel" class="update-label" aria-live="polite">更新于 {{ updatedLabel }}</span>
        <el-button :icon="Refresh" :loading="refreshing" @click="refreshCurrentView">刷新数据</el-button>
      </div>
    </div>

    <section v-if="activeView === 'bank'" aria-label="题库概览" class="view-content">
      <div v-if="overviewLoading" class="stats-loading" aria-label="正在加载题库统计" role="status">
        <el-skeleton animated><template #template><div class="skeleton-metrics"><div v-for="n in 4" :key="n"><el-skeleton-item variant="text" /><el-skeleton-item variant="h1" /></div></div><div class="skeleton-panels"><el-skeleton-item variant="rect" /><el-skeleton-item variant="rect" /></div></template></el-skeleton>
      </div>
      <div v-else-if="overviewError" class="stats-state error-state" role="alert">
        <el-icon><Warning /></el-icon><h2>题库统计暂时无法加载</h2><p>请检查网络或后端服务，然后重新尝试。</p><el-button @click="loadCategories">重新加载</el-button>
      </div>
      <template v-else>
        <div class="metric-strip overview-metrics" aria-label="题库关键指标">
          <div v-for="metric in overviewMetrics" :key="metric.label" class="metric-item">
            <div class="metric-top"><span>{{ metric.label }}</span><el-icon><component :is="metric.icon" /></el-icon></div>
            <div class="metric-number">{{ metric.value }}<span v-if="metric.unit">{{ metric.unit }}</span></div>
            <p>{{ metric.note }}</p>
          </div>
        </div>
        <el-alert v-if="examListError" type="warning" :closable="false" show-icon title="考试数量暂时无法加载"><template #default><el-button link type="primary" @click="loadExamPage()">重新加载考试列表</el-button></template></el-alert>
        <CategoryDistribution :items="catStats" />
        <p class="scope-note"><el-icon><InfoFilled /></el-icon>{{ store.isTeacher ? '统计范围为你创建的已分类题目，不包含未分类题目。' : '按题目所属分类直接计数，不包含未分类题目；父分类不重复累计子分类题量。' }}</p>
      </template>
    </section>

    <section v-else class="view-content" aria-label="考试分析">
      <div class="exam-selector-panel">
        <div class="exam-selection">
          <label for="stats-exam-select">分析对象</label>
          <el-select id="stats-exam-select" v-model="selectedExam" placeholder="选择一场考试" filterable :loading="examListLoading" :disabled="examListLoading || examListError || !exams.length" @change="loadExamStat">
            <el-option v-for="exam in exams" :key="exam.id" :label="exam.title" :value="exam.id" />
          </el-select>
        </div>
        <div v-if="selectedExamInfo" class="exam-context">
          <el-tag size="small" :type="selectedExamInfo.status === 'closed' ? 'info' : selectedExamInfo.status === 'published' ? 'success' : 'warning'">{{ examStatusLabel }}</el-tag>
          <span v-if="selectedExamInfo.course_name">{{ selectedExamInfo.course_name }}</span>
          <span v-if="selectedExamInfo.total_score != null">满分 {{ formatNumber(selectedExamInfo.total_score) }} 分</span>
        </div>
        <router-link v-if="store.canManage && selectedExam" :to="`/exams/${selectedExam}/stats`" class="report-link">详细报告 <el-icon><ArrowRight /></el-icon></router-link>
        <div v-if="examTotal > examPageSize" class="exam-pages">
          <span>切换列表页可选择更多考试</span>
          <el-pagination :current-page="examPage" :page-size="examPageSize" :total="examTotal" :pager-count="5" layout="prev, pager, next, total" small :disabled="examListLoading" @current-change="changeExamPage" />
        </div>
      </div>

      <div v-if="examListLoading || statLoading" class="stats-loading" aria-label="正在加载考试统计" role="status">
        <el-skeleton animated><template #template><div class="skeleton-metrics"><div v-for="n in 4" :key="n"><el-skeleton-item variant="text" /><el-skeleton-item variant="h1" /></div></div><div class="skeleton-panels"><el-skeleton-item variant="rect" /><el-skeleton-item variant="rect" /></div></template></el-skeleton>
      </div>
      <div v-else-if="examListError" class="stats-state error-state" role="alert"><el-icon><Warning /></el-icon><h2>考试列表暂时无法加载</h2><p>请检查网络或后端服务，然后重新尝试。</p><el-button @click="loadExamPage()">重新加载</el-button></div>
      <div v-else-if="!selectedExam" class="stats-state"><el-icon><DataAnalysis /></el-icon><h2>{{ exams.length ? '选择考试，开始分析' : '暂无可查看的考试' }}</h2><p>{{ store.isStudent ? '有可查看的考试后，你可以在这里了解成绩分布。' : '创建考试后，即可查看试卷结构与成绩表现。' }}</p><router-link to="/exams">前往考试列表 <el-icon><ArrowRight /></el-icon></router-link></div>
      <div v-else-if="scoreNotPublic" class="stats-state"><el-icon><Lock /></el-icon><h2>这场考试的统计尚未开放</h2><p>考试结束且老师公开成绩后，即可查看成绩统计。</p></div>
      <div v-else-if="statsError" class="stats-state error-state" role="alert"><el-icon><Warning /></el-icon><h2>考试统计暂时无法加载</h2><p>{{ statsError }}</p><el-button @click="loadExamStat(selectedExam)">重新加载</el-button></div>
      <template v-else-if="examStat">
        <div class="metric-strip exam-metrics" aria-label="考试关键指标">
          <div v-for="metric in examMetrics" :key="metric.label" class="metric-item">
            <div class="metric-top"><span>{{ metric.label }}</span></div>
            <div class="metric-number">{{ metric.value }}<span v-if="metric.unit">{{ metric.unit }}</span></div>
            <p>{{ metric.note }}</p>
          </div>
        </div>
        <div class="score-grid" :class="{ 'score-grid-student': !store.canManage }">
          <section class="analytics-panel score-panel" aria-label="成绩分布">
            <header class="panel-heading"><div><h2>成绩分布</h2><p>按得分占满分的比例，观察整体表现</p></div><button v-if="hasGradedResults" type="button" class="text-action" :aria-pressed="scoreTableVisible" @click="scoreTableVisible = !scoreTableVisible">{{ scoreTableVisible ? '查看图表' : '查看明细' }}</button></header>
            <template v-if="hasGradedResults">
              <el-table v-if="scoreTableVisible" :data="examStat.score_distribution || []" class="score-table">
                <el-table-column prop="range" label="得分区间" min-width="110" />
                <el-table-column prop="count" label="人数" min-width="70" align="right" />
                <el-table-column label="占比" min-width="80" align="right"><template #default="{ row }">{{ formatPercent(row.count / examStat.total_participants) }}</template></el-table-column>
              </el-table>
              <template v-else><div class="chart-unit">考生人数 / 人</div><AnalyticsChart :option="scoreOption" :height="252" :label="scoreDescription" /></template>
              <p class="panel-note">分组按得分占比划分；是否及格以该考试设置的及格线为准。</p>
            </template>
            <div v-else class="panel-empty"><el-icon><DataAnalysis /></el-icon><h3>等待第一份批改结果</h3><p>成绩完成批改后，这里将展示真实分布。</p></div>
          </section>
          <section v-if="store.canManage" class="analytics-panel knowledge-panel" aria-label="知识点掌握">
            <header class="panel-heading"><div><h2>知识点掌握</h2><p>平均得分率最低的 5 个知识点</p></div><el-tooltip content="平均得分率越低，越值得关注。低于 60% 的知识点以橙色标出。" placement="top"><button type="button" class="help-button" aria-label="知识点得分率说明"><el-icon><InfoFilled /></el-icon></button></el-tooltip></header>
            <el-skeleton v-if="insightsLoading" :rows="6" animated class="stats-loading knowledge-loading" />
            <div v-else-if="insightsError" class="panel-empty" role="alert"><el-icon><Warning /></el-icon><h3>知识点分析暂时无法加载</h3><el-button link type="primary" @click="loadExamStat(selectedExam)">重新加载</el-button></div>
            <ol v-else-if="weakPoints.length" class="knowledge-list">
              <li v-for="(point, index) in weakPoints" :key="`${point.point_type}-${point.name}-${index}`">
                <div class="knowledge-heading"><span class="knowledge-name" :title="point.name"><span>{{ index + 1 }}</span>{{ point.name }}</span><strong :class="{ 'needs-attention': point.avg_score_rate < 0.6 }">{{ formatPercent(point.avg_score_rate) }}</strong></div>
                <div class="knowledge-track" aria-hidden="true"><span :class="{ 'needs-attention-fill': point.avg_score_rate < 0.6 }" :style="{ width: `${Math.min(100, Math.max(0, point.avg_score_rate * 100))}%` }" /></div>
              </li>
            </ol>
            <div v-else class="panel-empty"><el-icon><Reading /></el-icon><h3>暂无知识点分析</h3><p>有已批改的题目作答记录后生成分析。</p></div>
            <p v-if="weakPoints.length && !insightsLoading && !insightsError" class="panel-note">得分率越低，越适合安排针对性复习。</p>
          </section>
        </div>
        <el-alert v-if="examCategoryError" type="warning" :closable="false" show-icon title="试卷分类分布暂时无法加载"><template #default><el-button link type="primary" @click="loadExamStat(selectedExam)">重新加载</el-button></template></el-alert>
        <CategoryDistribution v-else :key="selectedExam" :items="examCatDist" title="试卷题目分类分布" composition-title="试卷分类构成" />
        <p class="scope-note"><el-icon><InfoFilled /></el-icon>成绩按每位学生的最高已批改成绩统计；试卷分类仅统计已设置分类的题目。</p>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowRight, DataAnalysis, Document, FolderOpened, InfoFilled, Lock, Notebook, Reading, Refresh, Warning } from '@element-plus/icons-vue'
import { examApi, statsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import AnalyticsChart from '@/components/analytics/AnalyticsChart.vue'
import CategoryDistribution from '@/components/analytics/CategoryDistribution.vue'
import { createChartTooltip, formatChartTooltip, formatNumber, formatPercent, summarizeCategories } from '@/utils/analytics'

const store = useUserStore()
const activeView = ref(store.isStudent ? 'exam' : 'bank')
const catStats = ref([])
const overviewLoading = ref(!store.isStudent)
const overviewError = ref(false)
const exams = ref([])
const examPage = ref(1)
const examPageSize = 50
const examTotal = ref(0)
const examListLoading = ref(true)
const examListError = ref(false)
const selectedExam = ref(null)
const examStat = ref(null)
const examCatDist = ref([])
const examCategoryError = ref(false)
const insights = ref(null)
const insightsLoading = ref(false)
const insightsError = ref(false)
const statLoading = ref(false)
const statsError = ref('')
const scoreNotPublic = ref(false)
const scoreTableVisible = ref(false)
const updatedAt = ref({ bank: null, exam: null })
let categoryRequestId = 0
let examPageRequestId = 0
let statRequestId = 0

const categorySummary = computed(() => summarizeCategories(catStats.value))
const selectedExamInfo = computed(() => exams.value.find((exam) => exam.id === selectedExam.value))
const examStatusLabel = computed(() => ({ closed: '已结束', published: '已发布', draft: '草稿' }[selectedExamInfo.value?.status] || '考试'))
const hasGradedResults = computed(() => Number(examStat.value?.total_participants) > 0)
const weakPoints = computed(() => [...(insights.value?.weak_points || [])].sort((a, b) => a.avg_score_rate - b.avg_score_rate).slice(0, 5))
const refreshing = computed(() => activeView.value === 'bank' ? overviewLoading.value || examListLoading.value : examListLoading.value || statLoading.value || insightsLoading.value)
const updatedLabel = computed(() => {
  const updated = updatedAt.value[activeView.value]
  return updated ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(updated) : ''
})
const overviewMetrics = computed(() => [
  { label: '已分类题目', value: formatNumber(categorySummary.value.total), unit: '题', note: store.isTeacher ? '你创建的分类题目' : '题库中已设置分类的题目', icon: Document },
  { label: '有题分类', value: formatNumber(categorySummary.value.rows.length), unit: '个', note: '至少包含一道题的分类', icon: FolderOpened },
  { label: '单类平均题量', value: categorySummary.value.rows.length ? formatNumber(categorySummary.value.average) : '-', unit: '题', note: '已分类题目数 / 有题分类数', icon: DataAnalysis },
  { label: '可见考试', value: examListError.value || examListLoading.value ? '-' : formatNumber(examTotal.value), unit: '场', note: store.isTeacher ? '你创建的全部考试' : '当前账号可查看的考试', icon: Notebook },
])
const examMetrics = computed(() => {
  const stat = examStat.value
  const value = (key) => hasGradedResults.value ? formatNumber(stat?.[key]) : '-'
  const rate = (key) => hasGradedResults.value ? formatNumber(stat[key] * 100) : '-'
  return [
    { label: '统计人数', value: formatNumber(stat?.total_participants), unit: '人', note: '有已批改成绩的学生' },
    { label: '平均分', value: value('avg_score'), unit: '分', note: '每人取最高已批改成绩' },
    { label: '及格率', value: rate('pass_rate'), unit: hasGradedResults.value ? '%' : '', note: '按本场考试的及格线' },
    { label: '良好率', value: rate('good_rate'), unit: hasGradedResults.value ? '%' : '', note: '得分达到满分的 75%' },
    { label: '优秀率', value: rate('excellent_rate'), unit: hasGradedResults.value ? '%' : '', note: '得分达到满分的 90%' },
    { label: '最高分', value: value('max_score'), unit: '分', note: '本场已批改成绩最高值' },
  ]
})
const scoreDescription = computed(() => `成绩分布，共 ${examStat.value?.total_participants || 0} 人。${(examStat.value?.score_distribution || []).map((item) => `得分占比 ${item.range}：${item.count} 人`).join('；')}。可切换明细查看数据。`)
const scoreOption = computed(() => {
  const distribution = examStat.value?.score_distribution || []
  const maxCount = Math.max(...distribution.map((item) => Number(item.count)), 0)
  return {
    grid: { left: 0, right: 10, top: 28, bottom: 8, containLabel: true },
    tooltip: createChartTooltip({
      trigger: 'axis',
      // The highlight is a background, not an overlay over bars or value labels.
      axisPointer: { type: 'shadow', z: 0, shadowStyle: { color: 'rgba(0, 102, 204, 0.05)' } },
      formatter: (params) => {
        const row = distribution[params[0]?.dataIndex]
        return row ? formatChartTooltip([`得分占比 ${row.range}`, `${row.count} 人   占比 ${formatPercent(row.count / examStat.value.total_participants)}`]) : ''
      },
    }),
    xAxis: { type: 'category', data: distribution.map((item) => item.range), axisLine: { lineStyle: { color: '#e5e5e9' } }, axisTick: { show: false }, axisLabel: { color: '#6e6e73', fontSize: 11, interval: 0, margin: 14 } },
    yAxis: { type: 'value', min: 0, minInterval: 1, splitNumber: 4, axisLabel: { color: '#6e6e73', fontSize: 11 }, splitLine: { lineStyle: { color: '#edf0f4', type: 'dashed' } } },
    series: [{
      type: 'bar', barMaxWidth: 42,
      data: distribution.map((item) => ({ value: item.count, itemStyle: { color: Number(item.count) === maxCount ? '#0066cc' : '#a2bfdf' } })),
      itemStyle: { borderRadius: [5, 5, 0, 0] }, label: { show: true, position: 'top', color: '#454549', fontSize: 12 }, emphasis: { itemStyle: { color: '#0066cc' } },
    }],
  }
})

function setView(view) {
  if (view === activeView.value) return
  activeView.value = view
  if (view === 'exam' && selectedExam.value && !examStat.value && !statLoading.value) loadExamStat(selectedExam.value)
}

async function loadCategories() {
  if (store.isStudent) return
  const requestId = ++categoryRequestId
  overviewLoading.value = true
  overviewError.value = false
  try {
    const response = await statsApi.categories({ suppressError: true })
    if (requestId !== categoryRequestId) return
    catStats.value = response || []
    updatedAt.value.bank = new Date()
  } catch {
    if (requestId === categoryRequestId) overviewError.value = true
  } finally {
    if (requestId === categoryRequestId) overviewLoading.value = false
  }
}

function resetExamData() {
  ++statRequestId
  examStat.value = null
  examCatDist.value = []
  insights.value = null
  statLoading.value = false
  insightsLoading.value = false
  statsError.value = ''
  scoreNotPublic.value = false
  examCategoryError.value = false
  insightsError.value = false
  scoreTableVisible.value = false
  updatedAt.value.exam = null
}

async function loadExamStat(examId) {
  resetExamData()
  if (!examId) return
  const requestId = statRequestId
  statLoading.value = true
  const [statResult, categoryResult] = await Promise.allSettled([
    statsApi.exam(examId, { suppressError: true }),
    statsApi.examCategoryDist(examId, { suppressError: true }),
  ])
  if (requestId !== statRequestId) return
  statLoading.value = false
  if (statResult.status === 'rejected') {
    if (store.isStudent && statResult.reason?.response?.status === 403) scoreNotPublic.value = true
    else statsError.value = statResult.reason?.response?.data?.detail || '请检查网络或后端服务，然后重新尝试。'
    return
  }
  examStat.value = statResult.value
  examCategoryError.value = categoryResult.status === 'rejected'
  examCatDist.value = categoryResult.status === 'fulfilled' ? categoryResult.value || [] : []
  updatedAt.value.exam = new Date()

  if (store.canManage) {
    insightsLoading.value = true
    try {
      const response = await statsApi.insights(examId, { suppressError: true })
      if (requestId === statRequestId) insights.value = response
    } catch {
      if (requestId === statRequestId) insightsError.value = true
    } finally {
      if (requestId === statRequestId) insightsLoading.value = false
    }
  }
}

async function loadExamPage() {
  const requestId = ++examPageRequestId
  const requestedPage = examPage.value
  examListLoading.value = true
  examListError.value = false
  try {
    const response = await examApi.list({ page: requestedPage, size: examPageSize }, { suppressError: true })
    if (requestId !== examPageRequestId || requestedPage !== examPage.value) return
    exams.value = response.items || []
    examTotal.value = response.total || 0
    const current = exams.value.find((exam) => exam.id === selectedExam.value)
    const preferred = exams.value.find((exam) => exam.status === 'closed' && (!store.isStudent || exam.score_public))
      || exams.value.find((exam) => exam.status === 'closed') || exams.value[0]
    const nextId = current?.id ?? preferred?.id ?? null
    if (selectedExam.value !== nextId) resetExamData()
    selectedExam.value = nextId
  } catch {
    if (requestId === examPageRequestId) examListError.value = true
  } finally {
    if (requestId === examPageRequestId) examListLoading.value = false
  }
  if (requestId === examPageRequestId && !examListError.value && activeView.value === 'exam' && selectedExam.value) {
    await loadExamStat(selectedExam.value)
  }
}

async function changeExamPage(nextPage) {
  examPage.value = nextPage
  selectedExam.value = null
  resetExamData()
  await loadExamPage()
}

async function refreshCurrentView() {
  if (refreshing.value) return
  if (activeView.value === 'bank') await Promise.all([loadCategories(), loadExamPage()])
  else if (examListError.value || !selectedExam.value) await loadExamPage()
  else await loadExamStat(selectedExam.value)
}

onMounted(() => Promise.all([loadCategories(), loadExamPage()]))
onBeforeUnmount(() => { ++categoryRequestId; ++examPageRequestId; ++statRequestId })
</script>

<style scoped>
.stats-view { display: flex; flex-direction: column; gap: 24px; padding-top: 2px; }
.stats-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 47px; border-bottom: 1px solid var(--app-border); }
.stats-views { display: flex; align-self: stretch; gap: 28px; }
.stats-views button { position: relative; padding: 10px 0 17px; border: 0; background: none; color: var(--app-text-secondary); font-size: 14px; font-weight: 500; white-space: nowrap; }
.stats-views button[aria-pressed="true"] { color: var(--app-accent); }
.stats-views button[aria-pressed="true"]::after { content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; border-radius: 2px 2px 0 0; background: var(--app-accent); }
.stats-views button:hover { color: var(--app-accent); }
.toolbar-actions { display: flex; align-items: center; gap: 16px; padding-bottom: 12px; }
.toolbar-actions .el-button { font-size: 12px; min-height: 32px; padding: 7px 11px; background: var(--app-surface); }
.scope-label { color: #596477; background: #eaeef4; border: 1px solid #dfe5ed; border-radius: 6px; padding: 2px 8px; font-size: 11px; white-space: nowrap; }
.update-label { color: var(--app-text-secondary); font-size: 11px; white-space: nowrap; font-variant-numeric: tabular-nums; }
.student-view-label { font-size: 14px; font-weight: 500; padding-bottom: 12px; }
.view-content { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
.metric-strip { display: grid; background: var(--app-surface); border: 1px solid var(--app-border); border-radius: 16px; padding: 24px 0; }
.overview-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.metric-item { min-width: 0; padding: 0 24px; border-left: 1px solid #eff0f3; }
.metric-item:first-child { border-left: 0; }
.metric-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #626770; font-size: 12px; line-height: 1.6; }
.metric-top .el-icon { color: #8393a7; font-size: 17px; }
.metric-number { display: flex; align-items: baseline; gap: 6px; margin: 10px 0 7px; font-size: clamp(27px, 2.4vw, 34px); line-height: 1.2; letter-spacing: -.04em; font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.overview-metrics .metric-item:first-child .metric-number { color: var(--app-accent); }
.metric-number > span { font-size: 12px; color: var(--app-text-secondary); letter-spacing: 0; font-weight: 400; }
.metric-item > p { margin: 0; color: var(--app-text-secondary); font-size: 11px; line-height: 1.6; }
.scope-note { display: flex; align-items: flex-start; gap: 7px; margin: -2px 0 0; color: var(--app-text-secondary); font-size: 11px; line-height: 1.8; }
.scope-note > .el-icon { flex-shrink: 0; margin-top: 3px; font-size: 13px; color: #8393a7; }
.exam-selector-panel { display: flex; flex-wrap: wrap; align-items: center; gap: 18px; background: var(--app-surface); border: 1px solid var(--app-border); border-radius: 12px; padding: 18px 20px; }
.exam-selection { display: flex; align-items: center; gap: 16px; }
.exam-selection label { font-size: 12px; color: var(--app-text-secondary); white-space: nowrap; }
.exam-selection .el-select { width: 300px; }
.exam-context { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; min-width: 0; color: var(--app-text-secondary); font-size: 12px; }
.exam-context > span { overflow-wrap: anywhere; }
.report-link { margin-left: auto; white-space: nowrap; display: inline-flex; align-items: center; gap: 5px; font-size: 12px; }
.exam-pages { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; width: 100%; border-top: 1px solid var(--app-border); padding-top: 12px; color: var(--app-text-secondary); font-size: 11px; }
.exam-pages :deep(.el-pagination) { padding: 0; }
.exam-metrics { grid-template-columns: repeat(6, minmax(0, 1fr)); }
.exam-metrics .metric-item { padding: 0 18px; }
.exam-metrics .metric-number { font-size: clamp(25px, 2.2vw, 32px); }
.score-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(290px, 1fr); gap: 20px; }
.score-grid-student { grid-template-columns: minmax(0, 1fr); }
.analytics-panel { padding: 24px; border: 1px solid var(--app-border); border-radius: 16px; background: var(--app-surface); min-width: 0; }
.panel-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 22px; }
.panel-heading h2 { margin: 0 0 5px; font-size: 16px; line-height: 1.5; letter-spacing: -.02em; }
.panel-heading p { margin: 0; color: var(--app-text-secondary); font-size: 12px; line-height: 1.7; }
.text-action { background: transparent; color: var(--app-accent); padding: 3px 0; border: 0; font-size: 12px; white-space: nowrap; }
.text-action:hover { text-decoration: underline; }
.chart-unit { color: var(--app-text-secondary); font-size: 11px; }
.panel-note { border-top: 1px solid #eff0f3; padding-top: 14px; margin: 16px 0 0; color: var(--app-text-secondary); font-size: 11px; line-height: 1.7; }
.score-table { font-size: 12px; font-variant-numeric: tabular-nums; }
.score-table :deep(.el-table__cell) { padding: 10px 0; }
.help-button { display: grid; place-items: center; background: none; border: 0; color: #7a889b; padding: 4px; border-radius: 4px; }
.knowledge-list { list-style: none; padding: 2px 0 0; margin: 0; display: grid; gap: 21px; }
.knowledge-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; font-size: 12px; margin-bottom: 9px; }
.knowledge-name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #454549; }
.knowledge-name > span { display: inline-block; width: 20px; color: #7b8391; font-size: 11px; }
.knowledge-heading strong { font-weight: 600; font-variant-numeric: tabular-nums; flex-shrink: 0; }
.needs-attention { color: #946200; }
.knowledge-track { margin-left: 20px; height: 5px; background: #f1f3f6; border-radius: 3px; overflow: hidden; }
.knowledge-track > span { display: block; height: 100%; border-radius: inherit; background: #8db3df; }
.knowledge-track > span.needs-attention-fill { background: #d4a357; }
.knowledge-loading { padding: 12px 0; }
.stats-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 340px; padding: 40px 24px; border: 1px solid var(--app-border); border-radius: 16px; background: var(--app-surface); text-align: center; }
.stats-state > .el-icon { padding: 14px; width: 60px; height: 60px; background: #f1f5fa; border-radius: 16px; color: #698aac; margin-bottom: 20px; }
.stats-state h2 { font-size: 18px; margin: 0 0 10px; }
.stats-state p { font-size: 13px; color: var(--app-text-secondary); margin: 0 0 20px; line-height: 1.8; }
.stats-state a { font-size: 13px; display: inline-flex; align-items: center; gap: 4px; }
.error-state > .el-icon { background: #fcf6e9; color: #946200; }
.panel-empty { min-height: 265px; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; gap: 10px; }
.panel-empty > .el-icon { font-size: 30px; color: #839bb5; margin-bottom: 6px; }
.panel-empty h3 { font-size: 14px; margin: 0; }
.panel-empty p { font-size: 12px; color: var(--app-text-secondary); margin: 0; }
.skeleton-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 32px; border-radius: 16px; border: 1px solid var(--app-border); background: var(--app-surface); padding: 26px; margin-bottom: 20px; }
.skeleton-metrics > div { display: grid; gap: 14px; }
.skeleton-metrics :deep(.el-skeleton__text) { width: 60%; }
.skeleton-metrics :deep(.el-skeleton__h1) { height: 34px; width: 70%; }
.skeleton-panels { display: grid; grid-template-columns: 1.65fr 1fr; gap: 20px; }
.skeleton-panels > .el-skeleton__item { height: 445px; border-radius: 16px; }
@media (max-width: 1200px) {
  .exam-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); row-gap: 24px; }
  .exam-metrics .metric-item:nth-child(4) { border-left: 0; }
}
@media (max-width: 1100px) {
  .metric-item { padding: 0 18px; }
  .score-grid { grid-template-columns: minmax(0, 1.4fr) minmax(270px, 1fr); gap: 16px; }
  .score-grid-student { grid-template-columns: minmax(0, 1fr); }
  .analytics-panel { padding: 20px; }
  .update-label { display: none; }
  .exam-selection .el-select { width: 260px; }
}
@media (max-width: 960px) {
  .score-grid { grid-template-columns: minmax(0, 1fr); }
  .overview-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: 24px; }
  .overview-metrics .metric-item:nth-child(3) { border-left: 0; }
}
@media (max-width: 767px) {
  .stats-view { gap: 20px; }
  .stats-toolbar { flex-wrap: wrap; gap: 14px; }
  .stats-views { gap: 24px; order: 2; width: 100%; }
  .stats-views button { padding-bottom: 12px; }
  .toolbar-actions { margin-left: auto; padding-bottom: 0; gap: 10px; }
  .student-view-label { padding-bottom: 0; font-size: 13px; }
  .student-view-label + .toolbar-actions { margin-bottom: 10px; }
  .view-content { gap: 16px; }
  .metric-strip { padding: 20px 0; }
  .metric-item { padding: 0 16px; }
  .metric-top { font-size: 11px; }
  .metric-top .el-icon { font-size: 15px; }
  .metric-number { font-size: 28px; }
  .metric-item > p { font-size: 10px; }
  .exam-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .exam-metrics .metric-item { padding: 0 16px; border-left: 1px solid #eff0f3; }
  .exam-metrics .metric-item:nth-child(odd) { border-left: 0; }
  .exam-metrics .metric-number { font-size: 28px; }
  .exam-selector-panel { padding: 16px; gap: 14px; }
  .exam-selection { flex-direction: column; align-items: stretch; gap: 8px; width: 100%; }
  .exam-selection .el-select { width: 100%; }
  .exam-context { font-size: 11px; gap: 8px; }
  .analytics-panel { padding: 18px 16px; }
  .panel-heading h2 { font-size: 15px; }
  .panel-heading p { font-size: 11px; }
  .scope-note { font-size: 10px; }
  .skeleton-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .skeleton-panels { grid-template-columns: minmax(0, 1fr); }
}
</style>
