<template>
  <el-dialog :model-value="modelValue" title="题目详情" width="760px" top="8vh" class="question-preview-dialog" append-to-body destroy-on-close @update:model-value="$emit('update:modelValue', $event)">
    <div class="question-preview-content" :aria-busy="loading" role="region" aria-label="题目详情内容" tabindex="0">
      <el-skeleton v-if="loading" :rows="6" animated aria-label="正在加载题目详情" />
      <el-alert v-else-if="error" type="error" :closable="false" :title="error" show-icon>
        <el-button link type="primary" @click="loadQuestion">重新加载</el-button>
      </el-alert>
      <template v-else-if="question">
        <div class="preview-question-meta">
          <el-tag type="info" size="small">{{ questionTypeLabel(question.question_type) }}</el-tag>
          <el-tag v-if="question.difficulty" :type="difficultyTone(question.difficulty)" size="small">{{ difficultyLabel(question.difficulty) }}</el-tag>
          <span>题干、选项、答案与解析</span>
        </div>
        <QuestionDetails :question="question" />
      </template>
    </div>
    <template #footer><el-button @click="$emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { questionApi } from '@/api'
import { difficultyLabel, difficultyTone, questionTypeLabel } from '@/utils/listPresentation'
import QuestionDetails from './QuestionDetails.vue'

const props = defineProps({
  modelValue: Boolean,
  questionId: { type: [Number, String], default: null },
})
defineEmits(['update:modelValue'])

const question = ref(null)
const loading = ref(false)
const error = ref('')
let requestVersion = 0

async function loadQuestion() {
  const version = ++requestVersion
  const id = props.questionId
  question.value = null
  error.value = ''
  loading.value = false
  if (!props.modelValue) return
  if (id == null) {
    error.value = '未找到题目编号，请关闭后重新选择。'
    return
  }
  loading.value = true
  try {
    // 始终按题目 ID 读取受权限保护的详情，不依赖当前题库分页或题干文本。
    const detail = await questionApi.get(id)
    if (version !== requestVersion) return
    if (!detail || String(detail.id) !== String(id)) throw new Error('题目详情不匹配')
    question.value = detail
  } catch (cause) {
    if (version !== requestVersion) return
    const status = cause?.response?.status
    error.value = status === 404 ? '题目不存在，或你没有查看权限。'
      : status === 403 ? '你没有查看这道题目的权限。'
        : '题目详情加载失败，请重试。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

// 关闭、切题或离开页面时让旧响应失效，避免上一道题的答案覆盖新题。
watch([() => props.modelValue, () => props.questionId], loadQuestion, { immediate: true })
onBeforeUnmount(() => { requestVersion++ })
</script>

<style scoped>
.question-preview-content { max-height: min(65dvh, 680px, calc(100dvh - 200px)); overflow-y: auto; overscroll-behavior: contain; container-type: inline-size; container-name: management; }
.preview-question-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 14px; }
.preview-question-meta > span:last-child:not(.el-tag) { color: var(--app-text-secondary); font-size: 12px; }
</style>
