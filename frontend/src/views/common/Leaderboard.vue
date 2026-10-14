<template>
  <div class="workflow-page">
    <div class="workflow-context"><el-button text :icon="ArrowLeft" @click="$router.push('/exams')">返回考试列表</el-button><el-button text :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div>
    <el-card>
      <template #header><div class="workflow-heading"><div><h2>{{ exam?.title || '考试排行榜' }}</h2><p class="workflow-caption">按学生最佳成绩排名 · 同分时按提交时间排序</p></div><span class="workflow-count">{{ leaders.length }} 位学生</span></div></template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-result v-else-if="notPublic" icon="info" title="排行榜暂未公开" sub-title="考试结束并公开成绩后即可查看" />
      <el-result v-else-if="loadError" icon="error" title="排行榜加载失败" sub-title="请检查网络或后端服务后重试"><template #extra><el-button @click="load">重新加载</el-button></template></el-result>
      <template v-else>
        <el-alert v-if="leaders.length >= 500" type="info" :closable="false" title="当前展示成绩最高的前 500 名" />
        <ResponsiveTable :rows="pageRows" :columns="columns" row-key="rank" label="考试排行榜" empty-text="暂无已公布的成绩">
          <template #student="{ row }"><div class="leader-student"><span class="leader-rank" :class="{ 'is-leading': row.rank <= 3 }">{{ row.rank }}</span><div class="workflow-stack"><strong class="workflow-title">{{ row.real_name || row.username }}</strong><span class="workflow-secondary">{{ row.username }}</span></div></div></template>
          <template #score="{ row }"><strong class="workflow-score">{{ row.total_score }}<small class="workflow-secondary"> 分</small></strong></template>
          <template #submitted="{ row }"><span class="workflow-secondary">{{ formatAppDateTime(row.submitted_at) }}</span></template>
        </ResponsiveTable>
        <footer v-if="leaders.length > pageSize" class="workflow-pagination"><span class="workflow-secondary">共 {{ leaders.length }} 位学生</span><el-pagination v-model:current-page="page" :page-size="pageSize" :total="leaders.length" :pager-count="5" layout="prev, pager, next" small /></footer>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { examApi } from '@/api'
import { formatAppDateTime } from '@/utils/examTime'
import ResponsiveTable from '@/components/lists/ResponsiveTable.vue'
import '@/styles/workflow.css'

const route = useRoute()
const loading = ref(true)
const exam = ref(null)
const leaders = ref([])
const loadError = ref(false)
const notPublic = ref(false)
const page = ref(1)
const pageSize = 20
let requestVersion = 0
let disposed = false
const columns = [{ key: 'student', label: '排名 / 学生' }, { key: 'score', label: '成绩', width: '22%' }, { key: 'submitted', label: '提交时间', width: '32%' }]
const pageRows = computed(() => leaders.value.slice((page.value - 1) * pageSize, page.value * pageSize))
async function load() {
  const version = ++requestVersion
  loading.value = true
  loadError.value = notPublic.value = false
  leaders.value = []
  try {
    const [e, l] = await Promise.all([examApi.get(route.params.id), examApi.leaderboard(route.params.id, { suppressError: true })])
    if (disposed || version !== requestVersion) return
    exam.value = e
    leaders.value = l
    page.value = 1
  } catch (error) {
    if (disposed || version !== requestVersion) return
    if (error?.response?.status === 403) notPublic.value = true
    else loadError.value = true
  } finally { if (!disposed && version === requestVersion) loading.value = false }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => { disposed = true; requestVersion++ })
</script>

<style scoped>
.leader-student { display: flex; gap: 16px; align-items: center; min-width: 0; }
.leader-student > div { min-width: 0; }
.leader-rank { flex: 0 0 32px; height: 32px; display: grid; place-items: center; font-variant-numeric: tabular-nums; color: var(--app-text-secondary); font-size: 14px; }
.leader-rank.is-leading { border-radius: 8px; color: var(--app-accent); background: #edf4fc; font-weight: 600; }
</style>
