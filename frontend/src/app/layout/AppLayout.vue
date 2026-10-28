<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'

import { useSessionStore } from '@/features/auth/store'

const session = useSessionStore()
const router = useRouter()
const displayName = computed(() => session.user?.nickname ?? session.user?.username ?? '')

async function signOut(): Promise<void> {
  await session.logout()
  await router.push({ name: 'app-login' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="brand" to="/app">考证刷题平台</RouterLink>
      <nav class="app-nav" aria-label="用户导航">
        <RouterLink to="/app/library">题库</RouterLink>
        <RouterLink to="/app/records">记录</RouterLink>
        <RouterLink to="/app/favorites">收藏</RouterLink>
        <RouterLink to="/app/wrong-questions">错题</RouterLink>
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
