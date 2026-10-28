<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/features/auth/store'

const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const error = ref<string | null>(null)
const form = reactive({ username: '', password: '' })

const redirectTarget = computed(() =>
  typeof route.query.redirect === 'string' ? route.query.redirect : '/app',
)

async function submit(): Promise<void> {
  error.value = null
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await session.login({ username: form.username.trim(), password: form.password })
    await router.push(redirectTarget.value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <p class="eyebrow">账号</p>
      <h1>登录</h1>
      <p class="lead">使用用户名和密码登录，继续你的刷题计划。</p>

      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

      <el-form class="auth-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">
          登录
        </el-button>
      </el-form>

      <p class="switch-link">
        还没有账号？
        <RouterLink to="/app/register">立即注册</RouterLink>
      </p>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 2rem;
}

.auth-card {
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

.auth-form {
  margin-top: 1.25rem;
}

.submit-button {
  width: 100%;
}

.switch-link {
  margin: 1.25rem 0 0;
  color: var(--color-muted);
  font-size: 0.9rem;
}

.switch-link a {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}
</style>
