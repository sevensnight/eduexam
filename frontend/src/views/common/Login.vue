<template>
  <AuthShell>
    <el-form :model="form" :rules="rules" ref="formRef" size="large" label-position="top" @submit.prevent="submit">
      <el-form-item prop="username" label="用户名">
        <el-input v-model="form.username" placeholder="输入你的用户名" autocomplete="username" />
      </el-form-item>
      <el-form-item prop="password" label="密码">
        <el-input v-model="form.password" type="password" placeholder="输入你的密码" autocomplete="current-password" show-password />
      </el-form-item>
      <el-button native-type="submit" type="primary" :loading="loading" class="auth-submit">登录</el-button>
      <p class="auth-switch">没有账号？<router-link to="/register">立即注册</router-link></p>
      <details class="auth-demo">
        <summary>查看演示账号</summary>
        <p>管理员：admin / admin123<br>教师：teacher1 / teacher123<br>学生：student1 / student123</p>
      </details>
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
const form = ref({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }],
}

async function submit() {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await authApi.login(form.value)
    store.setAuth(res.access_token, res.user)
    ElMessage.success(`欢迎回来，${res.user.real_name || res.user.username}！`)
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>
