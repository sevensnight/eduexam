<template>
  <div class="workflow-page question-editor">
    <div class="workflow-context"><el-button text :icon="ArrowLeft" @click="$router.push('/questions')">返回题库</el-button><span class="workflow-secondary">{{ isEdit ? '编辑题目' : '新建题目' }}</span></div>
    <el-skeleton v-if="pageLoading" :rows="10" animated />
    <el-result v-else-if="loadError" icon="error" title="题目编辑器加载失败" sub-title="请重新加载后再编辑，避免覆盖未加载的内容"><template #extra><el-button @click="initialize">重新加载</el-button></template></el-result>
    <el-form v-else ref="formRef" class="workflow-form" :model="form" :rules="rules" label-position="top" :disabled="loading" @submit.prevent="submit">
      <el-card>
        <template #header><div class="workflow-heading"><div><h2>题目内容</h2><p class="workflow-caption">设置题型、题干与正确答案</p></div></div></template>
        <el-form-item label="题目类型" prop="question_type"><el-radio-group v-model="form.question_type" @change="handleQuestionTypeChange"><el-radio value="single">单选题</el-radio><el-radio value="multiple">多选题</el-radio><el-radio value="truefalse">判断题</el-radio><el-radio value="essay">简答题</el-radio></el-radio-group></el-form-item>
        <el-form-item label="题目内容" prop="title"><el-input v-model="form.title" type="textarea" :autosize="{ minRows: 3, maxRows: 12 }" placeholder="输入完整题干" /></el-form-item>
        <template v-if="['single', 'multiple'].includes(form.question_type)">
          <el-form-item label="选项"><div class="question-options">
            <div v-for="(_, index) in options" :key="index" class="question-option">
              <span class="option-letter">{{ optionLetter(index) }}</span>
              <el-input v-model="options[index]" type="textarea" :autosize="{ minRows: 1, maxRows: 6 }" :aria-label="'选项 ' + optionLetter(index)" placeholder="输入选项内容" />
              <el-button text :icon="Close" :aria-label="'删除选项 ' + optionLetter(index)" :disabled="options.length <= 2" @click="removeOption(index)" />
            </div>
            <el-button :icon="Plus" :disabled="options.length >= 26" @click="options.push('')">添加选项</el-button>
          </div></el-form-item>
          <el-form-item label="正确答案" prop="answer">
            <el-radio-group v-if="form.question_type === 'single'" v-model="form.answer"><el-radio v-for="(_, index) in options" :key="index" :value="optionLetter(index)">{{ optionLetter(index) }}</el-radio></el-radio-group>
            <el-checkbox-group v-else v-model="multiAnswers"><el-checkbox v-for="(_, index) in options" :key="index" :value="optionLetter(index)">{{ optionLetter(index) }}</el-checkbox></el-checkbox-group>
          </el-form-item>
        </template>
        <el-form-item v-if="form.question_type === 'truefalse'" label="正确答案" prop="answer"><el-radio-group v-model="form.answer"><el-radio value="True">正确</el-radio><el-radio value="False">错误</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="form.question_type === 'essay'" label="参考答案" prop="answer"><el-input v-model="form.answer" type="textarea" :autosize="{ minRows: 3, maxRows: 12 }" placeholder="输入评分参考答案" /></el-form-item>
        <el-form-item label="题目解析（可选）"><el-input v-model="form.explanation" type="textarea" :autosize="{ minRows: 3, maxRows: 12 }" placeholder="说明解题思路、关键知识点或易错原因" /></el-form-item>
      </el-card>
      <el-card>
        <template #header><div class="workflow-heading"><div><h2>分类与标签</h2><p class="workflow-caption">便于检索、组卷和统计分析</p></div></div></template>
        <div class="form-grid">
          <el-form-item label="所属分类"><el-select v-model="form.category_id" placeholder="选择分类" clearable filterable><el-option v-for="category in flatCategories" :key="category.id" :label="category.label" :value="category.id" /></el-select></el-form-item>
          <el-form-item label="难度" prop="difficulty"><el-radio-group v-model="form.difficulty"><el-radio value="easy">简单</el-radio><el-radio value="medium">中等</el-radio><el-radio value="hard">困难</el-radio></el-radio-group></el-form-item>
          <el-form-item class="form-wide" label="标签">
            <div class="question-tags"><el-select v-model="form.tag_ids" multiple filterable placeholder="选择标签"><el-option v-for="tag in allTags" :key="tag.id" :label="tag.name" :value="tag.id" /></el-select>
              <el-popover placement="bottom-end" :width="260" trigger="click" v-model:visible="newTagPopover"><template #reference><el-button :icon="Plus">新建标签</el-button></template>
                <div class="new-tag-form"><el-input v-model="newTagName" placeholder="标签名称" :disabled="tagSaving" /><div><span>标签颜色</span><el-color-picker v-model="newTagColor" :disabled="tagSaving" /></div><el-button type="primary" :loading="tagSaving" @click="createNewTag">确认添加</el-button></div>
              </el-popover>
            </div>
          </el-form-item>
        </div>
      </el-card>
      <footer class="workflow-footer"><span class="workflow-secondary">保存后可在题库中检索与组卷</span><div class="workflow-actions"><el-button @click="$router.push('/questions')">取消</el-button><el-button type="primary" native-type="submit" :loading="loading">保存题目</el-button></div></footer>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Close, ArrowLeft } from '@element-plus/icons-vue'
import { questionApi, categoryApi, tagApi } from '@/api'
import {
  optionLetter,
  parseAnswerLetters,
  parseOptionList,
  remapAnswerLettersAfterRemoval,
  serializeOptions,
  validateChoiceQuestion,
} from '@/utils/questionEditor'
import '@/styles/workflow.css'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const pageLoading = ref(true)
const loadError = ref(false)
const tagSaving = ref(false)
let requestVersion = 0
let disposed = false
const isEdit = computed(() => !!route.params.id)
const options = ref(['', '', '', ''])
const multiAnswers = ref([])
const allCategories = ref([])
const allTags = ref([])
const newTagPopover = ref(false)
const newTagName = ref('')
const newTagColor = ref('#409EFF')
const previousQuestionType = ref('single')

async function createNewTag() {
  const name = newTagName.value.trim()
  if (!name || tagSaving.value) return
  tagSaving.value = true
  try {
    const tag = await tagApi.create(name, newTagColor.value)
    allTags.value = await tagApi.list().catch(() => allTags.value)
    if (tag?.id) {
      if (!allTags.value.some(item => item.id === tag.id)) allTags.value.push(tag)
      if (!form.value.tag_ids.includes(tag.id)) form.value.tag_ids.push(tag.id)
    }
    newTagName.value = ''
    newTagPopover.value = false
  } catch { /* 接口层已提示错误，保留输入以便重试。 */ }
  finally { tagSaving.value = false }
}

const form = ref({
  title: '', question_type: 'single', difficulty: 'medium',
  options: null, answer: '', explanation: '', category_id: null, tag_ids: [],
})

const rules = {
  title: [{ required: true, message: '请输入题目内容' }],
  question_type: [{ required: true }],
  difficulty: [{ required: true }],
  answer: [{ required: true, message: '请设置正确答案' }],
}

const flatCategories = computed(() => {
  const result = []
  function flatten(cats, prefix = '') {
    cats.forEach(c => {
      result.push({ id: c.id, label: prefix + c.name })
      if (c.children?.length) flatten(c.children, prefix + '  ')
    })
  }
  flatten(allCategories.value)
  return result
})

watch(multiAnswers, (vals) => {
  if (form.value.question_type === 'multiple') form.value.answer = [...vals].sort().join(',')
}, { deep: true })

function handleQuestionTypeChange(type) {
  const previous = previousQuestionType.value
  if (type === 'multiple') {
    multiAnswers.value = previous === 'single' && /^[A-Z]$/.test(form.value.answer)
      ? [form.value.answer]
      : []
    form.value.answer = multiAnswers.value.join(',')
  } else if (type === 'single') {
    const candidates = previous === 'multiple'
      ? multiAnswers.value
      : parseAnswerLetters(form.value.answer)
    form.value.answer = candidates[0] || ''
    multiAnswers.value = []
  } else {
    multiAnswers.value = []
    form.value.answer = ''
  }
  if (['single', 'multiple'].includes(type) && options.value.length < 2) {
    options.value = ['', '', '', '']
  }
  previousQuestionType.value = type
  formRef.value?.clearValidate('answer')
}

function removeOption(index) {
  if (options.value.length <= 2) {
    ElMessage.warning('选择题至少需要两个选项')
    return
  }
  const selected = form.value.question_type === 'multiple'
    ? multiAnswers.value
    : parseAnswerLetters(form.value.answer)
  options.value.splice(index, 1)
  const remapped = remapAnswerLettersAfterRemoval(selected, index)
  if (form.value.question_type === 'multiple') {
    multiAnswers.value = remapped
  } else {
    form.value.answer = remapped[0] || ''
  }
}

async function submit() {
  if (loading.value || pageLoading.value || loadError.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || loading.value) return
  const choiceError = validateChoiceQuestion(
    form.value.question_type,
    options.value,
    form.value.answer,
  )
  if (choiceError) {
    ElMessage.warning(choiceError)
    return
  }
  loading.value = true
  try {
    const payload = {
      ...form.value,
      category_id: form.value.category_id ?? null,
      tag_ids: [...form.value.tag_ids],
      options: null,
    }
    if (['single', 'multiple'].includes(payload.question_type)) {
      payload.options = serializeOptions(options.value)
    }
    if (isEdit.value) {
      await questionApi.update(route.params.id, payload)
      ElMessage.success('更新成功')
    } else {
      await questionApi.create(payload)
      ElMessage.success('创建成功')
    }
    router.push('/questions')
  } catch { /* 保存失败时保留编辑内容。 */ }
  finally {
    loading.value = false
  }
}

async function initialize() {
  const version = ++requestVersion
  pageLoading.value = true
  loadError.value = false
  try {
    const [cats, tags, question] = await Promise.all([
      categoryApi.list(), tagApi.list(),
      isEdit.value ? questionApi.get(route.params.id) : Promise.resolve(null),
    ])
    if (disposed || version !== requestVersion) return
    allCategories.value = cats
    allTags.value = tags
    form.value = question ? {
      title: question.title, question_type: question.question_type, difficulty: question.difficulty,
      answer: question.answer, explanation: question.explanation || '', category_id: question.category_id,
      tag_ids: (question.tags || []).map(tag => tag.id), options: null,
    } : { title: '', question_type: 'single', difficulty: 'medium', options: null, answer: '', explanation: '', category_id: null, tag_ids: [] }
    previousQuestionType.value = form.value.question_type
    const parsedOptions = parseOptionList(question?.options)
    options.value = parsedOptions.length ? parsedOptions : ['', '', '', '']
    multiAnswers.value = question?.question_type === 'multiple' ? parseAnswerLetters(question.answer) : []
  } catch { if (version === requestVersion && !disposed) loadError.value = true }
  finally { if (version === requestVersion && !disposed) pageLoading.value = false }
}
watch(() => route.params.id, initialize, { immediate: true })
onBeforeUnmount(() => { disposed = true; requestVersion++ })
</script>

<style scoped>
.question-editor > .el-form { display: grid; gap: 20px; min-width: 0; }
.question-editor :deep(.el-radio-group), .question-editor :deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 8px; }
.question-editor :deep(.el-radio), .question-editor :deep(.el-checkbox) { margin: 0; height: auto; min-height: 38px; padding: 7px 12px; border: 1px solid #d8e0eb; border-radius: 7px; background: #f8f9fb; }
.question-editor :deep(.el-radio.is-checked), .question-editor :deep(.el-checkbox.is-checked) { border-color: var(--app-selection-border); box-shadow: inset 0 0 0 1px var(--app-selection-border); background: var(--app-selection-bg); }
.question-editor :deep(.is-checked .el-radio__label), .question-editor :deep(.is-checked .el-checkbox__label) { color: var(--app-selection-ink); font-weight: 600; }
.question-editor :deep(.el-radio:has(input:focus-visible)), .question-editor :deep(.el-checkbox:has(input:focus-visible)) { outline: 3px solid #183e78; outline-offset: 2px; }
.question-options { display: grid; gap: 12px; width: 100%; min-width: 0; }
.question-options > .el-button { justify-self: start; margin: 0; }
.question-option { display: grid; grid-template-columns: 28px minmax(0,1fr) 32px; align-items: start; gap: 12px; }
.option-letter { padding-top: 9px; text-align: center; font-weight: 600; color: var(--app-text-secondary); }
.question-option > .el-button { padding: 0; width: 32px; }
.question-tags { display: flex; flex-wrap: wrap; gap: 12px; width: 100%; min-width: 0; }
.question-tags > .el-select { flex: 1 1 220px; }
.new-tag-form { display: grid; gap: 12px; }
.new-tag-form > div { display: flex; gap: 12px; align-items: center; }
@media (max-width: 540px) { .question-option { grid-template-columns: 20px minmax(0,1fr) 28px; gap: 8px; } }
</style>
