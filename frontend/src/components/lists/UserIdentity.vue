<template>
  <div class="user-identity">
    <span class="user-initial" aria-hidden="true">{{ userDisplayName(user).slice(0, 1).toUpperCase() }}</span>
    <div class="user-identity-copy">
      <div class="user-display-name">{{ userDisplayName(user) }}<span v-if="current" class="current-user">本人</span></div>
      <div class="user-account">{{ user.username }}<span>ID {{ user.id }}</span></div>
    </div>
  </div>
</template>
<script setup>
import { userDisplayName } from '@/utils/management'
defineProps({ user: { type: Object, required: true }, current: Boolean })
</script>
<style scoped>
.user-identity { display: flex; align-items: center; gap: 12px; min-width: 0; }
.user-initial { width: 34px; height: 34px; flex: 0 0 34px; display: grid; place-items: center; border-radius: 10px; background: #eef2f7; color: #52647b; font-size: 14px; font-weight: 600; }
.user-identity-copy { min-width: 0; }
.user-display-name { color: var(--app-text); font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
.user-account { display: flex; flex-wrap: wrap; gap: 4px 10px; color: var(--app-text-secondary); font-size: 12px; margin-top: 4px; overflow-wrap: anywhere; }
.user-account > span { color: #85858c; }
.current-user { margin-left: 8px; font-size: 11px; font-weight: 400; padding: 2px 5px; border-radius: 4px; background: var(--app-accent-soft); color: var(--app-accent); white-space: nowrap; }
</style>
