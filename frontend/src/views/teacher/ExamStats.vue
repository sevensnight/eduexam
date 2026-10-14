<template>
  <div class="workflow-page exam-analytics">
    <div class="workflow-context"><el-button link :icon="Back" @click="$router.push('/exams')">返回考试列表</el-button><div class="workflow-actions"><el-button :icon="Refresh" :loading="loading" @click="refreshStats">刷新数据</el-button><el-upload :show-file-list="false" accept=".csv,.xlsx" :http-request="uploadGrades" :disabled="importing || loading || loadError"><el-button :icon="Upload" :loading="importing" :disabled="loading || loadError">导入成绩</el-button></el-upload><el-dropdown trigger="click" @command="downloadGrades"><el-button :icon="Download" :loading="exporting" :disabled="loading || loadError">导出成绩<el-icon style="margin-left:7px"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="xlsx">导出 Excel</el-dropdown-item><el-dropdown-item command="csv">导出 CSV</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></div>
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-alert v-else-if="loadError" type="error" :closable="false" title="统计数据加载失败"><el-button link type="primary" @click="refreshStats">重新加载</el-button></el-alert>
    <template v-else-if="exam && summary">
      <div class="workflow-heading exam-analysis-heading"><div><h2>{{ exam.title }}</h2><p class="workflow-caption">满分 {{ exam.total_score }} 分 · 及格 {{ exam.pass_score }} 分 · {{ exam.duration_minutes }} 分钟</p></div><p class="workflow-caption">按每位学生已批改的最佳成绩统计</p></div>
      <section class="workflow-metrics" aria-label="考试指标概览"><div v-for="metric in statCards" :key="metric.label" class="workflow-metric"><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><span>{{ metric.note }}</span></div></section>
      <el-card class="exam-analysis-card">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="总览" name="overview">
            <div v-if="activeTab === 'overview'" class="overview-panels">
              <section class="analysis-panel"><div class="workflow-heading"><div><h2>分数段分布</h2><p class="workflow-caption">按卷面得分率划分，纵轴为人数</p></div></div><AnalyticsChart v-if="summary.total_participants" :option="distributionOption" :height="280" :label="distributionLabel" /><el-empty v-else :image-size="64" description="尚无已批改成绩" /><div class="workflow-secondary analysis-footnote">已批改 {{ summary.total_attempts ?? summary.total_participants }} 份答卷，统计 {{ summary.total_participants }} 位学生</div></section>
              <section class="analysis-panel"><div class="workflow-heading"><div><h2>薄弱知识点</h2><p class="workflow-caption">平均得分率越低，越值得重点复习</p></div><span class="workflow-secondary">最多 8 项</span></div><div v-if="insights?.weak_points?.length" class="weak-points"><div v-for="(item, index) in insights.weak_points" :key="item.point_type + '-' + item.name" class="weak-point"><span class="weak-index">{{ index + 1 }}</span><div class="weak-main"><div class="weak-label"><span>{{ item.name }}</span><strong>{{ percentage(item.avg_score_rate) }}%</strong></div><div class="weak-track" aria-hidden="true"><span :style="{ width: percentage(item.avg_score_rate) + '%' }" /></div></div></div></div><el-empty v-else :image-size="64" description="暂无知识点分析" /></section>
            </div>
          </el-tab-pane>
          <el-tab-pane label="单题分析" name="questions"><div class="analysis-tab-body"><div class="workflow-tools"><div class="workflow-field workflow-search"><label for="analysis-question-query">查找试题</label><el-input id="analysis-question-query" v-model="query" :prefix-icon="Search" placeholder="题干、分类或标签" clearable /></div><span class="workflow-secondary">判断与选择题显示正确率，简答题显示满分率</span></div>
            <ResponsiveTable :rows="pageRows" :columns="questionColumns" row-key="question_id" label="单题分析" :empty-text="query ? '没有匹配的试题' : '暂无单题分析数据'">
              <template #question="{ row }"><div class="workflow-title">{{ row.question_title || '题目 #' + row.question_id }}</div><div class="question-analysis-meta"><span>{{ questionTypeLabel(row.question_type) }}</span><span>{{ row.category_name || '未分类' }}</span><span v-if="row.tag_names?.length">{{ row.tag_names.join(' / ') }}</span></div></template>
              <template #participation="{ row }"><span class="workflow-numeric">{{ row.answer_count }} 人 / {{ row.correct_count }} 人</span></template>
              <template #avg_score="{ row }">{{ formatNumber(row.avg_score) }} 分</template>
              <template #rate="{ row }"><strong class="workflow-numeric">{{ percentage(row.accuracy_rate) }}%</strong><div class="workflow-secondary">{{ row.question_type === 'essay' ? '满分率' : '正确率' }}</div></template>
            </ResponsiveTable>
          </div></el-tab-pane>
          <el-tab-pane label="班级对比" name="classes"><div class="analysis-tab-body"><div class="workflow-tools"><div class="workflow-field workflow-search"><label for="analysis-class-query">查找班级</label><el-input id="analysis-class-query" v-model="query" :prefix-icon="Search" placeholder="班级名称" clearable /></div></div>
            <ResponsiveTable :rows="pageRows" :columns="classColumns" row-key="class_name" label="班级成绩对比" :empty-text="query ? '没有匹配的班级' : '暂无班级成绩'">
              <template #class_name="{ row }"><span class="workflow-title">{{ row.class_name || '未分班' }}</span></template><template #avg_score="{ row }">{{ formatNumber(row.avg_score) }} 分</template><template #highest_score="{ row }">{{ formatNumber(row.highest_score) }} 分</template><template #pass_rate="{ row }">{{ percentage(row.pass_rate) }}%</template>
            </ResponsiveTable>
          </div></el-tab-pane>
          <el-tab-pane label="学生对比" name="students"><div class="analysis-tab-body"><el-alert v-if="insights?.student_comparison_truncated" class="workflow-error" type="info" :closable="false" :title="'共 ' + insights.student_comparison_total + ' 位学生，当前仅展示成绩最高的前 1000 名'" /><div class="workflow-tools"><div class="workflow-field workflow-search"><label for="analysis-student-query">查找学生</label><el-input id="analysis-student-query" v-model="query" :prefix-icon="Search" placeholder="姓名、账号或班级" clearable /></div></div>
            <ResponsiveTable :rows="pageRows" :columns="studentColumns" row-key="record_id" label="学生成绩对比" :empty-text="query ? '没有匹配的学生' : '暂无学生成绩'">
              <template #student="{ row }"><div class="student-analysis-name"><span class="analysis-rank">{{ row.rank }}</span><div><div class="workflow-title">{{ row.real_name || row.username }}</div><div class="workflow-secondary">{{ row.username }} · {{ row.class_name || '未分班' }}</div></div></div></template>
              <template #total_score="{ row }"><strong class="workflow-score">{{ formatNumber(row.total_score) }}</strong><span class="workflow-secondary"> 分</span></template>
              <template #delta_vs_avg="{ row }"><span class="workflow-numeric">{{ row.delta_vs_avg > 0 ? '+' : '' }}{{ formatNumber(row.delta_vs_avg) }} 分</span></template>
              <template #actions="{ row }"><el-button link type="primary" @click="openAnswerSheet(row)">查看答卷</el-button></template>
            </ResponsiveTable>
          </div></el-tab-pane>
        </el-tabs>
        <footer v-if="activeTab !== 'overview'" class="workflow-pagination"><span class="workflow-secondary">共 {{ filteredRows.length }} 条{{ query ? '匹配结果' : '记录' }}</span><el-pagination v-if="filteredRows.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="filteredRows.length" :pager-count="5" layout="prev, pager, next" small /></footer>
      </el-card>
    </template>
    <el-dialog v-model="sheetVisible" title="学生答卷" width="760px" top="5vh" class="answer-sheet-dialog" append-to-body destroy-on-close>
      <p class="sheet-student">{{ sheetStudent }}</p><el-skeleton v-if="sheetLoading" :rows="5" animated /><el-alert v-else-if="sheetError" type="error" :closable="false" title="答卷加载失败"><el-button link type="primary" @click="openAnswerSheet(sheetRow)">重新加载</el-button></el-alert>
      <template v-else-if="sheetRecord"><p class="workflow-secondary">总分 {{ sheetRecord.total_score ?? '未评分' }} 分</p><AnswerReview v-for="(question, index) in sheetRecord.questions || []" :key="question.answer_id || question.question_id" :question="question" :index="index" reveal-answers :pending="question.question_type === 'essay' && !sheetRecord.essay_graded" answer-label="学生作答" /></template><el-empty v-else description="暂无答卷数据" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown, Back, Download, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { examApi, statsApi } from '@/api'
import { createUploadForm, downloadBlob } from '@/utils/download'
import { createChartTooltip, formatChartTooltip, formatNumber } from '@/utils/analytics'
import { questionTypeLabel } from '@/utils/listPresentation'
import { percentage } from '@/utils/examPresentation'
import { matchesKeyword } from '@/utils/management'
import ResponsiveTable from '@/components/lists/ResponsiveTable.vue'
import AnswerReview from '@/components/lists/AnswerReview.vue'
import AnalyticsChart from '@/components/analytics/AnalyticsChart.vue'
import '@/styles/workflow.css'

const route = useRoute()
const examId = computed(() => route.params.id)
const loading = ref(true), loadError = ref(false), activeTab = ref('overview')
const exam = ref(null), summary = ref(null), insights = ref(null)
const importing = ref(false), exporting = ref(false), query = ref(''), page = ref(1)
const pageSize = 20
let requestId = 0, sheetRequestId = 0, disposed = false
const sheetVisible = ref(false), sheetLoading = ref(false), sheetError = ref(false)
const sheetStudent = ref(''), sheetRecord = ref(null), sheetRow = ref(null)
const hasScores = computed(() => !!summary.value?.total_participants)
const statCards = computed(() => [
  { label: '参与人数', value: summary.value?.total_participants ?? 0, note: '位已出分学生' },
  { label: '平均分', value: hasScores.value ? formatNumber(summary.value.avg_score) : '暂无', note: '满分 ' + (exam.value?.total_score ?? 0) + ' 分' },
  { label: '及格率', value: hasScores.value ? percentage(summary.value.pass_rate) + '%' : '暂无', note: '达到 ' + (exam.value?.pass_score ?? 0) + ' 分' },
  { label: '良好率', value: hasScores.value ? percentage(summary.value.good_rate) + '%' : '暂无', note: '达到卷面满分的 75%' },
  { label: '优秀率', value: hasScores.value ? percentage(summary.value.excellent_rate) + '%' : '暂无', note: '达到卷面满分的 90%' },
  { label: '最高分', value: hasScores.value ? formatNumber(summary.value.max_score) : '暂无', note: '最佳成绩' },
])
const questionColumns = [{ key: 'question', label: '试题信息' }, { key: 'participation', label: '作答 / 答对', width: '130px' }, { key: 'avg_score', label: '平均得分', width: '100px' }, { key: 'rate', label: '正确率 / 满分率', width: '120px' }]
const classColumns = [{ key: 'class_name', label: '班级' }, { key: 'participant_count', label: '参与人数', width: '100px' }, { key: 'avg_score', label: '平均分', width: '110px' }, { key: 'highest_score', label: '最高分', width: '110px' }, { key: 'pass_rate', label: '及格率', width: '100px' }]
const studentColumns = [{ key: 'student', label: '排名 / 学生信息' }, { key: 'total_score', label: '考试成绩', width: '130px' }, { key: 'delta_vs_avg', label: '相对平均分', width: '130px' }, { key: 'actions', label: '操作', width: '110px' }]
const filteredRows = computed(() => {
  const data = { questions: insights.value?.question_accuracy, classes: insights.value?.class_comparison, students: insights.value?.student_comparison }[activeTab.value] || []
  return data.filter(row => matchesKeyword([row.question_title, row.category_name, ...(row.tag_names || []), row.class_name, row.real_name, row.username], query.value))
})
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(activeTab, () => { query.value = ''; page.value = 1 })
watch(query, () => { page.value = 1 })
const distributionLabel = computed(() => '分数段人数分布。' + (summary.value?.score_distribution || []).map(item => item.range + '：' + item.count + ' 人').join('；'))
const distributionOption = computed(() => ({
  grid: { left: 8, right: 12, top: 30, bottom: 10, containLabel: true },
  tooltip: createChartTooltip({ trigger: 'axis', axisPointer: { type: 'shadow', z: 0, shadowStyle: { color: 'rgba(0,102,204,0.04)' } }, formatter: params => { const row = summary.value?.score_distribution?.[params[0]?.dataIndex]; return row ? formatChartTooltip([row.range, row.count + ' 人']) : '' } }),
  xAxis: { type: 'category', data: (summary.value?.score_distribution || []).map(row => row.range), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#6e6e73', fontSize: 11, interval: 0, overflow: 'break', width: 52 } },
  yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#6e6e73', fontSize: 11 }, splitLine: { lineStyle: { color: '#edf0f4', type: 'dashed' } } },
  series: [{ type: 'bar', barMaxWidth: 40, data: (summary.value?.score_distribution || []).map(row => row.count), itemStyle: { color: '#719fd2', borderRadius: [4,4,0,0] }, emphasis: { itemStyle: { color: '#0066cc' } }, label: { show: true, position: 'top', color: '#454549', fontSize: 12 } }],
}))
async function refreshStats() {
  const version = ++requestId
  loading.value = true
  loadError.value = false
  try {
    const [examInfo, statInfo, insightInfo] = await Promise.all([examApi.get(examId.value), statsApi.exam(examId.value), statsApi.insights(examId.value)])
    if (version !== requestId || disposed) return
    exam.value = examInfo
    summary.value = statInfo
    insights.value = insightInfo
    page.value = 1
  } catch { if (version === requestId && !disposed) loadError.value = true }
  finally { if (version === requestId && !disposed) loading.value = false }
}
async function openAnswerSheet(row) {
  if (!row) return
  const version = ++sheetRequestId
  sheetRow.value = row
  sheetStudent.value = row.real_name || row.username
  sheetRecord.value = null
  sheetError.value = false
  sheetVisible.value = true
  sheetLoading.value = true
  try {
    const detail = await examApi.record(examId.value, row.record_id)
    if (version === sheetRequestId && sheetVisible.value && !disposed) sheetRecord.value = detail
  } catch { if (version === sheetRequestId && !disposed) sheetError.value = true }
  finally { if (version === sheetRequestId && !disposed) sheetLoading.value = false }
}
watch(sheetVisible, value => { if (!value) ++sheetRequestId })
async function downloadGrades(format) {
  if (exporting.value || loading.value || loadError.value || !['xlsx','csv'].includes(format)) return
  exporting.value = true
  const id = examId.value
  try { const blob = await examApi.exportGrades(id, { format }); if (!disposed) downloadBlob(blob, 'grades_exam_' + id + '.' + format) }
  catch { /* The API interceptor reports export errors. */ }
  finally { exporting.value = false }
}
async function uploadGrades(options) {
  if (importing.value) throw new Error('正在导入，请稍后')
  importing.value = true
  try {
    const result = await examApi.importGrades(examId.value, createUploadForm(options.file))
    if (disposed) return result
    const firstError = result.errors?.[0]
    if (firstError) ElMessage.warning('已新增 ' + result.created + ' 条，更新 ' + result.updated + ' 条；第 ' + firstError.row + ' 行失败：' + firstError.message)
    else ElMessage.success('导入完成：新增 ' + result.created + ' 条，更新 ' + result.updated + ' 条')
    await refreshStats()
    return result
  } finally { importing.value = false }
}
watch(examId, () => { activeTab.value = 'overview'; sheetVisible.value = false; refreshStats() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; ++requestId; ++sheetRequestId })
</script>

<style scoped>
.exam-analysis-heading { padding: 2px 0 4px; }
.exam-analysis-heading h2 { font-size: 20px; }
.overview-panels { display: grid; grid-template-columns: minmax(0,1.2fr) minmax(0,1fr); gap: 30px; padding: 18px 0 4px; }
.analysis-panel { min-width: 0; } .analysis-panel + .analysis-panel { padding-left: 28px; border-left: 1px solid var(--app-border); }
.analysis-footnote { border-top: 1px solid var(--app-border); padding-top: 14px; }
.weak-points { display: grid; gap: 20px; margin-top: 24px; }
.weak-point { display: flex; align-items: center; gap: 14px; }
.weak-index { width: 16px; font-size: 12px; color: #85858c; }
.weak-main { flex: 1; min-width: 0; }
.weak-label { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; font-size: 13px; margin-bottom: 8px; }
.weak-label > span { overflow-wrap: anywhere; } .weak-label strong { font-weight: 500; font-variant-numeric: tabular-nums; white-space: nowrap; }
.weak-track { height: 5px; background: #f0f2f6; border-radius: 3px; overflow: hidden; } .weak-track > span { display: block; height: 100%; background: #8ba8c9; border-radius: 3px; }
.analysis-tab-body { padding-top: 14px; }
.question-analysis-meta { display: flex; flex-wrap: wrap; gap: 5px 12px; color: var(--app-text-secondary); font-size: 12px; margin-top: 7px; }
.student-analysis-name { display: flex; align-items: center; gap: 16px; }
.analysis-rank { min-width: 20px; font-variant-numeric: tabular-nums; font-size: 13px; color: var(--app-text-secondary); }
.sheet-student { font-size: 14px; font-weight: 500; overflow-wrap: anywhere; margin: 0 0 16px; }
@media (max-width: 1100px) { .overview-panels { grid-template-columns: minmax(0,1fr); gap: 24px; } .analysis-panel + .analysis-panel { border-left: 0; border-top: 1px solid var(--app-border); padding: 24px 0 0; } }
@media (max-width: 540px) { .exam-analysis-heading h2 { font-size: 17px; } .workflow-metric { min-height: 120px; } }
</style>
