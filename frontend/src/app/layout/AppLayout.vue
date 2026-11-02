<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/features/auth/store'
import { useNotificationStore } from '@/features/notification/store'

const session = useSessionStore()
const notifications = useNotificationStore()
const router = useRouter()
const route = useRoute()
const displayName = computed(() => session.user?.nickname ?? session.user?.username ?? '')
let refreshTimer: number | undefined

onMounted(() => {
  void notifications.refresh()
  refreshTimer = window.setInterval(() => {
    void notifications.refresh()
  }, 60_000)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
  }
})

watch(
  () => route.fullPath,
  () => {
    void notifications.refresh()
  },
)

async function signOut(): Promise<void> {
  await session.logout()
  notifications.clear()
  await router.push({ name: 'app-login' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="brand" to="/app">考证刷题平台</RouterLink>
      <nav class="app-nav" aria-label="用户导航">
        <RouterLink to="/app/library">题库</RouterLink>
        <RouterLink to="/app/selftest">自测</RouterLink>
        <RouterLink to="/app/assistant">AI 助手</RouterLink>
        <RouterLink to="/app/records">记录</RouterLink>
        <RouterLink to="/app/favorites">收藏</RouterLink>
        <RouterLink to="/app/wrong-questions">错题</RouterLink>
        <RouterLink class="notification-link" to="/app/notifications">
          通知
          <span
            v-if="notifications.unreadCount > 0"
            class="notification-badge"
            data-testid="unread-notification-badge"
          >
            {{ notifications.unreadCount > 99 ? '99+' : notifications.unreadCount }}
          </span>
        </RouterLink>
        <RouterLink to="/app/account">个人中心</RouterLink>
        <RouterLink v-if="session.isAdmin" to="/admin">管理端</RouterLink>
        <span v-if="displayName" class="user-name">{{ displayName }}</span>
        <button class="link-button" type="button" @click="signOut">退出</button>
      </nav>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem clamp(1rem, 4vw, 2.5rem);
  border-bottom: 1px solid var(--color-border);
  background: rgb(255 255 255 / 88%);
  backdrop-filter: blur(12px);
}

.brand {
  color: var(--color-text);
  font-size: 1.1rem;
  font-weight: 700;
  text-decoration: none;
}

.app-nav {
  display: flex;
  align-items: center;
  gap: 1rem;
  color: var(--color-muted);
  font-size: 0.9rem;
}

.app-nav a {
  text-decoration: none;
}

.notification-link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.notification-badge {
  min-width: 1.15rem;
  padding: 0 0.3rem;
  border-radius: 999px;
  background: var(--color-accent);
  color: #fff;
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.15rem;
  text-align: center;
}

.user-name {
  font-weight: 600;
}

.link-button {
  border: 0;
  padding: 0;
  background: none;
  color: var(--color-accent);
  cursor: pointer;
  font: inherit;
}

.app-main {
  padding: clamp(1.5rem, 4vw, 3rem);
}
</style>
