import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { clearStoredAuth, getStoredToken, getStoredUser, setStoredAuth } from '@/utils/authStorage'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getStoredToken(),
    user: getStoredUser(),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'admin',
    isTeacher: (state) => state.user?.role === 'teacher',
    isStudent: (state) => state.user?.role === 'student',
    canManage: (state) => ['admin', 'teacher'].includes(state.user?.role),
  },
  actions: {
    setAuth(token, user) {
      this.token = token
      this.user = user
      setStoredAuth(token, user)
    },
    logout() {
      this.token = ''
      this.user = null
      clearStoredAuth()
    },
  },
})
