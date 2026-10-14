<template>
  <div class="workflow-page student-records">
    <el-card>
      <template #header><div class="workflow-heading"><div><h2>我的考试记录<span class="workflow-count">{{ records.length }}</span></h2><p class="workflow-caption">查看每次作答的进展与已公布成绩</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新记录</el-button></div></template>
      <div class="workflow-body">
        <div class="workflow-tools"><div class="workflow-field workflow-search"><label for="record-search">搜索考试</label><el-input id="record-search" v-model="query" :prefix-icon="Search" placeholder="考试名称" clearable /></div><div class="workflow-field"><label for="record-state">记录状态</label><el-select id="record-state" v-model="stateFilter" placeholder="全部记录" clearable><el-option label="进行中" value="进行中" /><el-option label="待批改" value="待批改" /><el-option label="未公布" value="未公布" /><el-option label="已出分" value="released" /></el-select></div></div>
        <el-skeleton v-if="loading" :rows="5" animated />
        <el-alert v-else-if="loadError" type="error" :closable="false" title="考试记录加载失败"><el-button link type="primary" @click="load">重新加载</el-button></el-alert>
        <template v-else>
          <ResponsiveTable :rows="pageRows" :columns="columns" label="我的考试记录" :empty-text="query || stateFilter ? '没有匹配的考试记录' : '还没有考试记录，参加考试后会显示在这里'">
            <template #exam="{ row }"><div class="workflow-title">{{ row.exam_title }}</div><p class="workflow-caption">第 {{ row.attempt_count || 1 }} 次作答 · 及格 {{ row.pass_score }} 分</p></template>
            <template #score="{ row }"><span :class="typeof recordState(row).score === 'number' ? 'workflow-score' : 'workflow-secondary'">{{ recordState(row).score }}</span></template>
            <template #state="{ row }"><el-tag :type="recordState(row).tone" size="small">{{ recordState(row).label }}</el-tag></template>
            <template #submitted_at="{ row }"><div class="workflow-stack workflow-secondary workflow-numeric"><span>{{ listDateTime(row.submitted_at, '尚未提交').date }}</span><span>{{ listDateTime(row.submitted_at).time }}</span></div></template>
            <template #actions="{ row }"><el-button link type="primary" @click="openRecord(row)">{{ row.status === 'in_progress' ? '继续考试' : '查看详情' }}</el-button></template>
          </ResponsiveTable>
          <footer class="workflow-pagination"><span class="workflow-secondary">共 {{ filteredRows.length }} 条记录</span><el-pagination v-if="filteredRows.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="filteredRows.length" :pager-count="5" layout="prev, pager, next" small /></footer>
        </template>
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { studentApi } from '@/api'
import { listDateTime } from '@/utils/listPresentation'
import { recordState } from '@/utils/examPresentation'
import { matchesKeyword } from '@/utils/management'
import ResponsiveTable from '@/components/lists/ResponsiveTable.vue'
import '@/styles/workflow.css'
const router = useRouter()
const loading = ref(true), loadError = ref(false), records = ref([])
const query = ref(''), stateFilter = ref(''), page = ref(1), pageSize = 15
let requestId = 0, disposed = false
const columns = [{ key: 'exam', label: '考试信息' }, { key: 'score', label: '考试成绩', width: '110px' }, { key: 'state', label: '结果', width: '90px' }, { key: 'submitted_at', label: '提交时间', width: '145px' }, { key: 'actions', label: '操作', width: '110px' }]
const filteredRows = computed(() => records.value.filter(row => matchesKeyword([row.exam_title], query.value) && (!stateFilter.value || (stateFilter.value === 'released' ? row.result_released && row.essay_graded && row.status !== 'in_progress' : recordState(row).label === stateFilter.value))))
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([query, stateFilter], () => { page.value = 1 })
function openRecord(row) {
  if (row.status === 'in_progress') return router.push('/exams/' + row.exam_id + '/take')
  return router.push({ path: '/exams/' + row.exam_id + '/result', query: { recordId: String(row.id) } })
}
async function load() {
  const version = ++requestId
  loading.value = true; loadError.value = false
  try { const data = await studentApi.myRecords(); if (version === requestId && !disposed) { records.value = data; page.value = 1 } }
  catch { if (version === requestId && !disposed) loadError.value = true }
  finally { if (version === requestId && !disposed) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { disposed = true; ++requestId })
</script>
