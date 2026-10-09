<template>
  <el-row :gutter="20" style="align-items:stretch">
    <el-col :span="12" style="display:flex;flex-direction:column">
      <el-card header="题目分类管理" style="flex:1">
        <el-button type="primary" :icon="Plus" @click="openDialog(null)" style="margin-bottom:12px">新建分类</el-button>
        <el-tree
          :data="categories"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          default-expand-all
          draggable
          :allow-drop="allowDrop"
          @node-drop="onNodeDrop"
          @node-click="onNodeClick"
        >
          <template #default="{ node, data }">
            <div class="tree-node">
              <span>
                {{ node.label }}
                <el-tag size="small" type="info" style="margin-left:6px">{{ data.question_count ?? 0 }}题</el-tag>
              </span>
              <div @click.stop>
                <el-button link type="primary" size="small" @click="openDialog(data)">编辑</el-button>
                <el-button link type="success" size="small" @click="openDialog(null, data.id)">添加子分类</el-button>
                <el-popconfirm title="确认删除？" @confirm="removeCategory(data.id)">
                  <template #reference><el-button link type="danger" size="small">删除</el-button></template>
                </el-popconfirm>
              </div>
            </div>
          </template>
        </el-tree>
      </el-card>
    </el-col>
    <el-col :span="12" style="display:flex;flex-direction:column">
      <el-card header="标签管理">
        <div style="display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap">
          <el-input v-model="newTagName" placeholder="标签名称" style="width:160px" />
          <el-color-picker v-model="newTagColor" />
          <el-button type="primary" @click="createTag">添加标签</el-button>
        </div>
        <div style="display:flex;flex-wrap:wrap;gap:8px">
          <el-tag
            v-for="t in tags" :key="t.id"
            class="tag-item"
            :class="{ active: selectedTag?.id === t.id }"
            :style="{ background: t.color+'22', color: t.color, borderColor: t.color }"
            @click="selectTag(t)"
          >
            {{ t.name }}
            <el-popconfirm title="确认删除该标签？" @confirm="removeTag(t.id)">
              <template #reference>
                <span class="tag-delete" @click.stop>
                  <el-icon><Close /></el-icon>
                </span>
              </template>
            </el-popconfirm>
          </el-tag>
        </div>
      </el-card>

      <el-card class="tag-preview-card" v-loading="tagQuestionsLoading">
        <template #header>
          <div class="tag-preview-header">
            <span>标签关联题目预览</span>
            <el-tag
              v-if="selectedTag"
              size="small"
              :style="{ background: selectedTag.color+'22', color: selectedTag.color, borderColor: selectedTag.color }"
            >
              {{ selectedTag.name }}
            </el-tag>
          </div>
        </template>
        <el-empty v-if="!selectedTag" description="点击上方标签查看关联题目" />
        <el-empty v-else-if="!tagQuestionsLoading && !tagQuestions.length" description="该标签暂未关联题目" />
        <template v-else>
          <div class="preview-summary">
            共 {{ tagQuestionTotal }} 道题，按更新时间展示最近 {{ tagQuestions.length }} 道
          </div>
          <el-table :data="tagQuestions" size="small" border stripe>
            <el-table-column prop="title" label="题目" min-width="220" show-overflow-tooltip />
            <el-table-column prop="question_type" label="题型" width="82">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTagType(row.question_type)">{{ typeLabel(row.question_type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="difficulty" label="难度" width="76">
              <template #default="{ row }">
                <el-tag size="small" :type="diffTagType(row.difficulty)">{{ diffLabel(row.difficulty) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="category_name" label="分类" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.category_name || '-' }}</template>
            </el-table-column>
            <el-table-column prop="usage_count" label="使用" width="62" />
          </el-table>
        </template>
      </el-card>
    </el-col>
  </el-row>

  <el-card v-if="selectedCategory" style="margin-top:20px" v-loading="questionsLoading">
    <template #header>
      <span>「{{ selectedCategory.name }}」的题目（共 {{ selectedCategory.question_count ?? 0 }} 题）</span>
    </template>
    <el-empty v-if="!questionsLoading && !categoryQuestions.length" description="该分类暂无题目" />
    <el-table v-else :data="categoryQuestions" size="small" border stripe>
      <el-table-column type="index" width="50" />
      <el-table-column prop="title" label="题目" show-overflow-tooltip />
      <el-table-column prop="question_type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTagType(row.question_type)">{{ typeLabel(row.question_type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="difficulty" label="难度" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="diffTagType(row.difficulty)">{{ diffLabel(row.difficulty) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="editingCat ? '编辑分类' : '新建分类'" width="400px">
    <el-form :model="catForm" label-width="80px">
      <el-form-item label="名称">
        <el-input v-model="catForm.name" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="catForm.description" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveCategory">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { categoryApi, tagApi, questionApi } from '@/api'

const categories = ref([])
const tags = ref([])
const dialogVisible = ref(false)
const editingCat = ref(null)
const parentIdForNew = ref(null)
const catForm = ref({ name: '', description: '' })
const newTagName = ref('')
const newTagColor = ref('#409EFF')
const selectedCategory = ref(null)
const categoryQuestions = ref([])
const questionsLoading = ref(false)
const selectedTag = ref(null)
const tagQuestions = ref([])
const tagQuestionTotal = ref(0)
const tagQuestionsLoading = ref(false)

async function loadCategories() {
  categories.value = await categoryApi.list().catch(() => [])
}
async function loadTags() {
  tags.value = await tagApi.list().catch(() => [])
  if (selectedTag.value) {
    const latest = tags.value.find((tag) => tag.id === selectedTag.value.id)
    if (latest) selectedTag.value = latest
  }
}

function openDialog(cat, parentId = null) {
  editingCat.value = cat
  parentIdForNew.value = parentId
  catForm.value = { name: cat?.name || '', description: cat?.description || '' }
  dialogVisible.value = true
}

async function saveCategory() {
  if (!catForm.value.name) return ElMessage.warning('请输入分类名称')
  if (editingCat.value) {
    await categoryApi.update(editingCat.value.id, catForm.value)
  } else {
    await categoryApi.create({ ...catForm.value, parent_id: parentIdForNew.value })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadCategories()
}

async function removeCategory(id) {
  await categoryApi.remove(id)
  ElMessage.success('删除成功')
  if (selectedCategory.value?.id === id) selectedCategory.value = null
  loadCategories()
}

async function createTag() {
  if (!newTagName.value) return ElMessage.warning('请输入标签名称')
  await tagApi.create(newTagName.value, newTagColor.value)
  newTagName.value = ''
  ElMessage.success('标签已添加')
  loadTags()
}

async function removeTag(id) {
  await tagApi.remove(id)
  if (selectedTag.value?.id === id) {
    selectedTag.value = null
    tagQuestions.value = []
    tagQuestionTotal.value = 0
  }
  loadTags()
}

async function selectTag(tag) {
  selectedTag.value = tag
  tagQuestionsLoading.value = true
  try {
    const res = await questionApi.list({
      tag_id: tag.id,
      page: 1,
      size: 8,
      sort_by: 'updated_at',
      sort_order: 'desc',
    }).catch(() => ({ items: [], total: 0 }))
    tagQuestions.value = res.items || []
    tagQuestionTotal.value = res.total || 0
  } finally {
    tagQuestionsLoading.value = false
  }
}

async function onNodeClick(data) {
  selectedCategory.value = data
  questionsLoading.value = true
  const res = await questionApi.list({ category_id: data.id, size: 50 }).catch(() => ({ items: [] }))
  categoryQuestions.value = res.items || []
  questionsLoading.value = false
}

function allowDrop() {
  return true
}

function collectOrder(nodes, parentId = null) {
  const result = []
  nodes.forEach((node, index) => {
    result.push({ id: node.id, parent_id: parentId, sort_order: index })
    if (node.children?.length) result.push(...collectOrder(node.children, node.id))
  })
  return result
}

async function onNodeDrop() {
  const updates = collectOrder(categories.value)
  await categoryApi.reorder(updates).catch(() => ElMessage.error('排序保存失败'))
}

const TYPE_LABELS = { single: '单选', multiple: '多选', truefalse: '判断', essay: '问答' }
const DIFF_LABELS = { easy: '简单', medium: '中等', hard: '困难' }
const TYPE_TYPES = { single: '', multiple: 'success', truefalse: 'warning', essay: 'info' }
const DIFF_TYPES = { easy: 'success', medium: 'warning', hard: 'danger' }

function typeLabel(t) { return TYPE_LABELS[t] || t }
function diffLabel(d) { return DIFF_LABELS[d] || d }
function typeTagType(t) { return TYPE_TYPES[t] || '' }
function diffTagType(d) { return DIFF_TYPES[d] || '' }

onMounted(() => { loadCategories(); loadTags() })
</script>

<style scoped>
.tree-node { display: flex; justify-content: space-between; align-items: center; width: 100%; padding-right: 8px; }

.tag-item {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.tag-item:hover,
.tag-item.active {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.12);
}

.tag-delete {
  display: inline-flex;
  margin-left: 4px;
  cursor: pointer;
  vertical-align: middle;
}

.tag-preview-card {
  flex: 1;
  margin-top: 16px;
}

.tag-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-summary {
  margin-bottom: 10px;
  color: #64748b;
  font-size: 13px;
}
</style>
