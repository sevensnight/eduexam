<template>
  <span v-if="label" class="answer-state-badge" :data-state="state">
    <el-icon aria-hidden="true"><component :is="icon" /></el-icon>
    <span>{{ label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { Check, CircleCheckFilled, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue'
import { answerOptionLabels } from '@/utils/answerOptions'
const props = defineProps({ state: { type: String, default: 'selected' } })
const label = computed(() => answerOptionLabels[props.state] || '')
const icon = computed(() => ({ selected: Check, correct: CircleCheckFilled, wrong: CircleCloseFilled, missed: WarningFilled, answer: CircleCheckFilled }[props.state]))
</script>

<style scoped>
.answer-state-badge { display: inline-flex; align-items: center; justify-content: center; gap: 5px; flex-shrink: 0; width: max-content; max-width: 100%; padding: 3px 8px; border-radius: 5px; background: var(--app-selection-border); color: #fff; font-size: 12px; font-weight: 600; line-height: 1.6; white-space: nowrap; }
.answer-state-badge .el-icon { font-size: 14px; }
.answer-state-badge[data-state="correct"], .answer-state-badge[data-state="answer"] { background: var(--app-answer-correct); }
.answer-state-badge[data-state="wrong"] { background: var(--app-answer-wrong); }
.answer-state-badge[data-state="missed"] { background: var(--app-answer-missed); }
@media (forced-colors: active) { .answer-state-badge { border: 1px solid CanvasText; } }
</style>
