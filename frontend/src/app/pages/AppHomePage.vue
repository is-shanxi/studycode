<script setup lang="ts">
import { useSessionStore } from '@/features/auth/store'

const session = useSessionStore()
</script>

<template>
  <section class="page">
    <p class="eyebrow">P1 基础闭环</p>
    <h1>欢迎回来，{{ session.user?.nickname ?? session.user?.username ?? '同学' }}</h1>
    <p class="lead">工程外壳已接入。题库、练习、记录、收藏与错题页面将在 P1 阶段陆续接入。</p>
    <dl class="facts">
      <div>
        <dt>当前角色</dt>
        <dd>{{ session.user?.roles.join(' / ') || '未加载' }}</dd>
      </div>
      <div>
        <dt>会话状态</dt>
        <dd>{{ session.status }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.page {
  width: min(56rem, 100%);
  margin: 0 auto;
  padding: clamp(1.5rem, 4vw, 2.5rem);
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
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
  font-size: clamp(1.5rem, 3vw, 2rem);
}

.lead {
  margin: 1rem 0 2rem;
  color: var(--color-muted);
  line-height: 1.7;
}

.facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 1rem;
  margin: 0;
}

.facts div {
  padding: 1rem;
  border-radius: 0.75rem;
  background: var(--color-chip);
}

dt {
  color: var(--color-chip-text);
  font-size: 0.8rem;
}

dd {
  margin: 0.35rem 0 0;
  font-weight: 600;
}
</style>
