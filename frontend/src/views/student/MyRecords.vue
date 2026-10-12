<template>
  <el-card header="我的考试记录" v-loading="loading">
    <el-empty v-if="!loading && !records.length" description="暂无考试记录" />
    <el-table v-else :data="records" border stripe>
      <el-table-column prop="exam_title" label="考试名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="得分" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.status === 'in_progress'" style="color:#909399">--</span>
          <span v-else-if="row.status === 'submitted' || !row.essay_graded" style="color:#E6A23C">待批改</span>
          <span v-else-if="!row.result_released" style="color:#909399">暂未公布</span>
          <span v-else style="font-weight:bold;color:#409EFF">{{ row.total_score ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="及格分" prop="pass_score" width="90" align="center" />
      <el-table-column label="参考次数" width="90" align="center">
        <template #default="{ row }">第 {{ row.attempt_count || 1 }} 次</template>
      </el-table-column>
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'in_progress'" type="primary" size="small">进行中</el-tag>
          <el-tag v-else-if="row.status === 'submitted' || !row.essay_graded" type="warning" size="small">待批改</el-tag>
          <el-tag v-else-if="!row.result_released" type="info" size="small">未公布</el-tag>
          <el-tag v-else-if="row.passed === true" type="success" size="small">及格</el-tag>
          <el-tag v-else-if="row.passed === false" type="danger" size="small">不及格</el-tag>
          <el-tag v-else type="info" size="small">--</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ row.submitted_at ? new Date(row.submitted_at).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/exams/${row.exam_id}/${row.status === 'in_progress' ? 'take' : 'result'}`)">
            {{ row.status === 'in_progress' ? '继续考试' : '查看详情' }}
          </el-button>
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
