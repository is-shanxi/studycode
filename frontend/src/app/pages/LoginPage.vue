<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <p class="eyebrow">账号</p>
      <h1>登录</h1>
      <p class="lead">登录表单与会话交互将在 P1 核心闭环中接入。</p>
      <RouterLink class="back-link" to="/app">返回用户端</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 2rem;
}

.login-card {
  width: min(28rem, 100%);
  padding: clamp(1.75rem, 5vw, 2.5rem);
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
  box-shadow: 0 1.5rem 4rem rgb(15 23 42 / 8%);
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 1.75rem;
}

.lead {
  margin: 1rem 0 1.5rem;
  color: var(--color-muted);
  line-height: 1.7;
}

.back-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}
</style>
