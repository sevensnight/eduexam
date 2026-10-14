<template>
  <div class="taxonomy-browser">
    <div class="directory-heading">
      <h2>{{ isCategory ? '分类目录' : '全部标签' }}</h2>
      <div v-if="isCategory" class="directory-tools">
        <button type="button" :disabled="busy || loading || !!query" @click="$emit('expand-all', true)">展开</button>
        <span aria-hidden="true">/</span>
        <button type="button" :disabled="busy || loading || !!query" @click="$emit('expand-all', false)">收起</button>
      </div>
    </div>
    <el-input :model-value="query" :aria-label="isCategory ? '搜索分类' : '搜索标签'" :placeholder="isCategory ? '搜索分类名称' : '搜索标签名称'" :prefix-icon="Search" clearable @update:model-value="$emit('update:query', $event)" />
    <div class="directory-items" :aria-busy="loading || busy" v-loading="loading || busy">
      <div v-if="error" class="directory-state" role="alert">
        <span>目录加载失败</span><el-button link type="primary" @click="$emit('retry')">重新加载</el-button>
      </div>
      <el-tree
        v-else-if="isCategory" ref="treeRef" :data="categories" node-key="id" :indent="16"
        :props="{ label: 'name', children: 'children' }" :current-node-key="selectedId" highlight-current
        :default-expanded-keys="expandedKeys" :expand-on-click-node="false" :filter-node-method="filterNode"
        :draggable="!query && !busy && !loading" :allow-drag="allowDrag" :allow-drop="allowDrop"
        :empty-text="query ? '没有匹配的分类' : '暂无分类，点击上方新建'" aria-label="题目分类目录"
        @node-click="$emit('select', $event)" @node-drop="$emit('drop')"
        @node-expand="changeExpanded($event.id, true)" @node-collapse="changeExpanded($event.id, false)"
        @keydown.capture="onTreeKeydown"
      >
        <template #default="{ data }">
          <div class="directory-node" :data-category-id="data.id">
            <span class="directory-name" :title="data.name">{{ data.name }}</span>
            <span class="directory-count" :title="'本级题目：' + (data.question_count || 0) + ' 题'">{{ data.question_count || 0 }}</span>
            <el-dropdown v-if="canManage(data) || canAdd(data)" trigger="click" @command="$emit('action', $event, data)">
              <button type="button" class="directory-more" :aria-label="data.name + '的操作'" :disabled="busy" @click.stop @keydown.stop><el-icon><MoreFilled /></el-icon></button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canManage(data)" command="edit">编辑分类</el-dropdown-item>
                  <el-dropdown-item v-if="canAdd(data)" command="add">添加子分类</el-dropdown-item>
                  <el-dropdown-item v-if="canManage(data)" command="delete" divided class="taxonomy-danger">删除分类</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-tree>
      <template v-else>
        <div v-for="tag in filteredTags" :key="tag.id" class="directory-tag" :class="{ 'is-selected': selectedId === tag.id }">
          <button type="button" class="tag-select" :aria-pressed="selectedId === tag.id" @click="$emit('select', tag)">
            <span class="tag-dot" :style="{ backgroundColor: safeTagColor(tag.color) }" aria-hidden="true"></span>
            <span class="directory-name" :title="tag.name">{{ tag.name }}</span>
            <el-icon v-if="selectedId === tag.id" class="tag-check"><Check /></el-icon>
          </button>
          <el-dropdown v-if="canManage(tag)" trigger="click" @command="$emit('action', $event, tag)">
            <button type="button" class="directory-more" :aria-label="tag.name + '的操作'" :disabled="busy"><el-icon><MoreFilled /></el-icon></button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete" class="taxonomy-danger">删除标签</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
        <p v-if="!loading && !filteredTags.length" class="directory-state">{{ query ? '没有匹配的标签' : '暂无标签，点击上方新建' }}</p>
      </template>
    </div>
    <p class="directory-note">{{ isCategory ? '右侧数字为本级题数，不含子分类。可拖动有权限的分类调整顺序。' : '标签用于跨分类标记题目，点击查看关联内容。' }}</p>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Check, MoreFilled, Search } from '@element-plus/icons-vue'
import { safeTagColor } from '@/utils/taxonomy'
const props = defineProps({
  mode: String, categories: Array, tags: Array, selectedId: Number, expandedKeys: Array,
  query: String, loading: Boolean, error: Boolean, busy: Boolean,
  canManage: Function, canAdd: Function, allowDrag: Function, allowDrop: Function,
})
const emit = defineEmits(['select', 'action', 'drop', 'update:query', 'update:expandedKeys', 'expand-all', 'retry'])
const treeRef = ref(null)
const isCategory = computed(() => props.mode === 'category')
const filteredTags = computed(() => props.tags.filter(tag => tag.name.toLocaleLowerCase().includes(props.query.trim().toLocaleLowerCase())))
const filterNode = (query, data) => data.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())
function onTreeKeydown(event) {
  if (!event.target.matches('[role="treeitem"]') || !['Enter', ' ', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return
  const node = treeRef.value?.getNode(event.target.dataset.key)
  if (!node) return
  event.preventDefault()
  event.stopPropagation()
  if (event.key === 'Enter' || event.key === ' ') emit('select', node.data)
  else if (event.key === 'ArrowRight' && node.childNodes.length) {
    if (!node.expanded) changeExpanded(node.key, true)
    else event.target.querySelector('.el-tree-node__children > [role="treeitem"]')?.focus()
  } else if (event.key === 'ArrowLeft') {
    if (node.expanded) changeExpanded(node.key, false)
    else event.target.parentElement.closest('[role="treeitem"]')?.focus()
  }
}
function changeExpanded(id, open) {
  if (props.query) return
  const keys = new Set(props.expandedKeys)
  if (open) keys.add(id)
  else keys.delete(id)
  emit('update:expandedKeys', [...keys])
}
async function syncTree() {
  await nextTick()
  const tree = treeRef.value
  if (!tree) return
  tree.filter(props.query)
  if (!props.query) {
    const keys = new Set(props.expandedKeys)
    function visit(nodes) {
      for (const item of nodes) {
        const node = tree.getNode(item.id)
        if (node) node.expanded = keys.has(item.id)
        visit(item.children || [])
      }
    }
    visit(props.categories)
  }
  tree.setCurrentKey(props.selectedId, false)
}
watch(() => [props.query, props.expandedKeys, props.categories, props.selectedId, props.mode], syncTree, { immediate: true })
</script>

<style scoped>
.taxonomy-browser { display: flex; flex-direction: column; min-width: 0; height: 100%; padding: 22px 18px 16px; }
.directory-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.directory-heading h2 { font-size: 13px; letter-spacing: 0; margin: 0; color: var(--app-text-secondary); font-weight: 500; }
.directory-tools { display: flex; align-items: center; gap: 5px; color: #d6d6dc; font-size: 12px; }
.directory-tools button { border: 0; padding: 3px; color: var(--app-text-secondary); background: transparent; border-radius: 4px; font-size: 12px; }
.directory-tools button:hover:not(:disabled) { color: var(--app-accent); }
.directory-tools button:disabled { opacity: .45; }
.directory-items { margin: 16px -6px 0; padding: 0 4px 4px; min-height: 240px; max-height: 590px; overflow: auto; scrollbar-width: thin; flex: 1; }
.directory-node { display: flex; align-items: center; gap: 9px; flex: 1; min-width: 0; padding-right: 5px; }
.directory-name { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; min-width: 0; flex: 1; }
.directory-count { flex: 0 0 auto; font-size: 12px; color: var(--app-text-secondary); font-variant-numeric: tabular-nums; }
.directory-more { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: 0; border-radius: 5px; background: transparent; color: #8c9098; }
.directory-more:hover { color: var(--app-accent); background: #e9eff7; }
.directory-tag { display: flex; align-items: center; padding-right: 5px; border-radius: 7px; margin-bottom: 3px; min-width: 0; }
.directory-tag:hover { background: #f0f2f6; }
.directory-tag.is-selected { background: var(--app-selection-bg); box-shadow: inset 3px 0 var(--app-selection-border); }
.directory-tag.is-selected .tag-select { color: var(--app-selection-ink); font-weight: 600; }
.tag-select { display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1; min-height: 42px; padding: 9px 10px; border: 0; border-radius: 7px; background: transparent; text-align: left; color: var(--app-text); font-size: 13px; }
.tag-dot { flex: 0 0 8px; width: 8px; height: 8px; border-radius: 50%; }
.tag-check { color: var(--app-accent); font-size: 14px; }
.directory-note { color: var(--app-text-secondary); font-size: 11px; line-height: 1.7; margin: 14px 0 0; padding-top: 14px; border-top: 1px solid var(--app-border); }
.directory-state { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 32px 8px; margin: 0; font-size: 13px; color: var(--app-text-secondary); }
:deep(.el-tree) { background: transparent; font-size: 13px; }
:deep(.el-tree-node__content) { height: 42px; margin-bottom: 3px; border-radius: 7px; }
:deep(.el-tree-node.is-current > .el-tree-node__content) { background: var(--app-selection-bg); color: var(--app-selection-ink); box-shadow: inset 3px 0 var(--app-selection-border); font-weight: 600; }
:deep(.el-tree-node:focus-visible > .el-tree-node__content) { outline: 2px solid var(--app-accent); outline-offset: -2px; }
:deep(.el-tree__empty-block) { min-height: 100px; }
:deep(.el-tree__empty-text) { font-size: 12px; width: 100%; }
.taxonomy-danger { color: var(--el-color-danger); }
</style>
