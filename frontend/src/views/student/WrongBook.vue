<template>
  <div class="workflow-page wrong-book">
    <el-card>
      <template #header><div class="workflow-heading"><div><h2>错题回顾<span class="workflow-count">{{ questions.length }} 题</span></h2><p class="workflow-caption">对照作答与解析，找到需要巩固的知识</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新题目</el-button></div></template>
      <div class="workflow-body"><div class="workflow-tools"><div class="workflow-field workflow-search"><label for="wrong-search">搜索错题</label><el-input id="wrong-search" v-model="query" :prefix-icon="Search" placeholder="题目内容或来源考试" clearable /></div><div class="workflow-field"><label for="wrong-type">题目类型</label><el-select id="wrong-type" v-model="typeFilter" placeholder="全部题型" clearable><el-option v-for="type in ['single','multiple','truefalse','essay']" :key="type" :label="questionTypeLabel(type)" :value="type" /></el-select></div></div>
        <el-skeleton v-if="loading" :rows="6" animated /><el-alert v-else-if="loadError" type="error" :closable="false" title="错题加载失败"><el-button link type="primary" @click="load">重新加载</el-button></el-alert>
        <template v-else><div class="wrong-review-list"><AnswerReview v-for="(question, index) in pageRows" :key="question.question_id" :question="question" :index="(page - 1) * pageSize + index" reveal-answers><span class="workflow-secondary wrong-source">来自：{{ question.exam_title || '考试练习' }}</span><el-popconfirm title="确认将这道题移出错题本？" confirm-button-text="确认移出" cancel-button-text="取消" @confirm="remove(question.question_id)"><template #reference><el-button link type="info" :disabled="busyIds.has(question.question_id)">移出错题本</el-button></template></el-popconfirm></AnswerReview></div><el-empty v-if="!pageRows.length" :image-size="72" :description="query || typeFilter ? '没有匹配的错题' : '暂无错题，继续保持'"/><footer v-if="filteredRows.length" class="workflow-pagination"><span class="workflow-secondary">共 {{ filteredRows.length }} 道错题 · 每页 {{ pageSize }} 道</span><el-pagination v-if="filteredRows.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="filteredRows.length" :pager-count="5" layout="prev, pager, next" small /></footer></template>
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { studentApi } from '@/api'
import { questionTypeLabel } from '@/utils/listPresentation'
import { matchesKeyword } from '@/utils/management'
import AnswerReview from '@/components/lists/AnswerReview.vue'
import '@/styles/workflow.css'
const loading = ref(true), loadError = ref(false), questions = ref([]), query = ref(''), typeFilter = ref(''), page = ref(1)
const pageSize = 10, busyIds = ref(new Set())
let requestId = 0, disposed = false
const filteredRows = computed(() => questions.value.filter(row => (!typeFilter.value || row.question_type === typeFilter.value) && matchesKeyword([row.title, row.exam_title], query.value)))
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([query, typeFilter], () => { page.value = 1 })
watch(() => filteredRows.value.length, count => { page.value = Math.min(page.value, Math.max(1, Math.ceil(count / pageSize))) })
async function remove(id) {
  if (busyIds.value.has(id)) return
  busyIds.value.add(id)
  try { await studentApi.removeWrong(id); if (!disposed) { questions.value = questions.value.filter(q => q.question_id !== id); ElMessage.success('已移出错题本') } }
  catch { /* Keep the question on failed deletion. */ }
  finally { busyIds.value.delete(id) }
}
async function load() {
  const version = ++requestId
  loading.value = true; loadError.value = false
  try { const data = await studentApi.wrongBook(); if (version === requestId && !disposed) questions.value = data }
  catch { if (version === requestId && !disposed) loadError.value = true }
  finally { if (version === requestId && !disposed) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { disposed = true; ++requestId })
</script>
<style scoped>
.wrong-source { flex: 1; min-width: 120px; }
</style>
