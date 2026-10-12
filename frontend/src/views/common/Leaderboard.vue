<template>
  <el-card :header="`排行榜 — ${exam?.title || ''}`" v-loading="loading">
    <el-result v-if="!loading && notPublic" icon="info" title="排行榜暂未公开" sub-title="考试结束并公开成绩后即可查看" />
    <el-result v-else-if="!loading && loadError" icon="error" title="排行榜加载失败" sub-title="请检查网络或后端服务后重试">
      <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
    </el-result>
    <template v-else>
      <el-alert
        v-if="leaders.length >= 500"
        type="info"
        :closable="false"
        title="当前展示成绩最高的前 500 名"
        style="margin-bottom:12px"
      />
      <el-table :data="leaders" border stripe empty-text="暂无参赛记录">
      <el-table-column label="排名" width="80" align="center">
        <template #default="{ row }">
          <span v-if="row.rank === 1">🥇</span>
          <span v-else-if="row.rank === 2">🥈</span>
          <span v-else-if="row.rank === 3">🥉</span>
          <span v-else>{{ row.rank }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="real_name" label="姓名" />
      <el-table-column label="得分" width="100" align="center">
        <template #default="{ row }">
          <span style="font-size:16px;font-weight:bold;color:#409EFF">{{ row.total_score }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="180">
        <template #default="{ row }">{{ formatAppDateTime(row.submitted_at) }}</template>
      </el-table-column>
      </el-table>
    </template>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { examApi } from '@/api'
import { formatAppDateTime } from '@/utils/examTime'

const route = useRoute()
const examId = computed(() => route.params.id)
const loading = ref(true)
const exam = ref(null)
const leaders = ref([])
const loadError = ref(false)
const notPublic = ref(false)

async function load() {
  loading.value = true
  loadError.value = false
  notPublic.value = false
  try {
    const [e, l] = await Promise.all([
      examApi.get(examId.value),
      examApi.leaderboard(examId.value, { suppressError: true }),
    ])
    exam.value = e
    leaders.value = l
  } catch (error) {
    if (error?.response?.status === 403) notPublic.value = true
    else loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
