<template>
  <div v-loading="loading" class="stats-view">
    <el-card v-if="!store.isStudent" header="各分类题目分布" style="margin-bottom: 20px">
      <el-table :data="catStats.filter(r => r.question_count > 0)" border stripe size="small">
        <el-table-column type="index" width="50" />
        <el-table-column prop="category_name" label="分类名称" />
        <el-table-column prop="question_count" label="题目数量" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round(row.question_count / maxCat * 100)"
              :format="() => `${row.question_count} 题`"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card header="选择考试查看统计">
      <el-select
        v-model="selectedExam"
        placeholder="选择考试"
        style="width: 320px; margin-bottom: 16px"
        @change="loadExamStat"
      >
        <el-option v-for="exam in exams" :key="exam.id" :label="exam.title" :value="exam.id" />
      </el-select>

      <!-- Student: score not public notice -->
      <el-alert
        v-if="store.isStudent && selectedExam && scoreNotPublic"
        title="该考试成绩统计暂未公开"
        description="老师尚未开放该考试的成绩统计，请等待老师公开后再查看。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <template v-if="examStat">
        <el-row :gutter="16" style="margin-bottom: 20px">
          <el-col :span="4" v-for="card in statCards" :key="card.label">
            <el-card shadow="hover">
              <div class="metric-card">
                <div class="metric-value">{{ card.value }}</div>
                <div class="metric-label">{{ card.label }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <div class="section-title">分数段分布</div>
        <div v-for="item in examStat.score_distribution" :key="item.range" class="distribution-row">
          <span class="range-label">{{ item.range }}</span>
          <el-progress
            style="flex: 1"
            :percentage="Math.round(item.count / Math.max(examStat.total_participants, 1) * 100)"
            :format="() => `${item.count} 人`"
            :stroke-width="12"
          />
        </div>

        <!-- Per-exam category distribution (all roles) -->
        <template v-if="examCatDist.length">
          <div class="section-title" style="margin-top: 24px">该试卷题目分类分布</div>
          <el-table :data="examCatDist" border stripe size="small" style="margin-bottom: 16px">
            <el-table-column type="index" width="50" />
            <el-table-column prop="category_name" label="分类名称" />
            <el-table-column prop="question_count" label="题目数量" width="180">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round(row.question_count / maxExamCat * 100)"
                  :format="() => `${row.question_count} 题`"
                  :stroke-width="10"
                />
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-if="store.canManage && insights">
          <div class="section-title" style="margin-top: 24px">薄弱知识点</div>
          <div v-if="insights.weak_points?.length">
            <div
              v-for="item in insights.weak_points.slice(0, 5)"
              :key="`${item.point_type}-${item.name}`"
              class="distribution-row"
            >
              <span class="range-label weak-label">
                <span>{{ item.name }}</span>
                <el-tooltip
                  effect="dark"
                  placement="top"
                  :content="`当前平均得分率 ${Math.round(item.avg_score_rate * 100)}%，数值越低说明该知识点越薄弱。`"
                >
                  <el-icon class="weak-tip"><WarningFilled /></el-icon>
                </el-tooltip>
              </span>
              <el-progress
                style="flex: 1"
                :percentage="Math.round(item.avg_score_rate * 100)"
                :format="() => `${Math.round(item.avg_score_rate * 100)}%`"
                :stroke-width="12"
                color="#f59e0b"
              />
            </div>
          </div>
        </template>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

import { examApi, statsApi } from '@/api'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const loading = ref(true)
const catStats = ref([])
const exams = ref([])
const selectedExam = ref(null)
const examStat = ref(null)
const insights = ref(null)
const examCatDist = ref([])
const scoreNotPublic = ref(false)

const maxCat = computed(() => Math.max(...catStats.value.map((item) => item.question_count), 1))
const maxExamCat = computed(() => Math.max(...examCatDist.value.map((item) => item.question_count), 1))

const statCards = computed(() => [
  { label: '参与人数', value: examStat.value?.total_participants ?? '-' },
  { label: '平均分', value: examStat.value?.avg_score ?? '-' },
  { label: '及格率', value: examStat.value ? `${Math.round(examStat.value.pass_rate * 100)}%` : '-' },
  { label: '良好率', value: examStat.value ? `${Math.round(examStat.value.good_rate * 100)}%` : '-' },
  { label: '优秀率', value: examStat.value ? `${Math.round(examStat.value.excellent_rate * 100)}%` : '-' },
  { label: '最高分', value: examStat.value?.max_score ?? '-' },
])

async function loadExamStat(examId) {
  if (!examId) return
  scoreNotPublic.value = false
  examStat.value = null
  examCatDist.value = []
  insights.value = null

  const [stat, catDist] = await Promise.all([
    statsApi.exam(examId).catch((err) => {
      if (err?.response?.status === 403) scoreNotPublic.value = true
      return null
    }),
    statsApi.examCategoryDist(examId).catch(() => []),
  ])
  examStat.value = stat
  examCatDist.value = catDist
  if (stat && store.canManage) {
    insights.value = await statsApi.insights(examId).catch(() => null)
  }
}

onMounted(async () => {
  try {
    const [categories, examList] = await Promise.all([
      store.isStudent ? Promise.resolve([]) : statsApi.categories().catch(() => []),
      examApi.list().catch(() => []),
    ])
    catStats.value = categories
    exams.value = examList
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stats-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-view :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
}

.stats-view :deep(.el-select) {
  margin-bottom: 14px;
}

.stats-view :deep(.el-row) {
  margin-bottom: 16px !important;
}

.stats-view :deep(.el-row:last-of-type) {
  margin-bottom: 0 !important;
}

.metric-card {
  text-align: center;
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #2563eb;
  line-height: 1.2;
}

.metric-label {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.section-title {
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 10px;
}

.distribution-row {
  display: grid;
  grid-template-columns: 90px minmax(220px, 420px);
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 12px;
  background: rgba(248, 250, 252, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 10px;
  justify-content: space-between;
}

.distribution-row :deep(.el-progress__text),
.stats-view :deep(.el-progress__text) {
  font-size: 11px !important;
}

.distribution-row :deep(.el-progress) {
  width: 100%;
}

.distribution-row :deep(.el-progress-bar__outer),
.stats-view :deep(.el-progress-bar__outer) {
  border-radius: 999px;
}

.distribution-row:last-child {
  margin-bottom: 0;
}

.range-label {
  min-width: 90px;
  font-size: 13px;
  color: #475569;
  text-align: left;
}

.weak-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.weak-tip {
  color: #f59e0b;
  font-size: 12px;
  cursor: help;
}
</style>
