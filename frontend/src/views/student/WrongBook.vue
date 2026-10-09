<template>
  <el-card header="错题本" v-loading="loading">
    <el-empty v-if="!loading && !questions.length" description="暂无错题，继续加油！" />
    <div v-for="(q, idx) in questions" :key="q.question_id"
         style="border:1px solid #EBEEF5;border-radius:6px;padding:16px;margin-bottom:12px">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px">
        <span style="font-weight:bold;color:#606266">{{ idx + 1 }}.</span>
        <el-tag size="small" :type="typeTag(q.question_type)">{{ typeLabel(q.question_type) }}</el-tag>
        <span style="font-size:12px;color:#C0C4CC;flex:1">来自：{{ q.exam_title }}</span>
        <el-popconfirm title="移出错题本？" @confirm="remove(q.question_id)">
          <template #reference>
            <el-button link type="warning" size="small">移出错题本</el-button>
          </template>
        </el-popconfirm>
      </div>
      <div style="font-size:15px;margin-bottom:12px;line-height:1.7">{{ q.title }}</div>

      <div v-if="q.options" style="margin-bottom:10px">
        <div v-for="opt in parseOpts(q.options)" :key="opt"
             style="padding:4px 10px;margin-bottom:4px;border-radius:4px;font-size:13px"
             :style="optStyle(opt, q)">
          {{ opt }}
        </div>
      </div>

      <div style="font-size:13px;line-height:2;background:#FAFAFA;padding:10px 14px;border-radius:4px">
        <div><span style="color:#909399">我的答案：</span>
          <span style="color:#F56C6C;font-weight:bold">{{ q.user_answer || '（未作答）' }}</span>
        </div>
        <div><span style="color:#909399">正确答案：</span>
          <span style="color:#67C23A;font-weight:bold">{{ q.correct_answer }}</span>
        </div>
        <div v-if="q.explanation" style="margin-top:4px">
          <span style="color:#909399">解析：</span>{{ q.explanation }}
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { studentApi } from '@/api'

const loading = ref(true)
const questions = ref([])

function typeTag(t) { return { single: '', multiple: 'warning', truefalse: 'success', essay: 'info' }[t] || '' }
function typeLabel(t) { return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[t] || t }
function parseOpts(str) { try { return JSON.parse(str) } catch { return [] } }

function optStyle(opt, q) {
  const letter = opt.charAt(0)
  const correct = q.correct_answer?.split(',') || []
  const mine = q.user_answer?.split(',') || []
  if (correct.includes(letter)) return { background: '#F0F9EB', color: '#67C23A', fontWeight: 'bold' }
  if (mine.includes(letter)) return { background: '#FEF0F0', color: '#F56C6C' }
  return { color: '#606266' }
}

async function remove(qid) {
  await studentApi.removeWrong(qid)
  questions.value = questions.value.filter(q => q.question_id !== qid)
  ElMessage.success('已移出错题本')
}

onMounted(async () => {
  try {
    questions.value = await studentApi.wrongBook().catch(() => [])
  } finally {
    loading.value = false
  }
})
</script>
