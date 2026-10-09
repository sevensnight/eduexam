<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>全部题目 <span class="list-count">{{ total }}</span></span>
          <div class="header-actions">
            <el-upload
              v-if="store.canManage"
              :show-file-list="false"
              accept=".csv,.xlsx"
              :http-request="uploadQuestions"
            >
              <el-button :icon="Upload">导入题目</el-button>
            </el-upload>
            <el-button v-if="store.canManage" :icon="Download" @click="downloadQuestions('xlsx')">导出 Excel</el-button>
            <el-button v-if="store.canManage" :icon="Download" @click="downloadQuestions('csv')">导出 CSV</el-button>
            <el-button v-if="store.canManage" type="primary" :icon="Plus" @click="$router.push('/questions/create')">
              新建题目
            </el-button>
          </div>
        </div>
      </template>

      <el-form inline :model="filters" @submit.prevent="fetchList">
        <el-form-item label="搜索">
          <el-input
            v-model="filters.keyword"
            placeholder="题干、答案、解析、分类、标签"
            clearable
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.category_id" placeholder="全部分类" clearable style="width: 160px">
            <el-option v-for="item in flatCategories" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="filters.tag_id" placeholder="全部标签" clearable style="width: 150px">
            <el-option v-for="tag in allTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="filters.difficulty" placeholder="全部难度" clearable style="width: 120px">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="filters.question_type" placeholder="全部题型" clearable style="width: 130px">
            <el-option label="单选题" value="single" />
            <el-option label="多选题" value="multiple" />
            <el-option label="判断题" value="truefalse" />
            <el-option label="简答题" value="essay" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="filters.sort_by" style="width: 150px">
            <el-option label="按创建时间" value="created_at" />
            <el-option label="按更新时间" value="updated_at" />
            <el-option label="按使用次数" value="usage_count" />
            <el-option label="按难度" value="difficulty" />
            <el-option label="按题目名称" value="title" />
          </el-select>
        </el-form-item>
        <el-form-item label="顺序">
          <el-select v-model="filters.sort_order" style="width: 110px">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
          <el-button :icon="RefreshRight" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="tableRef"
        :data="questions"
        :row-key="getRowKey"
        v-loading="loading"
        style="margin-top: 12px"
      >
        <el-table-column type="expand" width="1" class-name="hidden-expand-col">
          <template #default="{ row }">
            <div class="expand-panel">
              <div><span class="muted">正确答案：</span><span class="good-answer">{{ row.answer }}</span></div>
              <div v-if="row.explanation"><span class="muted">解析：</span>{{ row.explanation }}</div>
              <div v-if="row.options">
                <span class="muted">选项：</span>
                <span v-for="option in parseOptions(row.options)" :key="option" class="option-item">{{ option }}</span>
              </div>
              <div v-if="row.category_path"><span class="muted">分类路径：</span>{{ row.category_path }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="序号" width="64">
          <template #default="{ $index }">{{ (page - 1) * pageSize + $index + 1 }}</template>
        </el-table-column>

        <el-table-column label="题目内容" min-width="300">
          <template #default="{ row }">
            <el-tooltip
              :content="row.title"
              effect="dark"
              placement="top-start"
              :show-after="120"
              popper-class="question-preview-tooltip"
            >
              <button type="button" class="title-trigger" @click="toggleExpand(row)">
                <span class="title-text">{{ row.title }}</span>
              </button>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="分类" min-width="126">
          <template #default="{ row }">
            <span class="category-text">{{ row.category_name || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="题型" width="88">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.question_type)">{{ typeLabel(row.question_type) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="难度" width="76">
          <template #default="{ row }">
            <el-tag size="small" :type="diffTag(row.difficulty)">{{ diffLabel(row.difficulty) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="usage_count" label="使用次数" width="88" />

        <el-table-column label="标签" min-width="148">
          <template #default="{ row }">
            <el-tag
              v-for="tag in row.tags"
              :key="tag.id"
              size="small"
              :style="{ background: `${tag.color}22`, color: tag.color, borderColor: tag.color }"
              class="tag-chip"
            >
              {{ tag.name }}
            </el-tag>
            <span v-if="!row.tags?.length">-</span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="168">
          <template #default="{ row }">{{ formatTime(row.updated_at || row.created_at) }}</template>
        </el-table-column>

        <el-table-column v-if="store.canManage" label="操作" width="112" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/questions/${row.id}/edit`)">编辑</el-button>
            <el-popconfirm title="确认删除这道题目？" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end; display: flex"
        @current-change="fetchList"
        @size-change="handlePageSizeChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Download, Plus, RefreshRight, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { categoryApi, questionApi, tagApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { createUploadForm, downloadBlob } from '@/utils/download'

const store = useUserStore()
const tableRef = ref(null)
const loading = ref(false)
const questions = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const allCategories = ref([])
const allTags = ref([])
const expandedRowKeys = ref(new Set())

const filters = ref({
  keyword: '',
  category_id: null,
  difficulty: null,
  question_type: null,
  tag_id: null,
  sort_by: 'created_at',
  sort_order: 'desc',
})

const flatCategories = computed(() => {
  const result = []
  function walk(items, prefix = '') {
    items.forEach((item) => {
      result.push({ id: item.id, label: `${prefix}${item.name}` })
      if (item.children?.length) walk(item.children, `${prefix}${item.name} / `)
    })
  }
  walk(allCategories.value)
  return result
})

function getRowKey(row) {
  return row.id
}

function toggleExpand(row) {
  const isExpanded = expandedRowKeys.value.has(row.id)
  tableRef.value?.toggleRowExpansion(row, !isExpanded)
  if (isExpanded) {
    expandedRowKeys.value.delete(row.id)
  } else {
    expandedRowKeys.value.add(row.id)
  }
}

function buildQueryParams() {
  const params = {
    page: page.value,
    size: pageSize.value,
    sort_by: filters.value.sort_by,
    sort_order: filters.value.sort_order,
  }
  if (filters.value.keyword) params.keyword = filters.value.keyword
  if (filters.value.category_id) params.category_id = filters.value.category_id
  if (filters.value.difficulty) params.difficulty = filters.value.difficulty
  if (filters.value.question_type) params.question_type = filters.value.question_type
  if (filters.value.tag_id) params.tag_id = filters.value.tag_id
  return params
}

function typeTag(type) {
  return { single: '', multiple: 'warning', truefalse: 'success', essay: 'info' }[type] || ''
}

function typeLabel(type) {
  return { single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[type] || type
}

function diffTag(level) {
  return { easy: 'success', medium: 'warning', hard: 'danger' }[level] || 'info'
}

function diffLabel(level) {
  return { easy: '简单', medium: '中等', hard: '困难' }[level] || level
}

function parseOptions(raw) {
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

async function fetchList() {
  loading.value = true
  try {
    const result = await questionApi.list(buildQueryParams())
    questions.value = result.items
    total.value = result.total
    expandedRowKeys.value.clear()
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  fetchList()
}

function resetFilters() {
  filters.value = {
    keyword: '',
    category_id: null,
    difficulty: null,
    question_type: null,
    tag_id: null,
    sort_by: 'created_at',
    sort_order: 'desc',
  }
  page.value = 1
  fetchList()
}

function handlePageSizeChange() {
  page.value = 1
  fetchList()
}

async function downloadQuestions(format) {
  const blob = await questionApi.export({ ...buildQueryParams(), format })
  downloadBlob(blob, `questions_export.${format}`)
}

async function uploadQuestions(options) {
  try {
    const result = await questionApi.import(createUploadForm(options.file))
    options.onSuccess?.(result)
    const firstError = result.errors?.[0]
    if (firstError) {
      ElMessage.warning(`已导入 ${result.created} 条，更新 ${result.updated} 条；第 ${firstError.row} 行失败：${firstError.message}`)
    } else {
      ElMessage.success(`导入完成：新增 ${result.created} 条，更新 ${result.updated} 条`)
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
  fetchList()
}

onMounted(async () => {
  const [categories, tags] = await Promise.all([
    categoryApi.list().catch(() => []),
    tagApi.list().catch(() => []),
  ])
  allCategories.value = categories
  allTags.value = tags
  fetchList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.title-trigger {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: #1f2937;
  text-align: left;
  cursor: pointer;
}

.title-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-text {
  display: block;
  color: #475569;
}

.tag-chip {
  margin: 1px;
}

.expand-panel {
  padding: 12px 24px 12px 32px;
  background: #fafafa;
  font-size: 13px;
  line-height: 2;
}

.muted {
  color: #909399;
}

.good-answer {
  color: #67c23a;
  font-weight: 700;
}

.option-item {
  margin-right: 12px;
}

:deep(.hidden-expand-col .cell) {
  width: 0;
  padding: 0;
  overflow: hidden;
}

:deep(.el-table__expand-column) {
  width: 0 !important;
  min-width: 0 !important;
}

:deep(.el-table__expand-column .cell) {
  display: none;
}

:deep(.question-preview-tooltip) {
  max-width: 520px;
  line-height: 1.65;
  white-space: normal;
  word-break: break-word;
}
</style>
