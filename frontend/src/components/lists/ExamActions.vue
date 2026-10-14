<template>
  <div class="exam-actions">
    <template v-if="canManage">
      <el-button link type="primary" @click="$emit('action', primary.command)">{{ primary.label }}</el-button>
      <el-dropdown trigger="click" @command="$emit('action', $event)">
        <el-button link class="exam-more" :aria-label="`更多操作：${exam.title}`" :loading="busy">
          更多<el-icon class="more-arrow"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="primary.command !== 'edit'" command="edit">编辑考试</el-dropdown-item>
            <el-dropdown-item v-if="primary.command !== 'stats'" command="stats">查看统计</el-dropdown-item>
            <el-dropdown-item command="grade" :disabled="!exam.has_essay">{{ exam.has_essay ? '批改答卷' : '批改（无主观题）' }}</el-dropdown-item>
            <el-dropdown-item v-if="exam.status !== 'closed'" command="status" :disabled="busy">{{ exam.status === 'published' ? '关闭考试' : '发布考试' }}</el-dropdown-item>
            <el-dropdown-item command="delete" divided :disabled="busy" class="exam-delete">删除考试</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </template>
    <template v-else-if="isStudent">
      <div class="student-actions">
        <el-button link type="primary" :disabled="!!startReason" @click="$emit('action', 'take')">参加考试</el-button>
        <el-button link @click="$emit('action', 'result')">查看成绩</el-button>
      </div>
      <span v-if="startReason" class="start-reason">{{ startReason }}</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { examPrimaryAction } from '@/utils/listPresentation'
const props = defineProps({ exam: { type: Object, required: true }, canManage: Boolean, isStudent: Boolean, busy: Boolean, startReason: { type: String, default: '' } })
defineEmits(['action'])
const primary = computed(() => examPrimaryAction(props.exam))
</script>

<style scoped>
.exam-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; min-width: 0; }
.exam-actions .el-button { margin: 0; font-size: 13px; }
.exam-more { color: var(--app-text-secondary); }
.more-arrow { margin-left: 4px; font-size: 11px; }
.exam-delete { color: var(--el-color-danger); }
.student-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.start-reason { flex-basis: 100%; color: var(--app-text-secondary); font-size: 12px; line-height: 1.4; }
</style>
