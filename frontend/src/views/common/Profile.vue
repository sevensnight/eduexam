<template>
  <div class="workflow-page profile-page">
    <el-card>
      <template #header><div class="workflow-heading"><div><h2>个人资料</h2><p class="workflow-caption">维护个人信息，账号与角色由系统管理员管理</p></div><el-tag type="info">{{ roleLabel }}</el-tag></div></template>
      <el-form :model="profileForm" label-position="top" class="workflow-form" :disabled="profileLoading" @submit.prevent="saveProfile">
        <div class="form-grid">
          <el-form-item label="用户名"><el-input :model-value="store.user?.username" disabled /></el-form-item>
          <el-form-item label="邮箱"><el-input :model-value="store.user?.email || '未设置邮箱'" disabled /></el-form-item>
          <el-form-item label="真实姓名"><el-input v-model="profileForm.real_name" placeholder="输入真实姓名" autocomplete="name" /></el-form-item>
          <el-form-item label="班级"><el-input v-model="profileForm.class_name" :disabled="store.isStudent" :placeholder="store.isStudent ? '由管理员维护' : '输入班级（可选）'" /></el-form-item>
        </div>
        <div class="profile-save"><el-button type="primary" native-type="submit" :loading="profileLoading">保存资料</el-button></div>
      </el-form>
    </el-card>
    <el-card>
      <template #header><div class="workflow-heading"><div><h2>账号安全</h2><p class="workflow-caption">修改密码后，其他已登录会话将失效</p></div></div></template>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-position="top" class="workflow-form password-form" :disabled="pwdLoading" @submit.prevent="changePassword">
        <el-form-item label="原密码" prop="old_password"><el-input v-model="pwdForm.old_password" type="password" maxlength="72" show-password autocomplete="current-password" placeholder="输入当前密码" /></el-form-item>
        <el-form-item label="新密码" prop="new_password"><el-input v-model="pwdForm.new_password" type="password" maxlength="72" show-password autocomplete="new-password" placeholder="设置新的登录密码" /><p class="workflow-caption">8–72 位，至少包含一个字母和一个数字；UTF-8 编码不超过 72 字节。</p></el-form-item>
        <el-form-item label="确认密码" prop="confirm"><el-input v-model="pwdForm.confirm" type="password" maxlength="72" show-password autocomplete="new-password" placeholder="再次输入新密码" /></el-form-item>
        <div class="profile-save"><el-button type="primary" native-type="submit" :loading="pwdLoading">修改密码</el-button></div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import '@/styles/workflow.css'

const store = useUserStore()

const roleLabel = computed(() => ({ admin: '管理员', teacher: '教师', student: '学生' }[store.user?.role] || ''))

const profileForm = ref({
  real_name: store.user?.real_name || '',
  class_name: store.user?.class_name || '',
})
const profileLoading = ref(false)

async function saveProfile() {
  if (profileLoading.value) return
  profileLoading.value = true
  try {
    const payload = {
      real_name: profileForm.value.real_name,
    }
    if (store.user?.role !== 'student') payload.class_name = profileForm.value.class_name
    const updated = await authApi.updateProfile(payload)
    store.setAuth(store.token, updated)
    ElMessage.success('保存成功')
  } catch { /* 保存失败保留输入，错误由接口层提示。 */ }
  finally {
    profileLoading.value = false
  }
}

const pwdFormRef = ref()
const pwdLoading = ref(false)
const pwdForm = ref({ old_password: '', new_password: '', confirm: '' })
function validatePasswordBytes(_, value, callback) {
  if (value && new TextEncoder().encode(value).length > 72) callback(new Error('密码的 UTF-8 编码不能超过 72 字节'))
  else callback()
}
const pwdRules = {
  old_password: [
    { required: true, message: '请输入原密码' },
    { max: 72, message: '密码不能超过 72 位' },
    { validator: validatePasswordBytes },
  ],
  new_password: [
    { required: true, message: '请输入新密码' },
    { min: 8, max: 72, message: '密码长度需为 8-72 位' },
    { validator: validatePasswordBytes },
    { pattern: /^(?=.*\p{L})(?=.*\d).+$/u, message: '密码至少包含一个字母和一个数字' },
  ],
  confirm: [
    { required: true, message: '请确认新密码' },
    {
      validator: (_, value, callback) => {
        if (value !== pwdForm.value.new_password) callback(new Error('两次密码不一致'))
        else callback()
      },
    },
  ],
}

async function changePassword() {
  if (pwdLoading.value) return
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid || pwdLoading.value) return
  pwdLoading.value = true
  try {
    const result = await authApi.changePassword({
      old_password: pwdForm.value.old_password,
      new_password: pwdForm.value.new_password,
    })
    store.setAuth(result.access_token, result.user)
    ElMessage.success('密码修改成功，其他已登录会话已失效')
    pwdForm.value = { old_password: '', new_password: '', confirm: '' }
    pwdFormRef.value.resetFields()
  } catch { /* 密码未更新，保留输入以便修正。 */ }
  finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.profile-page { grid-template-columns: minmax(0,1.2fr) minmax(0,1fr); align-items: start; }
.profile-page > .el-card { min-width: 0; }
.profile-save { padding-top: 8px; }
.password-form .workflow-caption { line-height: 1.7; margin-top: 8px; }
@media (max-width: 1200px) { .profile-page { grid-template-columns: minmax(0,1fr); } }
</style>
