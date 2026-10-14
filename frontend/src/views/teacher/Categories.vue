<template>
  <section ref="workspaceRef" class="taxonomy-workspace" :class="{ 'is-compact': compact }" aria-label="分类与标签管理">
    <header class="taxonomy-toolbar">
      <div class="taxonomy-tabs" role="tablist" aria-label="管理类型" @keydown="onTabKeydown">
        <button v-for="tab in tabs" :id="'taxonomy-tab-' + tab.key" :key="tab.key" type="button" role="tab"
          :aria-selected="mode === tab.key" :tabindex="mode === tab.key ? 0 : -1" aria-controls="taxonomy-panel"
          :class="{ active: mode === tab.key }" @click="mode = tab.key">
          {{ tab.label }}<span>{{ tab.key === 'category' ? categoryIndex.size : tags.length }}</span>
        </button>
      </div>
      <el-button type="primary" :icon="Plus" :disabled="mutating" @click="openCreate">新建{{ modeLabel }}</el-button>
    </header>

    <div id="taxonomy-panel" role="tabpanel" :aria-labelledby="'taxonomy-tab-' + mode" class="taxonomy-layout">
      <aside v-if="!compact" class="taxonomy-sidebar" :aria-label="modeLabel + '目录'">
        <TaxonomyBrowser v-bind="browserProps" v-on="browserEvents" />
      </aside>
      <div class="taxonomy-content">
        <button v-if="compact" type="button" class="directory-opener" @click="browserOpen = true">
          <el-icon><FolderOpened /></el-icon><span>选择{{ modeLabel }}</span><span class="opener-current">{{ selected?.name || '打开目录' }}</span><el-icon><ArrowDown /></el-icon>
        </button>

        <template v-if="selected">
          <header class="selection-heading">
            <div class="selection-copy">
              <div class="selection-path" :title="selectedPath">{{ selectedPath }}</div>
              <h2><span v-if="mode === 'tag'" class="selection-dot" :style="{ backgroundColor: safeTagColor(selected.color) }"></span>{{ selected.name }}</h2>
              <p v-if="mode === 'category' && selected.description" class="selection-description">{{ selected.description }}</p>
              <div class="selection-meta">
                <template v-if="mode === 'category'"><span>本级 <strong>{{ selected.question_count || 0 }}</strong> 题</span><span v-if="selected.children?.length">含子分类 {{ selected.subtreeCount }} 题</span></template>
                <span v-else>跨分类组织题目，不改变题目所属分类</span>
              </div>
            </div>
            <el-dropdown v-if="canManage(selected) || (mode === 'category' && canAdd(selected))" trigger="click" @command="handleAction($event, selected)">
              <button type="button" class="selection-more" :aria-label="'管理' + selected.name" :disabled="mutating"><el-icon><MoreFilled /></el-icon></button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="mode === 'category' && canManage(selected)" command="edit">编辑分类</el-dropdown-item>
                  <el-dropdown-item v-if="mode === 'category' && canAdd(selected)" command="add">添加子分类</el-dropdown-item>
                  <el-dropdown-item v-if="canManage(selected)" command="delete" :divided="mode === 'category'" class="taxonomy-danger">删除{{ modeLabel }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </header>

          <div class="questions-toolbar">
            <div><h3>关联题目<span v-if="!questionsLoading && !questionsError">{{ questionTotal }}</span></h3><p>{{ mode === 'category' ? '仅展示本级题目，点击题目查看答案与解析' : '点击题目，查看完整内容与解析' }}</p></div>
            <form class="taxonomy-question-search" @submit.prevent="searchQuestions">
              <el-input v-model="questionKeyword" :prefix-icon="Search" clearable placeholder="搜索关联题目" aria-label="搜索关联题目" @clear="searchQuestions" />
              <el-button native-type="submit" :icon="Search" aria-label="查询关联题目" />
            </form>
          </div>
          <div class="taxonomy-results" :aria-busy="questionsLoading" aria-live="polite">
            <el-skeleton v-if="questionsLoading" :rows="6" animated class="question-skeleton" />
            <div v-else-if="questionsError" class="content-state" role="alert"><el-icon><Warning /></el-icon><h3>题目加载失败</h3><p>请检查网络或后端服务后重试。</p><el-button @click="loadQuestions">重新加载</el-button></div>
            <div v-else-if="!questions.length" class="content-state"><el-icon><Document /></el-icon><h3>{{ appliedKeyword ? '没有匹配的题目' : '暂无关联题目' }}</h3><p>{{ appliedKeyword ? '换个关键词试试，或清除搜索条件。' : mode === 'category' ? '本级暂未添加题目，可以在左侧选择子分类。' : '在题库编辑题目时添加此标签，即可在这里查看。' }}</p><el-button v-if="appliedKeyword" @click="clearQuestionSearch">清除搜索</el-button></div>
            <article v-for="(question, index) in questionsLoading || questionsError ? [] : questions" :key="question.id" class="taxonomy-question">
              <div class="taxonomy-question-row">
                <span class="question-position">{{ (page - 1) * pageSize + index + 1 }}</span>
                <QuestionSummary :question="question" :expanded="expandedQuestions.has(question.id)" :controls-id="'taxonomy-question-' + question.id" @toggle="toggleQuestion(question.id)" />
                <div class="question-classification"><span>{{ questionTypeLabel(question.question_type) }}</span><el-tag size="small" :type="difficultyTone(question.difficulty)">{{ difficultyLabel(question.difficulty) }}</el-tag></div>
              </div>
              <QuestionDetails v-if="expandedQuestions.has(question.id)" :id="'taxonomy-question-' + question.id" :question="question" class="taxonomy-question-details" />
            </article>
          </div>
          <footer v-if="!questionsError && questionTotal > 0" class="questions-footer">
            <span>共 {{ questionTotal }} 题 · 按更新时间排序</span>
            <el-pagination :current-page="page" :page-size="pageSize" :total="questionTotal" :pager-count="5" :disabled="questionsLoading" layout="prev, pager, next" small @current-change="changePage" />
          </footer>
        </template>
        <div v-else class="content-state initial-state">
          <el-icon><Collection /></el-icon>
          <h2>{{ directoryLoading[mode] ? '正在加载' + modeLabel : directoryError[mode] ? modeLabel + '加载失败' : '从一个' + modeLabel + '开始' }}</h2>
          <p>{{ directoryError[mode] ? '无法读取目录，请稍后重试。' : '整理题目结构，让每一道题都有清晰的归属。' }}</p>
          <el-button v-if="directoryError[mode]" @click="loadDirectory(mode)">重新加载</el-button>
          <el-button v-else-if="!directoryLoading[mode]" :icon="Plus" @click="openCreate">新建{{ modeLabel }}</el-button>
        </div>
      </div>
    </div>
  </section>

  <el-drawer v-if="compact" v-model="browserOpen" direction="ltr" size="min(340px, calc(100vw - 24px))" :title="'选择' + modeLabel" class="taxonomy-drawer" destroy-on-close>
    <TaxonomyBrowser v-bind="browserProps" v-on="browserEvents" />
  </el-drawer>

  <el-dialog v-model="categoryDialog" :title="editingCategory ? '编辑分类' : parentId != null ? '添加子分类' : '新建分类'" width="440px" :close-on-click-modal="!mutating" :close-on-press-escape="!mutating" :show-close="!mutating">
    <el-form :model="categoryForm" label-position="top" @submit.prevent="saveCategory">
      <p class="dialog-context">上级分类：{{ parentId == null ? '无（顶级分类）' : categoryIndex.get(parentId)?.name || '所选分类' }}</p>
      <el-form-item label="分类名称" required><el-input v-model="categoryForm.name" maxlength="128" placeholder="例如：数据结构" aria-label="分类名称" :disabled="mutating" /></el-form-item>
      <el-form-item label="分类说明"><el-input v-model="categoryForm.description" type="textarea" :rows="3" maxlength="20000" placeholder="简要描述这个分类的内容（选填）" aria-label="分类说明" :disabled="mutating" /></el-form-item>
      <div class="dialog-actions"><el-button :disabled="mutating" @click="categoryDialog = false">取消</el-button><el-button type="primary" native-type="submit" :loading="mutating">保存分类</el-button></div>
    </el-form>
  </el-dialog>
  <el-dialog v-model="tagDialog" title="新建标签" width="420px" :close-on-click-modal="!mutating" :close-on-press-escape="!mutating" :show-close="!mutating">
    <el-form label-position="top" @submit.prevent="saveTag">
      <p class="dialog-context">用标签标记知识点，同一道题可以拥有多个标签。</p>
      <el-form-item label="标签名称" required><el-input v-model="tagForm.name" maxlength="64" placeholder="例如：重点、数据清洗" aria-label="标签名称" :disabled="mutating" /></el-form-item>
      <el-form-item label="标记颜色"><div class="tag-color-field"><el-color-picker v-model="tagForm.color" :predefine="tagColors" :disabled="mutating" aria-label="标签颜色" /><span class="tag-example"><i :style="{ backgroundColor: safeTagColor(tagForm.color) }"></i>{{ tagForm.name.trim() || '标签预览' }}</span></div></el-form-item>
      <div class="dialog-actions"><el-button :disabled="mutating" @click="tagDialog = false">取消</el-button><el-button type="primary" native-type="submit" :loading="mutating">创建标签</el-button></div>
    </el-form>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ArrowDown, Collection, Document, FolderOpened, MoreFilled, Plus, Search, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, questionApi, tagApi } from '@/api'
import { useUserStore } from '@/stores/user'
import QuestionDetails from '@/components/lists/QuestionDetails.vue'
import QuestionSummary from '@/components/lists/QuestionSummary.vue'
import TaxonomyBrowser from '@/components/taxonomy/TaxonomyBrowser.vue'
import { difficultyLabel, difficultyTone, questionTypeLabel } from '@/utils/listPresentation'
import { canAddToCategory, canManageTaxonomy, collectCategoryOrder, indexCategoryTree, safeTagColor } from '@/utils/taxonomy'

const store = useUserStore()
const tabs = [{ key: 'category', label: '分类' }, { key: 'tag', label: '标签' }]
const mode = ref('category')
const modeLabel = computed(() => mode.value === 'category' ? '分类' : '标签')
const categories = ref([])
const tags = ref([])
const categoryIndex = computed(() => indexCategoryTree(categories.value))
const selectedIds = reactive({ category: null, tag: null })
const selected = computed(() => mode.value === 'category' ? categoryIndex.value.get(selectedIds.category) : tags.value.find(tag => tag.id === selectedIds.tag))
const selectedPath = computed(() => mode.value === 'category' ? ['题目分类', ...(selected.value?.path.slice(0, -1).map(item => item.name) || [])].join(' / ') : '共享标签')
const directoryQuery = reactive({ category: '', tag: '' })
const directoryLoading = reactive({ category: true, tag: true })
const directoryError = reactive({ category: false, tag: false })
const expandedKeys = ref([])
const mutating = ref(false)
const workspaceRef = ref(null)
const compact = ref(false)
const browserOpen = ref(false)
let resizeObserver
let disposed = false
let questionRequest = 0
const directoryRequests = { category: 0, tag: 0 }
const canManage = item => canManageTaxonomy(item, store.user)
const canAdd = item => canAddToCategory(item, store.user)
const questions = ref([])
const questionTotal = ref(0)
const questionsLoading = ref(false)
const questionsError = ref(false)
const questionKeyword = ref('')
const appliedKeyword = ref('')
const expandedQuestions = ref(new Set())
const page = ref(1)
const pageSize = 10

const browserProps = computed(() => ({
  mode: mode.value, categories: categories.value, tags: tags.value, selectedId: selectedIds[mode.value],
  expandedKeys: expandedKeys.value, query: directoryQuery[mode.value], loading: directoryLoading[mode.value],
  error: directoryError[mode.value], busy: mutating.value, canManage, canAdd, allowDrag, allowDrop,
}))
const browserEvents = {
  select: item => { selectedIds[mode.value] = item.id; browserOpen.value = false },
  action: handleAction, drop: saveOrder,
  'update:query': value => { directoryQuery[mode.value] = value || '' },
  'update:expandedKeys': value => { expandedKeys.value = value },
  'expand-all': open => { expandedKeys.value = open ? [...categoryIndex.value.keys()] : [] },
  retry: () => loadDirectory(mode.value),
}

async function loadDirectory(kind) {
  const request = ++directoryRequests[kind]
  directoryLoading[kind] = true
  directoryError[kind] = false
  try {
    const data = await (kind === 'category' ? categoryApi.list() : tagApi.list())
    if (disposed || directoryRequests[kind] !== request) return
    if (kind === 'category') {
      categories.value = data
      if (!categoryIndex.value.has(selectedIds.category)) selectedIds.category = data[0]?.id ?? null
      const path = categoryIndex.value.get(selectedIds.category)?.path || []
      expandedKeys.value = [...new Set([...expandedKeys.value, ...path.map(item => item.id)])]
    } else {
      tags.value = data
      if (!data.some(item => item.id === selectedIds.tag)) selectedIds.tag = data[0]?.id ?? null
    }
  } catch {
    if (!disposed && directoryRequests[kind] === request) directoryError[kind] = true
  } finally {
    if (!disposed && directoryRequests[kind] === request) directoryLoading[kind] = false
  }
}

async function loadQuestions() {
  const request = ++questionRequest
  questions.value = []
  expandedQuestions.value = new Set()
  questionsError.value = false
  if (!selected.value) { questionTotal.value = 0; questionsLoading.value = false; return }
  questionsLoading.value = true
  try {
    const result = await questionApi.list({
      [mode.value === 'category' ? 'category_id' : 'tag_id']: selected.value.id,
      keyword: appliedKeyword.value || undefined, page: page.value, size: pageSize,
      sort_by: 'updated_at', sort_order: 'desc',
    })
    if (disposed || request !== questionRequest) return
    questions.value = result.items || []
    questionTotal.value = result.total || 0
    if (page.value > 1 && !questions.value.length && questionTotal.value > 0) {
      page.value = Math.ceil(questionTotal.value / pageSize)
      return loadQuestions()
    }
  } catch {
    if (!disposed && request === questionRequest) questionsError.value = true
  } finally {
    if (!disposed && request === questionRequest) questionsLoading.value = false
  }
}
watch(() => [mode.value, selectedIds[mode.value]], () => {
  questionKeyword.value = ''; appliedKeyword.value = ''; page.value = 1; questionTotal.value = 0
  loadQuestions()
})
function searchQuestions() { appliedKeyword.value = questionKeyword.value.trim(); page.value = 1; loadQuestions() }
function clearQuestionSearch() { questionKeyword.value = ''; searchQuestions() }
function changePage(value) { page.value = value; loadQuestions() }
function toggleQuestion(id) {
  const next = new Set(expandedQuestions.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedQuestions.value = next
}
async function onTabKeydown(event) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  mode.value = event.key === 'Home' ? 'category' : event.key === 'End' ? 'tag' : mode.value === 'category' ? 'tag' : 'category'
  await nextTick()
  document.getElementById('taxonomy-tab-' + mode.value)?.focus()
}

function siblings(node) { return node.parent?.data?.children || (Array.isArray(node.parent?.data) ? node.parent.data : categories.value) }
function allowDrag(node) {
  return !mutating.value && canManage(node.data) && (store.isAdmin || siblings(node).every(canManage))
}
function allowDrop(dragging, target, type) {
  if (!allowDrag(dragging)) return false
  const parent = type === 'inner' ? target.data : target.parent?.data
  if (parent?.id != null && !canAdd(parent)) return false
  const peers = type === 'inner' ? target.data.children || [] : siblings(target)
  return store.isAdmin || peers.every(canManage)
}
async function saveOrder() {
  if (mutating.value) return
  mutating.value = true
  try {
    const updates = collectCategoryOrder(categories.value).filter(item => canManage(categoryIndex.value.get(item.id)))
    await categoryApi.reorder(updates)
    ElMessage.success('分类顺序已保存')
  } catch {
    ElMessage.error('排序保存失败，正在恢复原有顺序')
  } finally {
    await loadDirectory('category')
    mutating.value = false
  }
}

const categoryDialog = ref(false)
const editingCategory = ref(null)
const parentId = ref(null)
const categoryForm = reactive({ name: '', description: '' })
const tagDialog = ref(false)
const tagForm = reactive({ name: '', color: '#0066cc' })
const tagColors = ['#0066cc', '#27834a', '#946200', '#7258b8', '#c53a36', '#6e6e73']
function openCategory(item = null, parent = null) {
  browserOpen.value = false
  editingCategory.value = item
  parentId.value = item?.parent_id ?? parent
  categoryForm.name = item?.name || ''
  categoryForm.description = item?.description || ''
  categoryDialog.value = true
}
function openCreate() {
  if (mode.value === 'category') openCategory()
  else { tagForm.name = ''; tagForm.color = '#0066cc'; tagDialog.value = true }
}
async function saveCategory() {
  if (mutating.value) return
  const name = categoryForm.name.trim()
  if (!name) return ElMessage.warning('请输入分类名称')
  mutating.value = true
  try {
    const data = { name, description: categoryForm.description.trim() }
    const saved = editingCategory.value ? await categoryApi.update(editingCategory.value.id, data) : await categoryApi.create({ ...data, parent_id: parentId.value })
    categoryDialog.value = false
    directoryQuery.category = ''
    await loadDirectory('category')
    if (categoryIndex.value.has(saved.id)) selectedIds.category = saved.id
    expandedKeys.value = [...new Set([...expandedKeys.value, ...(categoryIndex.value.get(saved.id)?.path.map(item => item.id) || [])])]
    ElMessage.success('分类已保存')
  } catch { /* API interceptor supplies validation or permission errors. */ }
  finally { mutating.value = false }
}
async function saveTag() {
  if (mutating.value) return
  const name = tagForm.name.trim()
  if (!name) return ElMessage.warning('请输入标签名称')
  mutating.value = true
  try {
    const saved = await tagApi.create(name, safeTagColor(tagForm.color))
    tagDialog.value = false
    directoryQuery.tag = ''
    await loadDirectory('tag')
    if (tags.value.some(item => item.id === saved.id)) selectedIds.tag = saved.id
    ElMessage.success('标签已创建')
  } catch { /* Keep the form open if creation fails. */ }
  finally { mutating.value = false }
}
async function handleAction(command, item) {
  if (mutating.value) return
  if (command === 'edit') return openCategory(item)
  if (command === 'add') return openCategory(null, item.id)
  if (command !== 'delete') return
  const kind = mode.value
  if (kind === 'category' && (item.children?.length || item.question_count > 0)) {
    return ElMessage.warning('该分类仍有子分类或题目，请先移动内容后再删除')
  }
  try {
    await ElMessageBox.confirm(
      kind === 'category' ? '确认删除分类「' + item.name + '」？此操作不可撤销。' : '确认删除标签「' + item.name + '」？将解除题目与此标签的关联，不会删除题目。',
      kind === 'category' ? '删除分类' : '删除标签',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch { return }
  if (mutating.value || disposed) return
  mutating.value = true
  try {
    await (kind === 'category' ? categoryApi.remove(item.id) : tagApi.remove(item.id))
    await loadDirectory(kind)
    ElMessage.success('删除成功')
  } catch { /* Preserve the selection when deletion is rejected. */ }
  finally { mutating.value = false }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(entries => {
    compact.value = entries[0].contentRect.width < 820
    if (!compact.value) browserOpen.value = false
  })
  resizeObserver.observe(workspaceRef.value)
  loadDirectory('category')
  loadDirectory('tag')
})
onUnmounted(() => { disposed = true; questionRequest++; resizeObserver?.disconnect() })
</script>

<style scoped>
.taxonomy-workspace { container: management / inline-size; border: 1px solid var(--app-border); border-radius: 14px; background: var(--app-surface); overflow: hidden; }
.taxonomy-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 74px; padding: 0 24px; border-bottom: 1px solid var(--app-border); }
.taxonomy-tabs { display: flex; gap: 28px; align-self: stretch; }
.taxonomy-tabs button { position: relative; display: flex; align-items: center; gap: 8px; padding: 0 3px; border: 0; background: transparent; color: var(--app-text-secondary); font-size: 14px; }
.taxonomy-tabs button.active { color: var(--app-accent); font-weight: 600; }
.taxonomy-tabs button.active::after { content: ''; position: absolute; height: 2px; background: var(--app-accent); bottom: -1px; left: 0; right: 0; }
.taxonomy-tabs button span { padding: 1px 6px; border-radius: 5px; background: var(--el-fill-color-light); color: var(--app-text-secondary); font-size: 11px; font-weight: 500; font-variant-numeric: tabular-nums; }
.taxonomy-tabs button.active span { color: var(--app-accent); background: var(--app-accent-soft); }
.taxonomy-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); min-height: 570px; }
.taxonomy-sidebar { background: #fbfcfd; border-right: 1px solid var(--app-border); min-width: 0; }
.taxonomy-content { padding: 28px; min-width: 0; }
.selection-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 24px; border-bottom: 1px solid var(--app-border); }
.selection-copy { min-width: 0; }
.selection-path { color: var(--app-text-secondary); font-size: 12px; margin-bottom: 9px; overflow-wrap: anywhere; }
.selection-copy h2 { display: flex; align-items: center; gap: 10px; margin: 0 0 9px; font-size: 23px; line-height: 1.4; letter-spacing: -.02em; overflow-wrap: anywhere; }
.selection-description { color: var(--app-text-secondary); font-size: 13px; margin: 4px 0 10px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.selection-meta { display: flex; flex-wrap: wrap; gap: 5px 18px; font-size: 12px; color: var(--app-text-secondary); }
.selection-meta strong { color: var(--app-text); font-weight: 600; }
.selection-dot { width: 10px; height: 10px; flex: 0 0 10px; border-radius: 50%; }
.selection-more { display: inline-flex; width: 32px; height: 32px; justify-content: center; align-items: center; background: transparent; border: 1px solid var(--app-border); border-radius: 7px; color: var(--app-text-secondary); }
.selection-more:hover { color: var(--app-accent); background: var(--app-accent-soft); }
.questions-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; padding: 22px 0 16px; }
.questions-toolbar h3 { font-size: 14px; letter-spacing: 0; margin: 0; }
.questions-toolbar h3 span { font-size: 12px; font-weight: 400; color: var(--app-text-secondary); margin-left: 8px; }
.questions-toolbar p { color: var(--app-text-secondary); font-size: 11px; margin: 5px 0 0; }
.taxonomy-question-search { display: flex; gap: 6px; width: 230px; max-width: 100%; }
.taxonomy-question-search .el-button { padding: 8px 10px; margin: 0; }
.taxonomy-results { min-height: 220px; }
.taxonomy-question { padding: 19px 0; border-top: 1px solid #eff0f3; }
.taxonomy-question-row { display: grid; grid-template-columns: 20px minmax(0, 1fr) auto; align-items: start; gap: 10px; }
.question-position { padding-top: 5px; color: #8c9098; font-size: 12px; font-variant-numeric: tabular-nums; }
.question-classification { display: flex; flex-wrap: nowrap; align-items: center; gap: 12px; white-space: nowrap; color: var(--app-text-secondary); font-size: 12px; padding-top: 4px; padding-left: 8px; }
.question-classification .el-tag { height: 23px; padding: 0 8px; font-size: 12px; }
.taxonomy-question-details { margin: 16px 0 0 30px; }
.questions-footer { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; padding-top: 20px; border-top: 1px solid var(--app-border); }
.questions-footer > span { font-size: 12px; color: var(--app-text-secondary); }
.questions-footer :deep(.el-pagination) { padding-top: 0; }
.question-skeleton { padding: 24px 0; }
.content-state { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 10px; min-height: 240px; padding: 32px 12px; text-align: center; }
.content-state > .el-icon { font-size: 27px; color: #9ca6b5; margin-bottom: 4px; }
.content-state h2, .content-state h3 { font-size: 16px; letter-spacing: 0; margin: 0; }
.content-state p { font-size: 13px; color: var(--app-text-secondary); margin: 0 0 8px; }
.initial-state { min-height: 420px; }
.is-compact .taxonomy-layout { grid-template-columns: minmax(0, 1fr); }
.directory-opener { width: 100%; display: flex; align-items: center; gap: 10px; padding: 11px 12px; border: 1px solid var(--app-border); border-radius: 8px; background: #fafbfc; color: var(--app-text); font-size: 13px; margin-bottom: 22px; text-align: left; }
.directory-opener > span:not(.opener-current) { white-space: nowrap; }
.opener-current { flex: 1; min-width: 0; color: var(--app-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: right; }
.dialog-context { font-size: 13px; color: var(--app-text-secondary); margin: 0 0 22px; overflow-wrap: anywhere; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 24px; }
.dialog-actions .el-button + .el-button { margin-left: 0; }
.tag-color-field { display: flex; align-items: center; gap: 16px; min-width: 0; }
.tag-example { display: flex; align-items: center; gap: 8px; font-size: 13px; padding: 2px 10px; border: 1px solid var(--app-border); border-radius: 6px; max-width: 250px; overflow-wrap: anywhere; }
.tag-example i { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 8px; }
.taxonomy-danger { color: var(--el-color-danger); }
@container management (max-width: 1000px) {
  .taxonomy-content { padding: 24px; }
  .taxonomy-question-row { grid-template-columns: 20px minmax(0, 1fr); }
  .question-classification { grid-column: 2; padding: 0 0 0 25px; }
}
@container management (max-width: 560px) {
  .taxonomy-toolbar { min-height: 66px; padding: 0 16px; gap: 10px; }
  .taxonomy-tabs { gap: 18px; }
  .taxonomy-toolbar > .el-button { padding: 9px 11px; }
  .taxonomy-content { padding: 20px 16px; }
  .selection-copy h2 { font-size: 21px; }
  .taxonomy-question-search { width: 100%; }
  .taxonomy-question-details { margin-left: 0; }
  .questions-footer { flex-direction: column; align-items: flex-start; }
}
:global(.taxonomy-drawer .el-drawer__header) { margin-bottom: 0; padding: 20px 22px; border-bottom: 1px solid var(--app-border); }
:global(.taxonomy-drawer .el-drawer__body) { padding: 0; }
</style>
