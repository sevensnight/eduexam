<template>
  <a href="#main-content" class="skip-link">跳到主要内容</a>
  <div class="layout">
    <aside class="sidebar">
      <router-link to="/dashboard" class="brand sidebar-brand"><el-icon :size="27"><Reading /></el-icon>EduExam</router-link>
      <AppNavigation />
      <div class="sidebar-bottom">
        <span>在线题库与考试管理系统</span>
        <router-link to="/profile"><el-icon><Setting /></el-icon>个人资料</router-link>
      </div>
    </aside>

    <div class="workspace">
      <header class="header">
        <div class="header-location">
          <button type="button" class="icon-button mobile-menu" aria-label="打开导航菜单" @click="menuOpen = true"><el-icon :size="20"><Menu /></el-icon></button>
          <span class="header-brand">工作空间</span>
          <span class="header-separator">/</span>
          <span>{{ pageInfo.title }}</span>
        </div>
        <div class="header-tools">
          <el-popover placement="bottom-end" :width="300" trigger="click" @show="openNotifications">
            <template #reference>
              <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99" type="danger">
                <button type="button" class="icon-button" :aria-label="'通知，' + unreadCount + ' 条未读'"><el-icon :size="20"><Bell /></el-icon></button>
              </el-badge>
            </template>
            <div class="notification-title">通知</div>
            <div class="notification-list" v-loading="notifLoading">
              <el-empty v-if="!notifications.length && !notifLoading" description="暂无通知" :image-size="60" />
              <article v-for="n in notifications" :key="n.id" class="notification-item">
                <p>{{ n.content }}</p>
                <time>{{ new Date(n.created_at).toLocaleString() }}</time>
              </article>
            </div>
          </el-popover>
          <span class="tools-divider" aria-hidden="true"></span>
          <el-dropdown @command="handleCommand" trigger="click">
            <button type="button" class="user-info" aria-label="账号菜单">
              <el-avatar :size="32">{{ store.user?.username?.charAt(0)?.toUpperCase() }}</el-avatar>
              <span class="user-copy"><strong>{{ store.user?.real_name || store.user?.username }}</strong><small>{{ roleLabel }}</small></span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="el-main" id="main-content" tabindex="-1">
        <div class="page-content">
          <div v-if="route.path !== '/dashboard'" class="page-heading">
            <h1>{{ pageInfo.title }}</h1>
            <p v-if="pageInfo.description">{{ pageInfo.description }}</p>
          </div>
          <router-view />
        </div>
        <footer class="workspace-footer">EduExam<span>让教学更有序，让学习更专注。</span></footer>
      </main>
    </div>
    <el-drawer v-model="menuOpen" direction="ltr" size="260px" title="EduExam" class="mobile-navigation">
      <AppNavigation @navigate="menuOpen = false" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { notificationApi } from '@/api'
import AppNavigation from '@/components/AppNavigation.vue'
import { getPageInfo } from '@/utils/pageInfo'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const menuOpen = ref(false)
const pageInfo = computed(() => getPageInfo(route.path))
const roleLabel = computed(() => ({ admin: '管理员', teacher: '教师', student: '学生' }[store.user?.role] || ''))
watch(() => route.path, () => { menuOpen.value = false; document.title = pageInfo.value.title + ' · EduExam' }, { immediate: true })
const unreadCount = ref(0)
const notifications = ref([])
const notifLoading = ref(false)
let pollTimer = null
async function fetchUnread() {
  const res = await notificationApi.unreadCount().catch(() => ({ count: 0 }))
  unreadCount.value = res.count || 0
}
async function openNotifications() {
  notifLoading.value = true
  try {
    notifications.value = await notificationApi.list()
    if (unreadCount.value > 0) { await notificationApi.readAll(); unreadCount.value = 0 }
  } finally { notifLoading.value = false }
}
function handleCommand(cmd) {
  if (cmd === 'logout') { store.logout(); router.push('/login') }
  else if (cmd === 'profile') router.push('/profile')
}
onMounted(() => { fetchUnread(); pollTimer = setInterval(fetchUnread, 30000) })
onUnmounted(() => clearInterval(pollTimer))
</script>

<style scoped>
/* Layer 10: sticky header. Drawer/popovers use Element Plus overlay layers. */
.layout { min-height: 100dvh; display: flex; }
.sidebar { width: 232px; flex-shrink: 0; background: #f0f0f3; border-right: 1px solid var(--app-border); padding: 0 14px; position: fixed; inset: 0 auto 0 0; display: flex; flex-direction: column; overflow-y: auto; }
.sidebar-brand { padding: 29px 15px 43px; }
.sidebar-bottom { margin-top: auto; padding: 28px 16px; display: grid; gap: 18px; font-size: 11px; color: var(--app-text-secondary); }
.sidebar-bottom a { display: flex; align-items: center; gap: 10px; color: #55555b; font-size: 13px; }
.workspace { margin-left: 232px; flex: 1; min-width: 0; }
.header { height: 76px; padding: 0 40px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--app-border); background: var(--app-surface); position: sticky; top: 0; z-index: 10; gap: 16px; }
.header-location { display: flex; align-items: center; gap: 14px; font-size: 13px; min-width: 0; }
.header-brand { color: var(--app-text-secondary); }
.header-separator { color: #c1c1c7; }
.header-tools { display: flex; align-items: center; gap: 18px; flex-shrink: 0; }
.icon-button { border: 0; background: transparent; display: grid; place-items: center; width: 36px; height: 36px; border-radius: 8px; color: #55555b; }
.icon-button:hover { background: var(--app-bg); }
.tools-divider { height: 24px; width: 1px; background: var(--app-border); }
.user-info { display: flex; align-items: center; gap: 10px; padding: 4px; background: transparent; border: 0; color: var(--app-text); border-radius: 8px; }
.user-info:hover { background: var(--app-bg); }
.user-info .el-avatar { background: #e9edf4; color: #3d577a; font-size: 13px; font-weight: 600; }
.user-copy { display: flex; flex-direction: column; text-align: left; line-height: 1.5; }
.user-copy strong { font-size: 12px; font-weight: 500; }
.user-copy small { font-size: 10px; color: var(--app-text-secondary); }
.user-info > .el-icon { margin-left: 8px; font-size: 12px; color: var(--app-text-secondary); }
.el-main { padding: 36px 40px 0; overflow: visible; min-width: 0; }
.workspace-footer { max-width: 1480px; margin: 32px auto 0; padding: 22px 0; display: flex; justify-content: space-between; border-top: 1px solid var(--app-border); color: var(--app-text-secondary); font-size: 11px; }
.notification-title { padding: 8px 8px 14px; font-size: 15px; font-weight: 600; border-bottom: 1px solid var(--app-border); }
.notification-list { max-height: 340px; min-height: 100px; overflow-y: auto; }
.notification-item { padding: 14px 8px; border-bottom: 1px solid var(--app-border); }
.notification-item p { font-size: 13px; margin: 0 0 6px; }
.notification-item time { color: var(--app-text-secondary); font-size: 11px; }
.mobile-menu { display: none; }
.skip-link { position: fixed; top: -100px; left: 16px; background: white; padding: 12px; z-index: 3000; }
.skip-link:focus { top: 12px; }
@media (max-width: 1100px) {
  .sidebar { width: 204px; }
  .workspace { margin-left: 204px; }
  .header { padding: 0 24px; }
  .el-main { padding: 28px 24px 0; }
}
@media (max-width: 767px) {
  .sidebar { display: none; }
  .workspace { margin-left: 0; }
  .mobile-menu { display: grid; }
  .header { height: 64px; padding: 0 16px; }
  .header-brand, .header-separator, .user-copy, .tools-divider { display: none; }
  .header-location { gap: 8px; }
  .header-tools { gap: 10px; }
  .user-info > .el-icon { margin-left: 0; }
  .el-main { padding: 24px 16px 0; }
  .workspace-footer { margin-top: 24px; gap: 12px; font-size: 10px; }
}
</style>
