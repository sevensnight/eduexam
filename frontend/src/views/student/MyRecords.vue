<template>
  <el-card header="我的考试记录" v-loading="loading">
    <el-empty v-if="!loading && !records.length" description="暂无考试记录" />
    <el-table v-else :data="records" border stripe>
      <el-table-column prop="exam_title" label="考试名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="得分" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.essay_graded" style="font-weight:bold;color:#409EFF">{{ row.total_score ?? '-' }}</span>
          <el-tooltip v-else content="主观题待批改" placement="top">
            <span style="color:#E6A23C">{{ row.total_score ?? '-' }} *</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="及格分" prop="pass_score" width="90" align="center" />
      <el-table-column label="参考次数" width="90" align="center">
        <template #default="{ row }">第 {{ row.attempt_count || 1 }} 次</template>
      </el-table-column>
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.passed === true" type="success" size="small">及格</el-tag>
          <el-tag v-else-if="row.passed === false" type="danger" size="small">不及格</el-tag>
          <el-tag v-else type="warning" size="small">待批改</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ row.submitted_at ? new Date(row.submitted_at).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/exams/${row.exam_id}/result`)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { studentApi } from '@/api'

const loading = ref(true)
const records = ref([])

onMounted(async () => {
  try {
    records.value = await studentApi.myRecords().catch(() => [])
  } finally {
    loading.value = false
  }
})
</script>
