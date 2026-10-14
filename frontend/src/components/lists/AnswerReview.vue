<template>
  <article class="answer-review">
    <header class="answer-review-header">
      <span class="answer-index">第 {{ index + 1 }} 题</span><span class="answer-type">{{ questionTypeLabel(item.question_type) }}</span>
      <el-tag v-if="revealAnswers && item.is_correct" size="small" :type="resultTone">{{ resultLabel }}</el-tag>
      <span v-if="revealAnswers && item.max_score != null" class="answer-points">{{ pending ? '满分 ' + item.max_score + ' 分' : (item.score_got ?? '未评分') + ' / ' + item.max_score + ' 分' }}</span>
    </header>
    <h3>{{ item.title }}</h3>
    <div v-if="options.length" class="review-options" role="list" aria-label="作答选项">
      <div v-for="option in options" :key="option.value" class="answer-option" :class="{ 'is-selected': option.selected }" :data-state="option.state" :data-value="option.value" role="listitem">
        <span class="answer-option-marker" :aria-hidden="item.question_type === 'truefalse'">{{ option.marker }}</span>
        <span class="answer-option-text">{{ option.text }}</span>
        <AnswerStateBadge v-if="option.label" :state="option.state" />
      </div>
    </div>
    <dl class="answer-fields">
      <dt>{{ answerLabel }}</dt><dd class="submitted-answer"><span v-if="selectedValues.length" class="answer-values"><span v-for="value in selectedValues" :key="value" class="answer-value">{{ reviewAnswer(value, item.question_type) }}</span></span><span v-else>{{ reviewAnswer(item.user_answer, item.question_type) }}</span></dd>
      <template v-if="revealAnswers && item.correct_answer != null"><dt>参考答案</dt><dd class="reference-answer">{{ reviewAnswer(item.correct_answer, item.question_type, '暂未提供') }}</dd></template>
      <template v-if="revealAnswers && item.explanation"><dt>题目解析</dt><dd>{{ item.explanation }}</dd></template>
    </dl>
    <footer v-if="$slots.default" class="answer-review-footer"><slot /></footer>
  </article>
</template>
<script setup>
import { computed } from 'vue'
import { questionTypeLabel } from '@/utils/listPresentation'
import { reviewAnswer, reviewQuestion } from '@/utils/examPresentation'
import { answerSelectionValues, reviewOptionRows } from '@/utils/answerOptions'
import AnswerStateBadge from './AnswerStateBadge.vue'
const props = defineProps({ question: { type: Object, required: true }, index: { type: Number, default: 0 }, revealAnswers: Boolean, pending: Boolean, answerLabel: { type: String, default: '我的作答' } })
const item = computed(() => reviewQuestion(props.question))
const options = computed(() => reviewOptionRows(item.value, props.revealAnswers))
const selectedValues = computed(() => answerSelectionValues(item.value.user_answer, item.value.question_type))
const resultLabel = computed(() => props.pending ? '待批改' : ({ Y: '正确', N: '错误', P: '部分得分' }[item.value.is_correct] || '未评分'))
const resultTone = computed(() => props.pending ? 'warning' : ({ Y: 'success', N: 'danger', P: 'warning' }[item.value.is_correct] || 'info'))
</script>
<style scoped>
.answer-review { padding: 22px 0; border-bottom: 1px solid var(--app-border); min-width: 0; }
.answer-review:first-child { padding-top: 4px; } .answer-review:last-child { border-bottom: 0; }
.answer-review-header { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; font-size: 12px; }
.answer-index { color: var(--app-text-secondary); font-variant-numeric: tabular-nums; }
.answer-type { color: #53657b; background: #eff3f8; padding: 2px 7px; border-radius: 5px; }
.answer-points { margin-left: auto; font-variant-numeric: tabular-nums; color: var(--app-text-secondary); }
.answer-review h3 { margin: 0 0 16px; font-size: 14px; font-weight: 500; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.review-options { display: grid; gap: 10px; margin: 0 0 18px; font-size: 14px; }
.answer-option { --option-accent: var(--app-selection-border); --option-bg: var(--app-selection-bg); --option-ink: var(--app-selection-ink); display: grid; grid-template-columns: 30px minmax(0,1fr) auto; align-items: start; gap: 12px; padding: 13px 14px; border: 2px solid #e7eaf0; border-radius: 9px; background: #f8f9fb; }
.answer-option-marker { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 6px; background: #e9edf3; color: #4d5c70; font-weight: 600; line-height: 1; }
.answer-option-text { min-width: 0; align-self: center; color: #25364d; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.answer-option > .answer-state-badge { align-self: center; }
.answer-option:not([data-state="neutral"]) { border-color: var(--option-accent); background: var(--option-bg); }
.answer-option:not([data-state="neutral"]) .answer-option-marker { background: var(--option-accent); color: #fff; }
.answer-option:not([data-state="neutral"]) .answer-option-text { color: var(--option-ink); font-weight: 600; }
.answer-option[data-state="correct"], .answer-option[data-state="answer"] { --option-accent: var(--app-answer-correct); --option-bg: var(--app-answer-correct-bg); --option-ink: #185a35; }
.answer-option[data-state="wrong"] { --option-accent: var(--app-answer-wrong); --option-bg: var(--app-answer-wrong-bg); --option-ink: #96242b; }
.answer-option[data-state="missed"] { --option-accent: var(--app-answer-missed); --option-bg: var(--app-answer-missed-bg); --option-ink: #714b00; border-style: dashed; }
.answer-option[data-state="answer"] { border-style: dashed; }
.answer-option[data-state="missed"] .answer-option-marker, .answer-option[data-state="answer"] .answer-option-marker { background: #fff; color: var(--option-accent); border: 1px solid currentColor; }
.answer-fields { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 12px 16px; margin: 0; padding: 16px; background: #f7f8fa; border-radius: 9px; font-size: 13px; line-height: 1.8; }
.answer-fields dt { color: var(--app-text-secondary); } .answer-fields dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.answer-fields .reference-answer { color: #287047; }
.answer-values { display: flex; flex-wrap: wrap; gap: 6px; }
.answer-value { display: inline-grid; place-items: center; min-width: 29px; min-height: 28px; padding: 0 8px; border: 1px solid #b6cdef; border-radius: 5px; background: var(--app-selection-bg); color: var(--app-selection-ink); font-weight: 600; }
.answer-review-footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
@media (max-width: 540px) { .answer-fields { grid-template-columns: minmax(0,1fr); gap: 5px; padding: 13px; } .answer-fields dd:not(:last-child) { margin-bottom: 8px; } }
@media (max-width: 540px) { .answer-option { grid-template-columns: 28px minmax(0,1fr); gap: 8px 10px; padding: 12px; } .answer-option-marker { width: 28px; height: 28px; } .answer-option > .answer-state-badge { grid-column: 2; justify-self: start; } }
@media (forced-colors: active) { .answer-option.is-selected { border-width: 3px; } }
</style>
