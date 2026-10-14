<template>
  <section class="question-details" aria-label="题目详情">
    <dl class="detail-fields">
      <div class="detail-field">
        <dt>完整题干</dt>
        <dd class="detail-copy">{{ question.title }}</dd>
      </div>
      <div v-if="options.length" class="detail-field">
        <dt>选项</dt>
        <dd>
          <ol class="detail-options" aria-label="题目选项">
            <li v-for="(option, index) in options" :key="index"><span>{{ optionLetter(index) }}</span><p>{{ option }}</p></li>
          </ol>
        </dd>
      </div>
      <div class="detail-field detail-answer">
        <dt>正确答案</dt>
        <dd class="detail-copy" :class="{ 'short-answer': question.question_type !== 'essay' }">{{ displayQuestionAnswer(question) }}</dd>
      </div>
      <div class="detail-field">
        <dt>解析</dt>
        <dd class="detail-copy">{{ question.explanation || '暂无解析' }}</dd>
      </div>
    </dl>
    <div class="detail-footer">
      <span>分类：{{ question.category_path || question.category_name || '未分类' }}</span>
      <span v-if="question.tags?.length">标签：{{ question.tags.map(tag => tag.name).join('、') }}</span>
      <span>更新：{{ formatAppDateTime(question.updated_at || question.created_at, '未设置') }}</span>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { optionLetter, parseOptionList } from '@/utils/questionEditor'
import { formatAppDateTime } from '@/utils/examTime'
import { displayQuestionAnswer } from '@/utils/listPresentation'
const props = defineProps({ question: { type: Object, required: true } })
const options = computed(() => parseOptionList(props.question.options))
</script>

<style scoped>
.question-details { padding: 18px 20px; background: #fafbfc; color: var(--app-text); border: 1px solid #e9edf2; border-radius: 8px; font-size: 14px; line-height: 1.75; }
.detail-fields { display: grid; gap: 12px; margin: 0; }
.detail-field { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: 16px; align-items: baseline; }
.detail-field dt { color: var(--app-text-secondary); font-size: 12px; line-height: 1.75; }
.detail-field dd { min-width: 0; margin: 0; }
.detail-copy { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.detail-options { display: grid; gap: 6px; padding: 0; margin: 0; list-style: none; }
.detail-options li { display: flex; gap: 10px; align-items: baseline; }
.detail-options li > span { flex: 0 0 24px; background: var(--app-surface); border: 1px solid var(--app-border); text-align: center; border-radius: 5px; font-size: 12px; }
.detail-options p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; min-width: 0; }
.short-answer { color: var(--el-color-success); font-weight: 600; }
.detail-footer { display: flex; flex-wrap: wrap; gap: 6px 20px; border-top: 1px solid var(--app-border); padding-top: 12px; margin-top: 14px; font-size: 12px; color: var(--app-text-secondary); overflow-wrap: anywhere; }
@container management (max-width: 480px) {
  .question-details { padding: 14px; }
  .detail-fields { gap: 14px; }
  .detail-field { grid-template-columns: minmax(0, 1fr); gap: 3px; }
}
</style>
