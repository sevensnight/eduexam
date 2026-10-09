<template>
  <div style="max-width: 520px; margin: 0 auto">
    <el-card header="个人资料" style="margin-bottom: 20px">
      <el-form :model="profileForm" label-width="90px">
        <el-form-item label="用户名">
          <el-input :value="store.user?.username" disabled />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input :value="store.user?.email" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-tag :type="roleTagType">{{ roleLabel }}</el-tag>
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="profileForm.real_name" placeholder="输入真实姓名" />
        </el-form-item>
        <el-form-item label="班级">
          <el-input v-model="profileForm.class_name" placeholder="可填写班级信息" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="profileLoading" @click="saveProfile">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card header="修改密码">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="old_password">
          <el-input v-model="pwdForm.old_password" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="new_password">
          <el-input v-model="pwdForm.new_password" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="pwdForm.confirm" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="pwdLoading" @click="changePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const store = useUserStore()

const roleLabel = computed(() => ({ admin: '管理员', teacher: '教师', student: '学生' }[store.user?.role] || ''))
const roleTagType = computed(() => ({ admin: 'danger', teacher: 'warning', student: 'success' }[store.user?.role] || 'info'))

const profileForm = ref({
  real_name: store.user?.real_name || '',
  class_name: store.user?.class_name || '',
})
const profileLoading = ref(false)

async function saveProfile() {
  profileLoading.value = true
  try {
    const updated = await authApi.updateProfile({
      real_name: profileForm.value.real_name,
      class_name: profileForm.value.class_name,
    })
    store.setAuth(store.token, updated)
    ElMessage.success('保存成功')
  } finally {
    profileLoading.value = false
  }
}

const pwdFormRef = ref()
const pwdLoading = ref(false)
const pwdForm = ref({ old_password: '', new_password: '', confirm: '' })
const pwdRules = {
  old_password: [{ required: true, message: '请输入原密码' }],
  new_password: [{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少 6 位' }],
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
  await pwdFormRef.value.validate()
  pwdLoading.value = true
  try {
    await authApi.changePassword({
      old_password: pwdForm.value.old_password,
      new_password: pwdForm.value.new_password,
    })
    ElMessage.success('密码修改成功')
    pwdForm.value = { old_password: '', new_password: '', confirm: '' }
    pwdFormRef.value.resetFields()
  } finally {
    pwdLoading.value = false
  }
}
</script>
