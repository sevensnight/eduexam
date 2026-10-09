<template>
  <div class="dashboard-view">
    <section class="dashboard-welcome">
      <div>
        <p class="welcome-date">{{ dateLabel }}</p>
        <h1>{{ displayName }}，欢迎回来。</h1>
        <p class="welcome-description">{{ store.isStudent ? '每一次练习，都在靠近更好的自己。' : '一眼掌握教学进展，开启今天的工作。' }}</p>
      </div>
      <el-button type="primary" :icon="store.canManage ? 'Plus' : 'Notebook'" @click="$router.push(store.canManage ? '/exams/create' : '/exams')">{{ store.canManage ? '创建考试' : '查看考试' }}</el-button>
    </section>
    <el-alert v-if="loadError" type="error" :closable="false" show-icon title="暂时无法加载概览">
      <template #default>请检查网络或后端服务。<el-button link type="primary" @click="loadDashboard">重新加载</el-button></template>
    </el-alert>
    <el-skeleton v-if="loading" :rows="6" animated class="dashboard-skeleton" />
    <template v-else-if="!loadError">
      <section class="metrics" aria-label="数据概览">
        <div v-for="card in cards" :key="card.label" class="metric">
          <div class="metric-top"><span>{{ card.label }}</span><el-icon :size="18"><component :is="card.icon" /></el-icon></div>
          <div class="metric-number">{{ card.value }}<span>{{ card.label.includes('分类') || card.label.includes('标签') ? '个' : card.label.includes('题') ? '题' : '场' }}</span></div>
        </div>
      </section>
      <section class="dashboard-grid">
        <el-card class="recent-card" shadow="never">
          <template #header><div class="section-header"><span>最新考试</span><router-link to="/exams" class="section-link">查看全部 <el-icon><ArrowRight /></el-icon></router-link></div></template>
          <el-table :data="recentExams.slice(0, 6)" empty-text="暂无考试，创建后即可在此查看">
            <el-table-column prop="title" label="考试名称" min-width="190" show-overflow-tooltip />
            <el-table-column prop="course_name" label="课程" min-width="105" show-overflow-tooltip><template #default="{ row }">{{ row.course_name || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="question_count" label="题数" width="60" align="right" />
          </el-table>
        </el-card>
        <el-card class="category-card" shadow="never">
          <template #header>{{ store.isStudent ? '错题分类分布' : '各分类题目分布' }}</template>
          <p class="category-caption">{{ store.isStudent ? '找准重点，针对性练习。' : '知识结构，一目了然。' }}</p>
          <div v-if="catStats.some(s => s.question_count > 0)" class="cat-list">
            <div v-for="s in catStats.filter(s => s.question_count > 0)" :key="s.category_id ?? s.category_name" class="cat-item">
              <div class="cat-label"><span>{{ s.category_name }}</span><strong>{{ s.question_count }}<small> 题</small></strong></div>
              <el-progress :percentage="Math.round(s.question_count / maxCatCount * 100)" :show-text="false" :stroke-width="5" />
            </div>
          </div>
          <el-empty v-else :description="store.isStudent ? '暂无错题，继续保持' : '暂无题目分类数据'" :image-size="80" />
        </el-card>
      </section>
    </template>
    <section class="quick-section">
      <h2>常用入口</h2>
      <div class="quick-links">
        <router-link :to="store.canManage ? '/questions' : '/wrong-book'"><el-icon><Document /></el-icon><div><strong>{{ store.canManage ? '题库管理' : '错题本' }}</strong><span>{{ store.canManage ? '整理题目，构建知识库' : '回顾错题，巩固知识' }}</span></div><el-icon><ArrowRight /></el-icon></router-link>
        <router-link :to="store.canManage ? '/courses' : '/my-records'"><el-icon><Collection /></el-icon><div><strong>{{ store.canManage ? '课程管理' : '我的成绩' }}</strong><span>{{ store.canManage ? '管理课程与选课学生' : '查看每次考试的表现' }}</span></div><el-icon><ArrowRight /></el-icon></router-link>
        <router-link to="/stats"><el-icon><TrendCharts /></el-icon><div><strong>统计分析</strong><span>用数据发现进步空间</span></div><el-icon><ArrowRight /></el-icon></router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { examApi, statsApi, tagApi, studentApi } from '@/api'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const loading = ref(true)
const loadError = ref(false)
const displayName = computed(() => store.user?.real_name || store.user?.username || '你')
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const recentExams = ref([])
const allExamCount = ref(0)
const catStats = ref([])
const totalQuestions = ref(0)
const totalTags = ref(0)
const wrongCount = ref(0)
const myRecordCount = ref(0)

const cards = computed(() => {
  if (store.isStudent) {
    return [
      { label: '参加考试', value: myRecordCount.value, icon: 'Notebook' },
      { label: '错题总数', value: wrongCount.value, icon: 'Warning' },
      { label: '错题分类', value: catStats.value.length, icon: 'FolderOpened' },
      { label: '可参加考试', value: allExamCount.value, icon: 'Document' },
    ]
  }
  return [
    { label: '题目总数', value: totalQuestions.value, icon: 'Document' },
    { label: '考试总数', value: allExamCount.value, icon: 'Notebook' },
    { label: '分类数量', value: catStats.value.length, icon: 'FolderOpened' },
    { label: '标签数量', value: totalTags.value, icon: 'TrendCharts' },
  ]
})

const maxCatCount = computed(() => Math.max(...catStats.value.map(c => c.question_count), 1))

function statusType(s) { return { draft: 'info', published: 'success', closed: 'warning' }[s] || 'info' }
function statusLabel(s) { return { draft: '草稿', published: '进行中', closed: '已关闭' }[s] || s }

async function loadDashboard() {
  loading.value = true
  loadError.value = false
  try {
  if (store.isStudent) {
    const [examsRes, wrongCatRes, recordsRes] = await Promise.all([
      examApi.list(),
      studentApi.wrongBookCategoryStats(),
      studentApi.myRecords(),
    ])
    allExamCount.value = examsRes.length
    recentExams.value = examsRes
    catStats.value = wrongCatRes
    wrongCount.value = wrongCatRes.reduce((sum, item) => sum + (item.question_count || 0), 0)
    myRecordCount.value = recordsRes.length
  } else {
    const [examsRes, statsRes, tagsRes] = await Promise.all([
      examApi.list(),
      statsApi.categories(),
      tagApi.list(),
    ])
    allExamCount.value = examsRes.length
    recentExams.value = examsRes
    catStats.value = statsRes
    totalQuestions.value = statsRes.reduce((sum, item) => sum + (item.question_count || 0), 0)
    totalTags.value = tagsRes.length
  }
  } catch { loadError.value = true }
  finally { loading.value = false }
}
onMounted(loadDashboard)
</script>

<style scoped>
.dashboard-view { display: flex; flex-direction: column; gap: 28px; }
.dashboard-welcome { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 0 0 8px; }
.welcome-date { font-size: 12px; color: var(--app-text-secondary); margin: 0 0 12px; }
.dashboard-welcome h1 { margin: 0 0 10px; }
.welcome-description { color: var(--app-text-secondary); font-size: 14px; margin: 0; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); background: var(--app-surface); border: 1px solid var(--app-border); border-radius: 16px; padding: 24px 0; }
.metric { padding: 0 28px; border-right: 1px solid var(--app-border); }
.metric:last-child { border: 0; }
.metric-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--app-text-secondary); font-size: 12px; }
.metric-top .el-icon { color: #898990; }
.metric-number { font-size: 38px; font-weight: 600; letter-spacing: -1.5px; line-height: 1.3; margin-top: 16px; font-variant-numeric: tabular-nums; }
.metric-number span { font-size: 11px; font-weight: 400; letter-spacing: 0; color: var(--app-text-secondary); margin-left: 8px; }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr); gap: 24px; }
.dashboard-grid > * { min-width: 0; }
.section-link { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 400; }
.category-caption { font-size: 12px; color: var(--app-text-secondary); margin: 0 0 20px; }
.cat-list { max-height: 340px; overflow-y: auto; padding-right: 6px; }
.cat-item { margin-bottom: 20px; }
.cat-label { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: 12px; margin-bottom: 9px; }
.cat-label strong { font-weight: 500; font-variant-numeric: tabular-nums; }
.cat-label small { font-weight: 400; color: var(--app-text-secondary); }
.cat-item :deep(.el-progress-bar__inner) { background: #5686ba; }
.quick-section h2 { font-size: 18px; margin: 2px 0 16px; }
.quick-links { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; }
.quick-links a { display: flex; align-items: center; gap: 14px; padding: 20px 0; color: var(--app-text); border-top: 1px solid var(--app-border); }
.quick-links a:hover { text-decoration: none; color: var(--app-accent); }
.quick-links a > .el-icon:first-child { background: #e9edf4; color: #426b96; padding: 12px; width: 44px; height: 44px; border-radius: 12px; font-size: 20px; flex-shrink: 0; }
.quick-links a > .el-icon:last-child { font-size: 12px; color: var(--app-text-secondary); margin-left: auto; }
.quick-links strong { display: block; font-size: 14px; font-weight: 500; }
.quick-links span { display: block; font-size: 11px; color: var(--app-text-secondary); margin-top: 4px; }
.dashboard-skeleton { padding: 32px; background: white; border-radius: 16px; }
@media (max-width: 1100px) { .dashboard-grid { grid-template-columns: 1fr; } .metric { padding: 0 20px; } .quick-links { gap: 16px; } }
@media (max-width: 767px) {
  .dashboard-view { gap: 24px; }
  .dashboard-welcome { align-items: flex-start; flex-direction: column; gap: 18px; }
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 0; }
  .metric { padding: 20px; border-bottom: 1px solid var(--app-border); }
  .metric:nth-child(2) { border-right: 0; }
  .metric:nth-child(3) { border-bottom: 0; }
  .metric-number { font-size: 32px; margin-top: 12px; }
  .quick-links { grid-template-columns: 1fr; gap: 0; }
}
</style>
