<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="admin-shell">
    <header class="admin-header">
      <RouterLink class="brand" to="/admin">考证刷题平台 · 管理端</RouterLink>
      <nav class="admin-nav" aria-label="管理导航">
        <RouterLink to="/app">返回用户端</RouterLink>
      </nav>
    </header>
    <main class="admin-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  background: #f8fafc;
}

.admin-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem clamp(1rem, 4vw, 2.5rem);
  background: #10203f;
  color: #f8fafc;
}

.brand {
  color: inherit;
  font-weight: 700;
  text-decoration: none;
}

.admin-nav a {
  color: #dbeafe;
  text-decoration: none;
}

.admin-main {
  padding: clamp(1.5rem, 4vw, 3rem);
}
</style>
