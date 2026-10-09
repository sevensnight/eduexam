<template>
  <AuthShell register>
    <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top" @submit.prevent="submit">
      <el-form-item prop="username" label="用户名">
        <el-input v-model="form.username" placeholder="4-20 位字母或数字" autocomplete="username" />
      </el-form-item>
      <el-form-item prop="real_name" label="真实姓名">
        <el-input v-model="form.real_name" placeholder="输入你的姓名" autocomplete="name" />
      </el-form-item>
      <el-form-item prop="class_name" label="班级">
        <el-input v-model="form.class_name" placeholder="学生建议填写" />
      </el-form-item>
      <el-form-item prop="email" label="邮箱">
        <el-input v-model="form.email" placeholder="输入邮箱地址" autocomplete="email" />
      </el-form-item>
      <el-form-item prop="password" label="密码">
        <el-input v-model="form.password" type="password" placeholder="至少 6 位" autocomplete="new-password" show-password />
      </el-form-item>
      <el-form-item prop="role" label="角色">
        <el-select v-model="form.role" style="width:100%" placeholder="选择角色">
          <el-option label="学生" value="student" /><el-option label="教师" value="teacher" />
        </el-select>
      </el-form-item>
      <el-button native-type="submit" type="primary" :loading="loading" class="auth-submit">注册</el-button>
      <p class="auth-switch">已有账号？<router-link to="/login">返回登录</router-link></p>
    </el-form>
  </AuthShell>
</template>

<script setup>
import AuthShell from '@/components/AuthShell.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = ref({
  username: '',
  email: '',
  password: '',
  real_name: '',
  class_name: '',
  role: 'student',
})

const rules = {
  username: [{ required: true, min: 4, max: 20, message: '用户名长度需为 4-20 位' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位' }],
  role: [{ required: true, message: '请选择角色' }],
}

async function submit() {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const result = await authApi.register(form.value)
    store.setAuth(result.access_token, result.user)
    ElMessage.success('注册成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>
