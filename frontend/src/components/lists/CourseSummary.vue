<template>
  <div class="course-summary">
    <span class="course-symbol" aria-hidden="true"><el-icon><Reading /></el-icon></span>
    <div class="course-copy"><button type="button" class="course-name" @click="$emit('open')">{{ course.name }}</button><p class="course-description">{{ course.description || '暂无课程说明' }}</p></div>
  </div>
</template>
<script setup>
import { Reading } from '@element-plus/icons-vue'
defineProps({ course: { type: Object, required: true } })
defineEmits(['open'])
</script>
<style scoped>
.course-summary { display: flex; align-items: flex-start; gap: 12px; min-width: 0; }
.course-symbol { display: grid; place-items: center; width: 36px; height: 36px; flex: 0 0 36px; border-radius: 9px; background: var(--app-accent-soft); color: var(--app-accent); font-size: 18px; }
.course-copy { min-width: 0; }
.course-name { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; border: 0; padding: 0; border-radius: 3px; background: transparent; color: var(--app-text); text-align: left; font-size: 14px; font-weight: 500; line-height: 1.6; overflow-wrap: anywhere; }
.course-name:hover { color: var(--app-accent); }
.course-description { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; margin: 5px 0 0; font-size: 12px; line-height: 1.65; color: var(--app-text-secondary); overflow-wrap: anywhere; }
</style>
