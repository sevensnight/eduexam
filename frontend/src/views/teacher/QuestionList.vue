<template>
  <div class="management-list question-list">
    <el-card>
      <template #header>
        <div class="list-heading">
          <div>
            <h2>全部题目</h2><span class="list-count-badge">{{ total }}</span>
          </div>
          <div v-if="store.canManage" class="header-actions">
            <el-upload :show-file-list="false" accept=".csv,.xlsx" :http-request="uploadQuestions">
              <el-button :icon="Upload">导入题目</el-button>
            </el-upload>
            <el-dropdown trigger="click" @command="downloadQuestions">
              <el-button :icon="Download" :loading="exporting">导出<el-icon class="button-arrow"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="xlsx">导出 Excel</el-dropdown-item>
                  <el-dropdown-item command="csv">导出 CSV</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button type="primary" :icon="Plus" @click="$router.push('/questions/create')">新建题目</el-button>
          </div>
        </div>
      </template>

      <div class="list-body">
        <form class="question-filters" @submit.prevent="applyFilters">
          <div class="filter-primary">
            <div class="filter-field search-field">
              <label for="question-search">搜索题目</label>
              <div class="search-control">
                <el-input id="question-search" v-model="filters.keyword" placeholder="题干、答案、解析…" clearable :prefix-icon="Search" @clear="applyFilters" />
                <el-button type="primary" native-type="submit">查询</el-button>
              </div>
            </div>
            <div class="filter-field">
              <label for="question-category">分类</label>
              <el-select id="question-category" v-model="filters.category_id" placeholder="全部分类" clearable filterable @change="applyFilters">
                <el-option v-for="item in flatCategories" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </div>
            <div class="filter-field">
              <label for="question-type">题型</label>
              <el-select id="question-type" v-model="filters.question_type" placeholder="全部题型" clearable @change="applyFilters">
                <el-option v-for="type in questionTypes" :key="type" :label="questionTypeLabel(type)" :value="type" />
              </el-select>
            </div>
            <el-button class="more-filters" :icon="Filter" :aria-expanded="advancedOpen" aria-controls="question-advanced-filters" @click="advancedOpen = !advancedOpen">
              更多筛选<span v-if="advancedCount" class="filter-count">{{ advancedCount }}</span>
            </el-button>
          </div>
          <div v-show="advancedOpen" id="question-advanced-filters" class="filter-advanced">
            <div class="filter-field">
              <label for="question-tag">标签</label>
              <el-select id="question-tag" v-model="filters.tag_id" placeholder="全部标签" clearable filterable @change="applyFilters">
                <el-option v-for="tag in allTags" :key="tag.id" :label="tag.name" :value="tag.id" />
              </el-select>
            </div>
            <div class="filter-field">
              <label for="question-difficulty">难度</label>
              <el-select id="question-difficulty" v-model="filters.difficulty" placeholder="全部难度" clearable @change="applyFilters">
                <el-option v-for="level in difficulties" :key="level" :label="difficultyLabel(level)" :value="level" />
              </el-select>
            </div>
            <span class="filter-help">选择后自动更新列表</span>
          </div>
        </form>

        <div class="list-context">
          <span class="secondary-copy">点击题目，展开完整内容与解析</span>
          <div class="context-actions">
            <el-button link :icon="RefreshRight" @click="resetFilters">重置</el-button>
            <el-select v-model="sortChoice" class="sort-select" aria-label="题目排序" @change="applyFilters">
              <el-option v-for="option in sortOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </div>
        </div>
        <div v-if="activeFilters.length" class="active-filters" aria-label="已应用的筛选条件">
          <span class="secondary-copy">已筛选</span>
          <el-tag v-for="item in activeFilters" :key="item.key" closable type="info" @close="removeFilter(item.key)">{{ item.label }}</el-tag>
        </div>

        <el-alert v-if="loadError" class="list-error" type="error" :closable="false" show-icon title="题库加载失败">
          <template #default>请检查网络或后端服务。<el-button link type="primary" @click="fetchList">重新加载</el-button></template>
        </el-alert>

        <div class="list-results" v-loading="loading" :aria-busy="loading">
          <el-table
            class="list-table" :data="questions" row-key="id" :expand-row-keys="[...expandedRowKeys]"
            :empty-text="loadError ? '暂时无法获取题目' : '没有符合条件的题目'"
          >
            <el-table-column type="expand" width="1" class-name="question-expand-cell">
              <template #default="{ row }">
                <QuestionDetails :id="'question-detail-table-' + row.id" :question="row" />
              </template>
            </el-table-column>
            <el-table-column label="序号" width="56" align="center">
              <template #default="{ $index }"><span class="secondary-copy tabular">{{ (page - 1) * pageSize + $index + 1 }}</span></template>
            </el-table-column>
            <el-table-column label="题目内容" min-width="300">
              <template #default="{ row }">
                <QuestionSummary :question="row" :expanded="expandedRowKeys.has(row.id)" :controls-id="'question-detail-table-' + row.id" @toggle="toggleExpand(row)" />
              </template>
            </el-table-column>
            <el-table-column label="题型" width="72" align="center">
              <template #default="{ row }">
                <span class="question-kind">{{ questionTypeLabel(row.question_type) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="难度" width="80" align="center">
              <template #default="{ row }">
                <el-tag class="difficulty-tag" size="small" :type="difficultyTone(row.difficulty)">{{ difficultyLabel(row.difficulty) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="使用情况" width="148">
              <template #default="{ row }">
                <div class="cell-stack tabular">
                  <span>使用 {{ row.usage_count ?? 0 }} 次</span>
                  <span class="secondary-copy">更新 {{ listDateTime(row.updated_at || row.created_at).date }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="store.canManage" label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" @click="$router.push('/questions/' + row.id + '/edit')">编辑</el-button>
                <el-popconfirm title="确认删除这道题目？" @confirm="remove(row.id)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <div class="compact-list">
            <article v-for="(row, index) in questions" :key="row.id" class="compact-item">
              <div class="compact-topline">
                <span class="compact-index">题目 {{ (page - 1) * pageSize + index + 1 }}</span>
                <span class="question-kind">{{ questionTypeLabel(row.question_type) }}</span>
                <el-tag class="difficulty-tag" size="small" :type="difficultyTone(row.difficulty)">{{ difficultyLabel(row.difficulty) }}</el-tag>
              </div>
              <QuestionSummary :question="row" :expanded="expandedRowKeys.has(row.id)" :controls-id="'question-detail-compact-' + row.id" @toggle="toggleExpand(row)" />
              <div class="compact-footer">
                <span class="secondary-copy tabular">使用 {{ row.usage_count ?? 0 }} 次 · 更新 {{ listDateTime(row.updated_at || row.created_at).date }}</span>
                <div v-if="store.canManage">
                  <el-button link type="primary" @click="$router.push('/questions/' + row.id + '/edit')">编辑</el-button>
                  <el-popconfirm title="确认删除这道题目？" @confirm="remove(row.id)">
                    <template #reference><el-button link type="danger">删除</el-button></template>
                  </el-popconfirm>
                </div>
              </div>
              <QuestionDetails v-if="expandedRowKeys.has(row.id)" :id="'question-detail-compact-' + row.id" class="compact-details" :question="row" />
            </article>
            <el-empty v-if="!questions.length && !loading && !loadError" description="没有符合条件的题目，试试调整筛选条件" :image-size="72" />
          </div>
        </div>

        <div class="list-pagination">
          <span class="secondary-copy" aria-live="polite">共 {{ total }} 道题目</span>
          <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" :pager-count="5" layout="sizes, prev, pager, next" @current-change="fetchList" @size-change="handlePageSizeChange" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { ArrowDown, Download, Filter, Plus, RefreshRight, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { categoryApi, questionApi, tagApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { createUploadForm, downloadBlob } from '@/utils/download'
import { difficultyLabel, difficultyTone, listDateTime, questionTypeLabel } from '@/utils/listPresentation'
import QuestionSummary from '@/components/lists/QuestionSummary.vue'
import QuestionDetails from '@/components/lists/QuestionDetails.vue'
import '@/styles/managementLists.css'

const store = useUserStore()
const loading = ref(false)
const loadError = ref(false)
const exporting = ref(false)
const questions = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const allCategories = ref([])
const allTags = ref([])
const expandedRowKeys = ref(new Set())
const advancedOpen = ref(false)
const questionTypes = ['single', 'multiple', 'truefalse', 'essay']
const difficulties = ['easy', 'medium', 'hard']
let loadRequestId = 0
let disposed = false

const defaultFilters = () => ({
  keyword: '', category_id: null, difficulty: null, question_type: null,
  tag_id: null, sort_by: 'created_at', sort_order: 'desc',
})
const filters = ref(defaultFilters())
const appliedFilters = ref(defaultFilters())
const sortOptions = [
  { value: 'created_at:desc', label: '创建时间：从新到旧' },
  { value: 'created_at:asc', label: '创建时间：从旧到新' },
  { value: 'updated_at:desc', label: '更新时间：从新到旧' },
  { value: 'updated_at:asc', label: '更新时间：从旧到新' },
  { value: 'usage_count:desc', label: '使用次数：从多到少' },
  { value: 'usage_count:asc', label: '使用次数：从少到多' },
  { value: 'difficulty:desc', label: '难度：降序' },
  { value: 'difficulty:asc', label: '难度：升序' },
  { value: 'title:asc', label: '题目名称：升序' },
  { value: 'title:desc', label: '题目名称：降序' },
]
const sortChoice = computed({
  get: () => filters.value.sort_by + ':' + filters.value.sort_order,
  set: (value) => { [filters.value.sort_by, filters.value.sort_order] = value.split(':') },
})
const flatCategories = computed(() => {
  const result = []
  function walk(items, prefix = '') {
    items.forEach((item) => {
      result.push({ id: item.id, label: prefix + item.name })
      if (item.children?.length) walk(item.children, prefix + item.name + ' / ')
    })
  }
  walk(allCategories.value)
  return result
})
const activeFilters = computed(() => {
  const current = appliedFilters.value
  return [
    { key: 'keyword', label: '搜索：' + current.keyword },
    { key: 'category_id', label: '分类：' + (flatCategories.value.find(item => item.id === current.category_id)?.label || current.category_id) },
    { key: 'question_type', label: '题型：' + questionTypeLabel(current.question_type) },
    { key: 'tag_id', label: '标签：' + (allTags.value.find(item => item.id === current.tag_id)?.name || current.tag_id) },
    { key: 'difficulty', label: '难度：' + difficultyLabel(current.difficulty) },
  ].filter(item => current[item.key] != null && current[item.key] !== '')
})
const advancedCount = computed(() => activeFilters.value.filter(item => ['tag_id', 'difficulty'].includes(item.key)).length)

function toggleExpand(row) {
  const next = new Set(expandedRowKeys.value)
  if (next.has(row.id)) next.delete(row.id)
  else next.add(row.id)
  expandedRowKeys.value = next
}

function buildQueryParams() {
  const current = appliedFilters.value
  const params = { page: page.value, size: pageSize.value, sort_by: current.sort_by, sort_order: current.sort_order }
  for (const key of ['keyword', 'category_id', 'difficulty', 'question_type', 'tag_id']) {
    if (current[key] != null && current[key] !== '') params[key] = current[key]
  }
  return params
}

async function fetchList() {
  const requestId = ++loadRequestId
  loading.value = true
  loadError.value = false
  try {
    const result = await questionApi.list(buildQueryParams())
    if (requestId !== loadRequestId || disposed) return
    questions.value = result.items || []
    total.value = result.total || 0
    expandedRowKeys.value = new Set()
  } catch {
    if (requestId === loadRequestId && !disposed) loadError.value = true
  } finally {
    if (requestId === loadRequestId && !disposed) loading.value = false
  }
}

function applyFilters() {
  filters.value.keyword = filters.value.keyword.trim()
  appliedFilters.value = { ...filters.value }
  page.value = 1
  fetchList()
}

function removeFilter(key) {
  filters.value[key] = defaultFilters()[key]
  appliedFilters.value = { ...appliedFilters.value, [key]: defaultFilters()[key] }
  page.value = 1
  fetchList()
}

function resetFilters() {
  filters.value = defaultFilters()
  appliedFilters.value = defaultFilters()
  page.value = 1
  fetchList()
}

function handlePageSizeChange() {
  page.value = 1
  fetchList()
}

async function downloadQuestions(format) {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await questionApi.export({ ...buildQueryParams(), format })
    downloadBlob(blob, 'questions_export.' + format)
  } finally {
    exporting.value = false
  }
}

async function uploadQuestions(options) {
  try {
    const result = await questionApi.import(createUploadForm(options.file))
    options.onSuccess?.(result)
    const firstError = result.errors?.[0]
    if (firstError) {
      ElMessage.warning('已导入 ' + result.created + ' 条，更新 ' + result.updated + ' 条；第 ' + firstError.row + ' 行失败：' + firstError.message)
    } else {
      ElMessage.success('导入完成：新增 ' + result.created + ' 条，更新 ' + result.updated + ' 条')
    }
    page.value = 1
    await fetchList()
  } catch (error) {
    options.onError?.(error)
    throw error
  }
}

async function remove(id) {
  await questionApi.remove(id)
  ElMessage.success('删除成功')
  await fetchList()
  if (!questions.value.length && page.value > 1 && !loadError.value) {
    page.value--
    await fetchList()
  }
}

onMounted(async () => {
  const [categories, tags] = await Promise.all([
    categoryApi.list().catch(() => []), tagApi.list().catch(() => []),
  ])
  if (disposed) return
  allCategories.value = categories
  allTags.value = tags
  fetchList()
})
onBeforeUnmount(() => { disposed = true; loadRequestId++ })
</script>

<style scoped>
.button-arrow { margin-left: 7px; font-size: 12px; }
.question-kind { color: var(--el-text-color-regular); font-size: 13px; white-space: nowrap; }
.difficulty-tag { height: 24px; padding: 0 8px; font-size: 12px; font-weight: 500; }
:deep(.list-table .el-table__expanded-cell) { padding: 0 14px 16px 56px !important; }
.question-filters { margin: 0; }
.filter-primary { display: grid; grid-template-columns: minmax(230px, 1fr) 160px 130px auto; gap: 16px; align-items: end; }
.filter-field { display: flex; flex-direction: column; gap: 7px; min-width: 0; }
.filter-field label { font-size: 12px; color: var(--app-text-secondary); }
.filter-field .el-select { width: 100%; }
.search-control { display: flex; gap: 8px; min-width: 0; }
.search-control .el-input { min-width: 0; }
.search-control .el-button { margin: 0; }
.more-filters { margin: 0; height: 38px; }
.filter-count { margin-left: 7px; color: var(--app-accent); }
.filter-advanced { display: grid; grid-template-columns: minmax(0, 240px) minmax(0, 160px) 1fr; align-items: end; gap: 16px; padding: 16px; margin-top: 16px; border: 1px solid var(--app-border); border-radius: 10px; background: var(--app-subtle); }
.filter-help { color: var(--app-text-secondary); font-size: 12px; padding-bottom: 9px; }
.list-context { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 18px 0; }
.context-actions { display: flex; align-items: center; gap: 14px; }
.context-actions .el-button { font-size: 12px; color: var(--app-text-secondary); }
.sort-select { width: 190px; }
.sort-select :deep(.el-select__wrapper) { font-size: 12px; min-height: 34px; }
.active-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding-bottom: 16px; }
.active-filters :deep(.el-tag) { max-width: 100%; height: auto; min-height: 26px; white-space: normal; }
.active-filters :deep(.el-tag__content) { overflow-wrap: anywhere; min-width: 0; }
:deep(.question-expand-cell .cell) { padding: 0 !important; }
:deep(.question-expand-cell .el-table__expand-icon) { display: none; }
@container management (max-width: 780px) {
  .filter-primary { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; }
  .search-field { grid-column: 1 / -1; }
  .more-filters { justify-self: start; grid-column: 1 / -1; }
  .filter-advanced { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; padding: 12px; }
  .filter-help { grid-column: 1 / -1; padding: 0; }
  .list-context { border-bottom: 1px solid var(--app-border); margin-bottom: 16px; }
  .context-actions { margin-left: auto; }
}
@media (max-width: 767px) {
  .header-actions { width: 100%; gap: 6px; }
  .header-actions :deep(.el-button) { padding-left: 9px; padding-right: 9px; white-space: nowrap; }
}
</style>
