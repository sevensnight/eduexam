<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>课程与选课管理</span>
        <el-button type="primary" :icon="Plus" @click="openForm()">新增课程</el-button>
      </div>
    </template>

    <el-table :data="courses" v-loading="loading" border stripe>
      <el-table-column prop="name" label="课程名称" min-width="180" />
      <el-table-column prop="teacher_name" label="授课教师" width="140" />
      <el-table-column prop="student_count" label="选课人数" width="100" align="center" />
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="success" @click="openEnroll(row)">选课学生</el-button>
          <el-popconfirm title="确认删除该课程？" @confirm="removeCourse(row.id)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑课程' : '新增课程'"
      width="520px"
      append-to-body
      destroy-on-close
      class="course-dialog"
    >
      <el-form :model="form" label-width="90px">
        <el-form-item label="课程名称" required>
          <el-input v-model="form.name" placeholder="例如：数据库系统" />
        </el-form-item>
        <el-form-item label="课程说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="课程简介、适用班级等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCourse">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="enrollVisible"
      title="维护选课学生"
      width="820px"
      append-to-body
      destroy-on-close
      class="course-dialog enroll-dialog"
    >
      <div class="dialog-body">
        <div class="enroll-toolbar">
          <div>
            <strong>{{ activeCourse?.name }}</strong>
            <span class="muted">已选择 {{ selectedStudentIds.length }} 人</span>
          </div>
          <el-select v-model="classFilter" clearable placeholder="按班级筛选" style="width:180px" @change="loadStudents">
            <el-option v-for="name in classes" :key="name" :label="name" :value="name" />
          </el-select>
        </div>
        <el-table
          ref="studentTableRef"
          :data="students"
          row-key="id"
          height="420"
          border
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="48" reserve-selection />
          <el-table-column prop="username" label="账号" width="130" />
          <el-table-column prop="real_name" label="姓名" width="130" />
          <el-table-column prop="class_name" label="班级" min-width="160" />
          <el-table-column prop="email" label="邮箱" min-width="220" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="enrollVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEnrollments">保存选课</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import { courseApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const courses = ref([])
const classes = ref([])
const students = ref([])
const formVisible = ref(false)
const enrollVisible = ref(false)
const activeCourse = ref(null)
const classFilter = ref('')
const selectedStudentIds = ref([])
const studentTableRef = ref()
const restoringSelection = ref(false)

const form = reactive({
  id: null,
  name: '',
  description: '',
})

async function loadCourses() {
  loading.value = true
  try {
    courses.value = await courseApi.list().catch(() => [])
  } finally {
    loading.value = false
  }
}

function openForm(course = null) {
  form.id = course?.id || null
  form.name = course?.name || ''
  form.description = course?.description || ''
  formVisible.value = true
}

async function saveCourse() {
  if (!form.name.trim()) return ElMessage.warning('请输入课程名称')
  saving.value = true
  try {
    const payload = { name: form.name.trim(), description: form.description || null }
    if (form.id) await courseApi.update(form.id, payload)
    else await courseApi.create(payload)
    ElMessage.success('课程已保存')
    formVisible.value = false
    await loadCourses()
  } finally {
    saving.value = false
  }
}

async function removeCourse(id) {
  await courseApi.remove(id)
  ElMessage.success('课程已删除')
  await loadCourses()
}

async function openEnroll(course) {
  activeCourse.value = course
  selectedStudentIds.value = [...(course.student_ids || [])]
  classFilter.value = ''
  enrollVisible.value = true
  await loadStudents()
}

async function loadStudents() {
  const params = {
    ...(classFilter.value ? { class_name: classFilter.value } : {}),
    ...(activeCourse.value?.id ? { course_id: activeCourse.value.id } : {}),
  }
  const selectedSet = new Set(selectedStudentIds.value)
  students.value = await courseApi.students(params).catch(() => [])
  await nextTick()
  restoringSelection.value = true
  studentTableRef.value?.clearSelection()
  for (const student of students.value) {
    if (selectedSet.has(student.id)) {
      studentTableRef.value?.toggleRowSelection(student, true)
    }
  }
  await nextTick()
  restoringSelection.value = false
}

function onSelectionChange(selection) {
  if (restoringSelection.value) return
  const visibleIds = new Set(students.value.map((student) => student.id))
  const selectedVisibleIds = new Set(selection.map((student) => student.id))
  const hiddenSelected = selectedStudentIds.value.filter((id) => !visibleIds.has(id))
  selectedStudentIds.value = [...hiddenSelected, ...selectedVisibleIds]
}

async function saveEnrollments() {
  if (!activeCourse.value) return
  saving.value = true
  try {
    await courseApi.enroll(activeCourse.value.id, selectedStudentIds.value)
    ElMessage.success('选课名单已更新')
    enrollVisible.value = false
    await loadCourses()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    loadCourses(),
    courseApi.classes().then((res) => { classes.value = res }).catch(() => { classes.value = [] }),
  ])
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.enroll-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.muted {
  margin-left: 10px;
  color: #909399;
  font-size: 13px;
}

.dialog-body {
  max-height: 62vh;
  overflow: auto;
}

:global(.course-dialog) {
  max-width: calc(100vw - 32px);
}

:global(.course-dialog .el-dialog__body) {
  padding-top: 12px;
}

@media (max-width: 768px) {
  .enroll-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  :global(.course-dialog) {
    width: calc(100vw - 24px) !important;
  }
}
</style>
