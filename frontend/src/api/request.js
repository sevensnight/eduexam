import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearStoredAuth, getStoredToken } from '@/utils/authStorage'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err.response?.data?.detail || err.message || '请求失败'
    ElMessage.error(typeof msg === 'string' ? msg : JSON.stringify(msg))
    if (err.response?.status === 401) {
      clearStoredAuth()
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default request
