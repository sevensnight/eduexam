<template>
  <div class="management-actions">
    <el-button link type="primary" :disabled="busy" @click="$emit('action', 'primary')">{{ primaryLabel }}</el-button>
    <el-dropdown trigger="click" @command="$emit('action', $event)">
      <el-button link class="management-more" :aria-label="'更多操作：' + name" :disabled="busy">更多<el-icon><ArrowDown /></el-icon></el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-for="item in actions" :key="item.command" :command="item.command" :disabled="item.disabled || busy" :divided="item.divided" :class="{ 'management-danger': item.danger }">{{ item.label }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>
<script setup>
import { ArrowDown } from '@element-plus/icons-vue'
defineProps({ name: String, primaryLabel: String, busy: Boolean, actions: { type: Array, default: () => [] } })
defineEmits(['action'])
</script>
<style scoped>
.management-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.management-actions .el-button { margin: 0; font-size: 13px; }
.management-more { color: var(--app-text-secondary); }
.management-more .el-icon { margin-left: 4px; font-size: 11px; }
.management-danger:not(.is-disabled) { color: var(--el-color-danger); }
</style>
