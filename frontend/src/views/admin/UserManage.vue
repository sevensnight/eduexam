<template>
  <el-card class="management-list directory-management user-management">
    <template #header>
      <div class="list-heading">
        <div><h2>{{ hasFilters ? '筛选结果' : '全部用户' }}</h2><span class="list-count-badge">{{ users.length }}</span></div>
        <div class="header-actions">
          <el-upload :show-file-list="false" accept=".csv,.xlsx" :http-request="uploadStudents" :disabled="importing"><el-button :icon="Upload" :loading="importing">导入学生</el-button></el-upload>
          <el-dropdown trigger="click" @command="downloadStudents">
            <el-button :icon="Download" :loading="exporting" :disabled="!canExport || loading || loadError">导出学生<el-icon class="export-arrow"><ArrowDown /></el-icon></el-button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item command="xlsx">导出 Excel</el-dropdown-item><el-dropdown-item command="csv">导出 CSV</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </div>
    </template>
    <div class="list-body">
      <form class="directory-filters" @submit.prevent="applyFilters">
        <div class="directory-field directory-keyword"><label for="user-search">搜索用户</label><el-input id="user-search" v-model="filters.keyword" placeholder="姓名、账号、邮箱或班级" clearable :prefix-icon="Search" /></div>
        <div class="directory-field"><label for="user-role">用户角色</label><el-select id="user-role" v-model="filters.role" placeholder="全部角色" clearable><el-option v-for="role in roles" :key="role" :label="roleLabel(role)" :value="role" /></el-select></div>
        <div class="directory-field"><label for="user-class">所属班级</label><el-select id="user-class" v-model="filters.class_name" placeholder="全部班级" clearable filterable><el-option v-for="name in classOptions" :key="name" :label="name" :value="name" /></el-select></div>
        <div class="filter-buttons"><el-button type="primary" native-type="submit">查询</el-button><el-button :icon="RefreshLeft" @click="resetFilters">重置</el-button></div>
      </form>
      <div class="directory-context"><span>集中管理账号、角色与访问权限</span><span>{{ canExport ? '导出包含当前查询条件下的全部学生' : '导出学生仅适用于「全部角色」或「学生」筛选' }}</span></div>
      <el-alert v-if="loadError" class="list-error" type="error" :closable="false" show-icon title="用户列表加载失败"><el-button link type="primary" @click="fetchUsers">重新加载</el-button></el-alert>
      <div v-else v-loading="loading" class="list-results" :aria-busy="loading">
        <template v-if="pageUsers.length">
          <el-table :data="pageUsers" row-key="id" class="list-table">
            <el-table-column label="用户信息" min-width="210"><template #default="{ row }"><UserIdentity :user="row" :current="isSelf(row)" /></template></el-table-column>
            <el-table-column label="班级 / 联系方式" min-width="200"><template #default="{ row }"><div class="cell-stack wrap-copy"><span>{{ row.class_name || '未分配班级' }}</span><span class="secondary-copy">{{ row.email || '未填写邮箱' }}</span></div></template></el-table-column>
            <el-table-column label="角色" width="90"><template #default="{ row }"><span class="user-role" :class="{ 'is-admin': row.role === 'admin' }">{{ roleLabel(row.role) }}</span></template></el-table-column>
            <el-table-column label="状态" width="85"><template #default="{ row }"><span class="user-state" :class="{ 'is-active': row.is_active }">{{ row.is_active ? '正常' : '禁用' }}</span></template></el-table-column>
            <el-table-column label="注册时间" width="130"><template #default="{ row }"><div class="date-copy"><span>{{ listDateTime(row.created_at).date }}</span><span>{{ listDateTime(row.created_at).time }}</span></div></template></el-table-column>
            <el-table-column label="操作" width="128"><template #default="{ row }"><ManagementActions :name="userDisplayName(row)" primary-label="详情" :actions="userActions(row)" :busy="busyIds.has(row.id)" @action="command => handleAction(row, command)" /></template></el-table-column>
          </el-table>
          <div class="compact-list">
            <article v-for="user in pageUsers" :key="user.id" class="compact-item">
              <UserIdentity :user="user" :current="isSelf(user)" />
              <div class="compact-user-meta"><span>{{ user.class_name || '未分配班级' }}</span><span>{{ user.email || '未填写邮箱' }}</span><span>注册于 {{ formatAppDateTime(user.created_at) }}</span></div>
              <div class="compact-footer"><div class="user-badges"><span class="user-role" :class="{ 'is-admin': user.role === 'admin' }">{{ roleLabel(user.role) }}</span><span class="user-state" :class="{ 'is-active': user.is_active }">{{ user.is_active ? '正常' : '禁用' }}</span></div><ManagementActions :name="userDisplayName(user)" primary-label="详情" :actions="userActions(user)" :busy="busyIds.has(user.id)" @action="command => handleAction(user, command)" /></div>
            </article>
          </div>
        </template>
        <el-empty v-else-if="!loading" :image-size="72" :description="hasFilters ? '没有匹配的用户，试试调整筛选条件' : '暂无用户'" />
      </div>
      <div v-if="!loadError && users.length" class="list-pagination"><span class="secondary-copy">共 {{ users.length }} 位用户 · 每页 {{ pageSize }} 位</span><el-pagination v-model:current-page="page" :page-size="pageSize" :total="users.length" layout="prev, pager, next" :pager-count="5" small /></div>
    </div>
  </el-card>

  <el-dialog v-model="detailVisible" title="用户详情" width="540px" class="directory-detail-dialog user-dialog" append-to-body destroy-on-close>
    <template v-if="detailUser"><UserIdentity :user="detailUser" :current="isSelf(detailUser)" /><dl class="detail-list"><dt>用户 ID</dt><dd>{{ detailUser.id }}</dd><dt>账号</dt><dd>{{ detailUser.username }}</dd><dt>姓名</dt><dd>{{ detailUser.real_name || '未填写' }}</dd><dt>用户角色</dt><dd>{{ roleLabel(detailUser.role) }}</dd><dt>账号状态</dt><dd>{{ detailUser.is_active ? '正常' : '禁用' }}</dd><dt>所属班级</dt><dd>{{ detailUser.class_name || '未分配班级' }}</dd><dt>联系邮箱</dt><dd>{{ detailUser.email || '未填写邮箱' }}</dd><dt>注册时间</dt><dd>{{ formatAppDateTime(detailUser.created_at) }}</dd></dl></template>
    <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
  </el-dialog>

  <el-dialog v-model="roleVisible" title="修改用户角色" width="480px" class="user-dialog role-dialog" append-to-body destroy-on-close :before-close="closeRole" :close-on-click-modal="!roleSaving" :close-on-press-escape="!roleSaving" :show-close="!roleSaving">
    <template v-if="roleTarget">
      <UserIdentity :user="roleTarget" />
      <p class="directory-form-note role-note">当前角色：{{ roleLabel(originalRole) }}。角色变更后，该用户需要重新登录，访问权限也会随之改变。</p>
      <el-form label-position="top" @submit.prevent="saveRole"><el-form-item label="新的用户角色"><el-select v-model="nextRole" aria-label="新的用户角色" style="width:100%" :disabled="roleSaving"><el-option v-for="role in roles" :key="role" :label="roleLabel(role)" :value="role" /></el-select></el-form-item></el-form>
      <p class="directory-form-note">有关联课程、考试或题目等业务数据时，系统可能阻止角色转换，以保护原有数据。</p>
    </template>
    <template #footer><el-button :disabled="roleSaving" @click="roleVisible = false">取消</el-button><el-button type="primary" :loading="roleSaving" :disabled="nextRole === originalRole" @click="saveRole">确认修改</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ArrowDown, Download, RefreshLeft, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { createUploadForm, downloadBlob } from '@/utils/download'
import { commitRoleChange } from '@/utils/userRole'
import { formatAppDateTime } from '@/utils/examTime'
import { listDateTime } from '@/utils/listPresentation'
import { roleLabel, userDisplayName, userListParams } from '@/utils/management'
import ManagementActions from '@/components/lists/ManagementActions.vue'
import UserIdentity from '@/components/lists/UserIdentity.vue'
import '@/styles/managementLists.css'
import '@/styles/directoryLists.css'

const store = useUserStore()
const roles = ['student', 'teacher', 'admin']
const loading = ref(true)
const loadError = ref(false)
const users = ref([])
const busyIds = ref(new Set())
const filters = reactive({ keyword: '', role: '', class_name: '' })
const applied = ref({ ...filters })
const knownClasses = ref([])
const page = ref(1)
const pageSize = 20
const detailVisible = ref(false)
const detailUser = ref(null)
const roleVisible = ref(false)
const roleTarget = ref(null)
const originalRole = ref('')
const nextRole = ref('')
const roleSaving = ref(false)
const importing = ref(false)
const exporting = ref(false)
let sequence = 0
let disposed = false
const hasFilters = computed(() => !!(applied.value.keyword || applied.value.role || applied.value.class_name))
const canExport = computed(() => !applied.value.role || applied.value.role === 'student')
const classOptions = computed(() => [...new Set([...knownClasses.value, filters.class_name].filter(Boolean))].sort((a, b) => a.localeCompare(b, 'zh-CN')))
const pageUsers = computed(() => users.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(() => users.value.length, count => { page.value = Math.min(page.value, Math.max(1, Math.ceil(count / pageSize))) })
const isSelf = user => String(user.id) === String(store.user?.id)

async function fetchUsers() {
  const current = ++sequence
  loading.value = true
  loadError.value = false
  try {
    const result = await userApi.list(userListParams(applied.value))
    if (current !== sequence || disposed) return
    users.value = result
    knownClasses.value = [...new Set([...knownClasses.value, ...result.map(user => user.class_name).filter(Boolean)])]
  } catch {
    if (current === sequence && !disposed) loadError.value = true
  } finally {
    if (current === sequence && !disposed) loading.value = false
  }
}
async function loadClasses() {
  try {
    const result = await courseApi.classes()
    if (!disposed) knownClasses.value = [...new Set([...knownClasses.value, ...result])]
  } catch {
    // The list response still supplies class options if the supplemental request fails.
  }
}
function applyFilters() { applied.value = { ...filters, keyword: filters.keyword.trim() }; page.value = 1; fetchUsers() }
function resetFilters() { Object.assign(filters, { keyword: '', role: '', class_name: '' }); applyFilters() }
function userActions(user) {
  return [
    { command: 'role', label: '修改角色', disabled: isSelf(user) },
    { command: 'status', label: user.is_active ? '禁用账号' : '启用账号', disabled: isSelf(user), danger: !!user.is_active, divided: true },
  ]
}
function handleAction(user, command) {
  if (busyIds.value.has(user.id)) return
  if (command === 'primary') { detailUser.value = user; detailVisible.value = true }
  else if (!isSelf(user) && command === 'role') { roleTarget.value = user; nextRole.value = user.role; originalRole.value = user.role; roleVisible.value = true }
  else if (!isSelf(user) && command === 'status') toggleActive(user)
}
function closeRole(done) { if (!roleSaving.value) done() }
async function saveRole() {
  const user = roleTarget.value
  if (!user || isSelf(user) || roleSaving.value || busyIds.value.has(user.id) || nextRole.value === originalRole.value) return
  roleSaving.value = true
  busyIds.value.add(user.id)
  try {
    await commitRoleChange(user, nextRole.value, role => userApi.setRole(user.id, role))
    if (disposed) return
    ElMessage.success('角色已更新')
    roleVisible.value = false
    await fetchUsers()
  } catch {
    // commitRoleChange restores the old role; retain the dialog for correction or retry.
  } finally { roleSaving.value = false; busyIds.value.delete(user.id) }
}
async function toggleActive(user) {
  if (isSelf(user) || busyIds.value.has(user.id)) return
  busyIds.value.add(user.id)
  try {
    await ElMessageBox.confirm(
      (user.is_active ? '禁用后，该用户将无法登录，当前登录也会失效。' : '启用后，该用户可以重新登录系统。') + '已有业务数据会保留。',
      (user.is_active ? '禁用' : '启用') + '“' + userDisplayName(user) + '”？',
      { type: 'warning', confirmButtonText: user.is_active ? '确认禁用' : '确认启用', cancelButtonText: '取消' },
    )
    if (disposed) return
    const result = await userApi.toggleActive(user.id)
    if (disposed) return
    user.is_active = result.is_active
    ElMessage.success(user.is_active ? '账号已启用' : '账号已禁用')
    await fetchUsers()
  } catch {
    // Do not optimistically change account status when the request is cancelled or rejected.
  } finally { busyIds.value.delete(user.id) }
}
async function downloadStudents(format) {
  if (!canExport.value || exporting.value || loading.value || loadError.value || !['xlsx', 'csv'].includes(format)) return
  exporting.value = true
  try {
    const blob = await userApi.exportStudents({ ...userListParams(applied.value), role: 'student', format })
    if (!disposed) downloadBlob(blob, 'students_export.' + format)
  } catch {
    // The request interceptor reports export errors.
  } finally { exporting.value = false }
}
async function uploadStudents(options) {
  if (importing.value) throw new Error('正在导入，请稍后再试')
  if (!/\.(csv|xlsx)$/i.test(options.file.name)) {
    ElMessage.warning('请选择 CSV 或 Excel（.xlsx）文件')
    throw new Error('不支持的文件类型')
  }
  importing.value = true
  try {
    const result = await userApi.importStudents(createUploadForm(options.file))
    if (disposed) return result
    const firstError = result.errors?.[0]
    if (firstError) ElMessage.warning('已新增 ' + result.created + ' 人，更新 ' + result.updated + ' 人；第 ' + firstError.row + ' 行失败：' + firstError.message)
    else ElMessage.success('导入完成：新增 ' + result.created + ' 人，更新 ' + result.updated + ' 人')
    await Promise.all([fetchUsers(), loadClasses()])
    return result
  } finally { importing.value = false }
}
onMounted(() => { fetchUsers(); loadClasses() })
onBeforeUnmount(() => { disposed = true; ++sequence })
</script>

<style scoped>
:global(.user-dialog) { max-width: calc(100vw - 32px); }
.role-note { margin-top: 20px; }
</style>
