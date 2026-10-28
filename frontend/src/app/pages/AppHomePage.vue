<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { useSessionStore } from '@/features/auth/store'

const session = useSessionStore()
</script>

<template>
  <section class="page">
    <p class="eyebrow">P1 核心闭环</p>
    <h1>欢迎回来，{{ session.user?.nickname ?? session.user?.username ?? '同学' }}</h1>
    <p class="lead">
      从题库筛选开始一次 10
      题练习，提交后查看结果；填空题需要逐题自评，错题和收藏会自动进入学习记录。
    </p>

    <div class="quick-grid">
      <RouterLink class="quick-card" to="/app/library">
        <span class="quick-title">题库筛选</span>
        <span class="quick-desc">按目录、标签、难度和关键词选题</span>
      </RouterLink>
      <RouterLink class="quick-card" to="/app/records">
        <span class="quick-title">刷题记录</span>
        <span class="quick-desc">查看历史成绩和当时题目版本</span>
      </RouterLink>
      <RouterLink class="quick-card" to="/app/favorites">
        <span class="quick-title">我的收藏</span>
        <span class="quick-desc">收藏稳定题目身份，随时回看</span>
      </RouterLink>
      <RouterLink class="quick-card" to="/app/wrong-questions">
        <span class="quick-title">错题集</span>
        <span class="quick-desc">失败自动加入，可手动移出</span>
      </RouterLink>
    </div>

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
  width: min(64rem, 100%);
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

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 1rem;
  margin-bottom: 2rem;
}

.quick-card {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 1.1rem 1.2rem;
  border: 1px solid var(--color-border);
  border-radius: 0.8rem;
  background: var(--color-chip);
  color: inherit;
  text-decoration: none;
  transition:
    transform 120ms ease,
    border-color 120ms ease;
}

.quick-card:hover {
  transform: translateY(-2px);
  border-color: var(--color-accent);
}

.quick-title {
  font-weight: 700;
}

.quick-desc {
  color: var(--color-chip-text);
  font-size: 0.85rem;
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
