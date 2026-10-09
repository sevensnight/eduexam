<template>
  <el-card :header="isEdit ? '编辑题目' : '新建题目'">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" style="max-width:800px">
      <el-form-item label="题目类型" prop="question_type">
        <el-radio-group v-model="form.question_type">
          <el-radio value="single">单选题</el-radio>
          <el-radio value="multiple">多选题</el-radio>
          <el-radio value="truefalse">判断题</el-radio>
          <el-radio value="essay">简答题</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="题目内容" prop="title">
        <el-input v-model="form.title" type="textarea" :rows="3" placeholder="输入题目内容" />
      </el-form-item>
      <el-form-item label="难度" prop="difficulty">
        <el-radio-group v-model="form.difficulty">
          <el-radio value="easy">简单</el-radio>
          <el-radio value="medium">中等</el-radio>
          <el-radio value="hard">困难</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="所属分类">
        <el-select v-model="form.category_id" placeholder="选择分类" clearable style="width:240px">
          <el-option v-for="c in flatCategories" :key="c.id" :label="c.label" :value="c.id" />
        </el-select>
      </el-form-item>

      <!-- 选择题选项 -->
      <template v-if="form.question_type === 'single' || form.question_type === 'multiple'">
        <el-form-item label="选项">
          <div v-for="(opt, idx) in options" :key="idx" style="display:flex;align-items:center;gap:12px;margin-bottom:8px">
            <el-tag style="flex-shrink:0;width:32px;text-align:center;line-height:32px;margin-left:8px">{{ String.fromCharCode(65 + idx) }}</el-tag>
            <el-input v-model="options[idx]" placeholder="选项内容" style="flex:1" />
            <el-button circle :icon="Close" @click="options.splice(idx, 1)" type="danger" plain size="small" style="flex-shrink:0;margin-left:-8px" />
          </div>
          <div style="margin-top:4px;margin-left:52px">
            <el-button @click="options.push('')" :icon="Plus">添加选项</el-button>
          </div>
        </el-form-item>
        <el-form-item label="正确答案" prop="answer">
          <template v-if="form.question_type === 'single'">
            <el-radio-group v-model="form.answer">
              <el-radio v-for="(_, idx) in options" :key="idx" :value="String.fromCharCode(65 + idx)">
                {{ String.fromCharCode(65 + idx) }}
              </el-radio>
            </el-radio-group>
          </template>
          <template v-else>
            <el-checkbox-group v-model="multiAnswers">
              <el-checkbox v-for="(_, idx) in options" :key="idx" :value="String.fromCharCode(65 + idx)">
                {{ String.fromCharCode(65 + idx) }}
              </el-checkbox>
            </el-checkbox-group>
          </template>
        </el-form-item>
      </template>

      <!-- 判断题 -->
      <el-form-item v-if="form.question_type === 'truefalse'" label="正确答案" prop="answer">
        <el-radio-group v-model="form.answer">
          <el-radio value="True">正确</el-radio>
          <el-radio value="False">错误</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 简答题参考答案 -->
      <el-form-item v-if="form.question_type === 'essay'" label="参考答案" prop="answer">
        <el-input v-model="form.answer" type="textarea" :rows="4" placeholder="参考答案（用于评分参考）" />
      </el-form-item>

      <el-form-item label="解析">
        <el-input v-model="form.explanation" type="textarea" :rows="2" placeholder="题目解析（可选）" />
      </el-form-item>
      <el-form-item label="标签">
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <el-select v-model="form.tag_ids" multiple placeholder="选择标签" style="flex:1;min-width:200px">
            <el-option v-for="t in allTags" :key="t.id" :label="t.name" :value="t.id">
              <el-tag size="small" :style="{ background: t.color+'22', color: t.color, borderColor: t.color }">{{ t.name }}</el-tag>
            </el-option>
          </el-select>
          <el-popover placement="bottom" :width="260" trigger="click" v-model:visible="newTagPopover">
            <template #reference>
              <el-button :icon="Plus">新建标签</el-button>
            </template>
            <div style="display:flex;flex-direction:column;gap:10px">
              <el-input v-model="newTagName" placeholder="标签名称" size="small" />
              <div style="display:flex;align-items:center;gap:8px">
                <span style="font-size:13px;color:#606266">颜色</span>
                <el-color-picker v-model="newTagColor" size="small" />
              </div>
              <el-button type="primary" size="small" @click="createNewTag">确认添加</el-button>
            </div>
          </el-popover>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">保存题目</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import { questionApi, categoryApi, tagApi } from '@/api'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const isEdit = computed(() => !!route.params.id)
const options = ref(['', '', '', ''])
const multiAnswers = ref([])
const allCategories = ref([])
const allTags = ref([])
const newTagPopover = ref(false)
const newTagName = ref('')
const newTagColor = ref('#409EFF')

async function createNewTag() {
  if (!newTagName.value) return
  const tag = await tagApi.create(newTagName.value, newTagColor.value)
  allTags.value = await tagApi.list().catch(() => allTags.value)
  if (tag?.id) form.value.tag_ids.push(tag.id)
  newTagName.value = ''
  newTagColor.value = '#409EFF'
  newTagPopover.value = false
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

watch(() => form.value.question_type, (type) => {
  if (type === 'multiple') {
    multiAnswers.value = []
    form.value.answer = ''
  }
})

watch(multiAnswers, (vals) => {
  form.value.answer = [...vals].sort().join(',')
})

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const payload = { ...form.value }
    if (['single', 'multiple'].includes(payload.question_type)) {
      payload.options = JSON.stringify(
        options.value.filter(Boolean).map((o, i) => `${String.fromCharCode(65 + i)}. ${o}`)
      )
    }
    if (isEdit.value) {
      await questionApi.update(route.params.id, payload)
      ElMessage.success('更新成功')
    } else {
      await questionApi.create(payload)
      ElMessage.success('创建成功')
    }
    router.push('/questions')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const [cats, tags] = await Promise.all([
    categoryApi.list().catch(() => []),
    tagApi.list().catch(() => []),
  ])
  allCategories.value = cats
  allTags.value = tags

  if (isEdit.value) {
    const q = await questionApi.get(route.params.id)
    form.value = {
      title: q.title, question_type: q.question_type, difficulty: q.difficulty,
      answer: q.answer, explanation: q.explanation || '', category_id: q.category_id,
      tag_ids: q.tags.map(t => t.id),
    }
    if (q.options) {
      try {
        const parsed = JSON.parse(q.options)
        options.value = parsed.map(o => o.replace(/^[A-Z]\.\s*/, ''))
      } catch {}
    }
    if (q.question_type === 'multiple' && q.answer) {
      multiAnswers.value = q.answer.split(',')
    }
  }
})
</script>
