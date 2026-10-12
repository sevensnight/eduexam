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
  async (err) => {
    let responseData = err.response?.data
    if (typeof Blob !== 'undefined' && responseData instanceof Blob) {
      try {
        const raw = await responseData.text()
        try {
          responseData = raw ? JSON.parse(raw) : null
        } catch {
          responseData = raw || null
        }
      } catch {
        responseData = null
      }
    }
    const msg = responseData?.detail || responseData?.message
      || (typeof responseData === 'string' ? responseData : null)
      || err.message
      || '请求失败'
    if (!err.config?.suppressError) {
      ElMessage.error(typeof msg === 'string' ? msg : JSON.stringify(msg))
    }
    const requestUrl = err.config?.url || ''
    const isPublicAuthRequest = /^\/auth\/(login|register)(?:$|\?)/.test(requestUrl)
    if (err.response?.status === 401 && !isPublicAuthRequest) {
      clearStoredAuth()
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    return Promise.reject(err)
  }
)

export default request
