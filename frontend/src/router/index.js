import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, getActivePinia } from 'pinia'
import { getStoredToken, getStoredUser } from '@/utils/authStorage'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('@/views/common/Login.vue'), meta: { guest: true } },
  { path: '/register', component: () => import('@/views/common/Register.vue'), meta: { guest: true } },
  {
    path: '/',
    component: () => import('@/views/common/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: 'dashboard', component: () => import('@/views/common/Dashboard.vue') },
      { path: 'questions', component: () => import('@/views/teacher/QuestionList.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'questions/create', component: () => import('@/views/teacher/QuestionForm.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'questions/:id/edit', component: () => import('@/views/teacher/QuestionForm.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'categories', component: () => import('@/views/teacher/Categories.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'courses', component: () => import('@/views/teacher/CourseManage.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'exams', component: () => import('@/views/common/ExamList.vue') },
      { path: 'exams/create', component: () => import('@/views/teacher/ExamForm.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'exams/:id/edit', component: () => import('@/views/teacher/ExamForm.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'exams/:id/stats', component: () => import('@/views/teacher/ExamStats.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'exams/:id/grade', component: () => import('@/views/teacher/ExamGrade.vue'), meta: { roles: ['admin', 'teacher'] } },
      { path: 'exams/:id/take', component: () => import('@/views/student/TakeExam.vue'), meta: { roles: ['student'] } },
      { path: 'exams/:id/result', component: () => import('@/views/student/ExamResult.vue') },
      { path: 'exams/:id/leaderboard', component: () => import('@/views/common/Leaderboard.vue') },
      { path: 'stats', component: () => import('@/views/common/Stats.vue') },
      { path: 'profile', component: () => import('@/views/common/Profile.vue') },
      { path: 'my-records', component: () => import('@/views/student/MyRecords.vue'), meta: { roles: ['student'] } },
      { path: 'wrong-book', component: () => import('@/views/student/WrongBook.vue'), meta: { roles: ['student'] } },
      { path: 'admin/users', component: () => import('@/views/admin/UserManage.vue'), meta: { roles: ['admin'] } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  // 直接读 sessionStorage，不依赖 Pinia 是否初始化；不同标签页可登录不同账号，方便演示。
  const token = getStoredToken()
  const user = getStoredUser()
  const isLoggedIn = !!token

  if (to.meta.guest) {
    if (isLoggedIn) return next('/dashboard')
    return next()
  }

  if (to.meta.requiresAuth || to.matched.some((r) => r.meta.requiresAuth)) {
    if (!isLoggedIn) return next('/login')
  }

  if (to.meta.roles) {
    if (!user || !to.meta.roles.includes(user.role)) {
      return next('/dashboard')
    }
  }

  next()
})

export default router
