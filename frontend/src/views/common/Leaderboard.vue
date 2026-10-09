<template>
  <el-card :header="`排行榜 — ${exam?.title || ''}`" v-loading="loading">
    <el-table :data="leaders" border stripe>
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
        <template #default="{ row }">{{ row.submitted_at ? new Date(row.submitted_at).toLocaleString() : '-' }}</template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !leaders.length" description="暂无参赛记录" />
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { examApi } from '@/api'

const route = useRoute()
const examId = computed(() => route.params.id)
const loading = ref(true)
const exam = ref(null)
const leaders = ref([])

onMounted(async () => {
  try {
    const [e, l] = await Promise.all([
      examApi.get(examId.value).catch(() => null),
      examApi.leaderboard(examId.value).catch(() => []),
    ])
    exam.value = e
    leaders.value = l
  } finally {
    loading.value = false
  }
})
</script>
