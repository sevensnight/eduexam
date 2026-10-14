<template>
  <div class="question-summary">
    <button type="button" class="question-trigger" :aria-expanded="expanded" :aria-controls="controlsId" @click="$emit('toggle')">
      <el-icon class="question-chevron" :class="{ 'is-expanded': expanded }"><ArrowRight /></el-icon>
      <span class="question-title">{{ question.title }}</span>
    </button>
    <div class="question-meta">
      <span class="question-category">{{ question.category_name || '未分类' }}</span>
      <span v-for="tag in (question.tags || []).slice(0, 2)" :key="tag.id" class="question-tag">{{ tag.name }}</span>
      <span v-if="question.tags?.length > 2" class="question-tag">+{{ question.tags.length - 2 }}</span>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight } from '@element-plus/icons-vue'
defineProps({ question: { type: Object, required: true }, expanded: Boolean, controlsId: String })
defineEmits(['toggle'])
</script>

<style scoped>
.question-summary { min-width: 0; }
.question-trigger { display: grid; grid-template-columns: 16px minmax(0, 1fr); gap: 9px; width: 100%; padding: 3px 0; border: 0; border-radius: 4px; background: transparent; text-align: left; color: var(--app-text); line-height: 1.65; }
.question-trigger:hover { color: var(--app-accent); }
.question-chevron { margin-top: 5px; font-size: 12px; color: var(--app-text-secondary); transition: transform .15s; }
.question-chevron.is-expanded { transform: rotate(90deg); color: var(--app-accent); }
.question-title { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; overflow-wrap: anywhere; font-weight: 500; }
.question-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 5px 8px; margin: 7px 0 0 25px; font-size: 12px; line-height: 1.6; }
.question-category { color: var(--app-text-secondary); overflow-wrap: anywhere; }
.question-tag { padding: 1px 7px; border-radius: 5px; background: var(--el-fill-color-light); color: var(--app-text-secondary); overflow-wrap: anywhere; }
</style>
