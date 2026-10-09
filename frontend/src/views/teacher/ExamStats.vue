<template>
  <el-card :header="`考试统计 - ${exam?.title || ''}`" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>考试统计 - {{ exam?.title || '' }}</span>
        <div class="header-actions">
          <el-upload
            :show-file-list="false"
            accept=".csv,.xlsx"
            :http-request="uploadGrades"
          >
            <el-button :icon="Upload">导入成绩</el-button>
          </el-upload>
          <el-button :icon="Download" @click="downloadGrades('xlsx')">导出 Excel</el-button>
          <el-button :icon="Download" @click="downloadGrades('csv')">导出 CSV</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="总览" name="overview">
        <template v-if="summary">
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

          <el-row :gutter="20">
            <el-col :span="12">
              <el-card shadow="never" header="分数段分布">
                <div
                  v-for="item in summary.score_distribution"
                  :key="item.range"
                  class="distribution-row"
                >
                  <span class="range-label">{{ item.range }}</span>
                  <el-progress
                    style="flex: 1"
                    :percentage="Math.round(item.count / Math.max(summary.total_participants, 1) * 100)"
                    :format="() => `${item.count} 人`"
                    :stroke-width="12"
                  />
                </div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" header="薄弱知识点 Top 8">
                <div v-if="insights?.weak_points?.length">
                  <div v-for="item in insights.weak_points" :key="`${item.point_type}-${item.name}`" class="distribution-row">
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
                <el-empty v-else description="暂无薄弱点数据" />
              </el-card>
            </el-col>
          </el-row>
        </template>
      </el-tab-pane>

      <el-tab-pane label="单题分析" name="questions">
        <el-table :data="insights?.question_accuracy || []" border stripe>
          <el-table-column prop="question_title" label="题目" min-width="260" show-overflow-tooltip />
          <el-table-column prop="category_name" label="分类" min-width="120" show-overflow-tooltip />
          <el-table-column label="题型" width="90">
            <template #default="{ row }">{{ typeLabel(row.question_type) }}</template>
          </el-table-column>
          <el-table-column prop="answer_count" label="作答人数" width="90" />
          <el-table-column prop="correct_count" label="答对人数" width="90" />
          <el-table-column prop="avg_score" label="平均得分" width="100" />
          <el-table-column label="正确率/得分率" width="170">
            <template #default="{ row }">
              <el-progress :percentage="Math.round(row.accuracy_rate * 100)" :stroke-width="10" />
            </template>
          </el-table-column>
          <el-table-column label="标签" min-width="160">
            <template #default="{ row }">{{ row.tag_names?.join(' / ') || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="班级对比" name="classes">
        <el-table :data="insights?.class_comparison || []" border stripe>
          <el-table-column prop="class_name" label="班级" min-width="160" />
          <el-table-column prop="participant_count" label="人数" width="90" />
          <el-table-column prop="avg_score" label="平均分" width="100" />
          <el-table-column prop="highest_score" label="最高分" width="100" />
          <el-table-column label="及格率" width="180">
            <template #default="{ row }">
              <el-progress :percentage="Math.round(row.pass_rate * 100)" :stroke-width="10" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="学生对比" name="students">
        <el-table :data="insights?.student_comparison || []" border stripe>
          <el-table-column prop="rank" label="排名" width="80" />
          <el-table-column prop="username" label="用户名" width="130" />
          <el-table-column prop="real_name" label="姓名" width="120" />
          <el-table-column prop="class_name" label="班级" min-width="140" />
          <el-table-column prop="total_score" label="成绩" width="90" />
          <el-table-column prop="delta_vs_avg" label="相对平均分" width="120" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openAnswerSheet(row)">查看答卷</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

    </el-tabs>
  </el-card>

  <!-- 答卷详情对话框 -->
  <el-dialog
    v-model="sheetVisible"
    :title="`答卷详情 — ${sheetStudent}`"
    width="760px"
    destroy-on-close
    top="5vh"
  >
    <div v-if="sheetLoading" v-loading="true" style="height:200px" />
    <template v-else-if="sheetRecord">
      <div style="margin-bottom:12px;color:#606266;font-size:13px">
        总分：<strong>{{ sheetRecord.total_score ?? '-' }}</strong>
      </div>
      <div
        v-for="(answer, idx) in sheetRecord.answers"
        :key="answer.id"
        style="margin-bottom:16px;padding:12px;background:#fafafa;border-radius:6px;border:1px solid #e4e7ed"
      >
        <div style="font-weight:bold;margin-bottom:6px;font-size:14px">
          {{ idx + 1 }}. {{ answer.question_title || `题目#${answer.question_id}` }}
          <el-tag size="small" style="margin-left:6px">{{ typeLabel(answer.question_type) }}</el-tag>
        </div>
        <div style="margin-bottom:4px">
          <span style="color:#909399;font-size:13px">学生作答：</span>
          <span>{{ answer.user_answer || '（未作答）' }}</span>
        </div>
        <div style="margin-bottom:4px">
          <span style="color:#909399;font-size:13px">参考答案：</span>
          <span style="color:#67c23a">{{ answer.correct_answer || '-' }}</span>
        </div>
        <div>
          <span style="color:#909399;font-size:13px">得分：</span>
          <el-tag
            size="small"
            :type="answer.is_correct === 'Y' ? 'success' : answer.is_correct === 'P' ? 'warning' : answer.is_correct === 'N' ? 'danger' : 'info'"
          >
            {{ answer.score_got ?? '-' }} / {{ answer.max_score ?? '-' }}
          </el-tag>
        </div>
      </div>
    </template>
    <el-empty v-else description="暂无答卷数据" />
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Download, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { examApi, statsApi } from '@/api'
import { createUploadForm, downloadBlob } from '@/utils/download'

const route = useRoute()
const examId = route.params.id
const loading = ref(true)
const activeTab = ref('overview')
const exam = ref(null)
const summary = ref(null)
const insights = ref(null)

const statCards = computed(() => [
  { label: '参与人数', value: summary.value?.total_participants ?? '-' },
  { label: '平均分', value: summary.value?.avg_score ?? '-' },
  { label: '及格率', value: summary.value ? `${Math.round(summary.value.pass_rate * 100)}%` : '-' },
  { label: '良好率', value: summary.value ? `${Math.round(summary.value.good_rate * 100)}%` : '-' },
  { label: '优秀率', value: summary.value ? `${Math.round(summary.value.excellent_rate * 100)}%` : '-' },
  { label: '最高分', value: summary.value?.max_score ?? '-' },
])

function typeLabel(type) {
  return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[type] || type
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

const sheetVisible = ref(false)
const sheetLoading = ref(false)
const sheetStudent = ref('')
const sheetRecord = ref(null)
let cachedRecords = null

async function openAnswerSheet(row) {
  sheetStudent.value = row.real_name || row.username
  sheetRecord.value = null
  sheetVisible.value = true
  sheetLoading.value = true
  try {
    if (!cachedRecords) {
      cachedRecords = await examApi.records(examId)
    }
    sheetRecord.value = cachedRecords.find((r) => r.student_id === row.student_id) ?? null
  } finally {
    sheetLoading.value = false
  }
}
async function loadStats() {
  const [examInfo, statInfo, insightInfo] = await Promise.all([
    examApi.get(examId).catch(() => null),
    statsApi.exam(examId).catch(() => null),
    statsApi.insights(examId).catch(() => null),
  ])
  exam.value = examInfo
  summary.value = statInfo
  insights.value = insightInfo
}

async function downloadGrades(format) {
  const blob = await examApi.exportGrades(examId, { format })
  downloadBlob(blob, `grades_exam_${examId}.${format}`)
}

async function uploadGrades(options) {
  try {
    const result = await examApi.importGrades(examId, createUploadForm(options.file))
    options.onSuccess?.(result)
    const firstError = result.errors?.[0]
    if (firstError) {
      ElMessage.warning(`已新增 ${result.created} 条，更新 ${result.updated} 条；第 ${firstError.row} 行失败：${firstError.message}`)
    } else {
      ElMessage.success(`导入完成：新增 ${result.created} 条，更新 ${result.updated} 条`)
    }
    await loadStats()
  } catch (error) {
    options.onError?.(error)
    throw error
  }
}

onMounted(async () => {
  try {
    await loadStats()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.metric-card {
  text-align: center;
}

.metric-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}

.metric-label {
  color: #909399;
  font-size: 13px;
}

.distribution-row {
  display: grid;
  grid-template-columns: 110px minmax(220px, 420px);
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  justify-content: space-between;
}

.distribution-row :deep(.el-progress__text),
:deep(.el-progress__text) {
  font-size: 11px !important;
}

.distribution-row :deep(.el-progress) {
  width: 100%;
}

.range-label {
  min-width: 110px;
  font-size: 13px;
  color: #606266;
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
