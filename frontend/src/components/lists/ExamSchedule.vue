<template>
  <dl class="exam-schedule">
    <div v-for="item in schedule" :key="item.label">
      <dt>{{ item.label }}</dt>
      <dd><span>{{ item.date }}</span><span v-if="item.time">{{ item.time }}</span></dd>
    </div>
  </dl>
</template>

<script setup>
import { computed } from 'vue'
import { listDateTime } from '@/utils/listPresentation'
const props = defineProps({ exam: { type: Object, required: true } })
const schedule = computed(() => [
  { label: '开始', ...listDateTime(props.exam.open_time, '不限') },
  { label: '截止', ...listDateTime(props.exam.close_time, '不限') },
])
</script>

<style scoped>
.exam-schedule { display: grid; gap: 7px; margin: 0; font-size: 12px; line-height: 1.65; }
.exam-schedule > div { display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 8px; }
.exam-schedule dt { color: var(--app-text-secondary); }
.exam-schedule dd { display: flex; flex-wrap: wrap; gap: 0 6px; margin: 0; font-variant-numeric: tabular-nums; color: var(--el-text-color-regular); }
.exam-schedule dd > span { white-space: nowrap; }
</style>
