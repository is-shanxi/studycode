<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="not-found">
    <p class="code">404</p>
    <h1>页面不存在</h1>
    <p>你访问的地址不存在或已被移动。</p>
    <RouterLink to="/app">返回用户端</RouterLink>
  </main>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 0.75rem;
  padding: 2rem;
  text-align: center;
}

.code {
  margin: 0;
  color: var(--color-accent);
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 0.2em;
}

h1 {
  margin: 0;
  font-size: 2rem;
}

p {
  margin: 0;
  color: var(--color-muted);
}

a {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}
</style>
