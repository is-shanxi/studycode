<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="page">
    <p class="eyebrow">管理端</p>
    <h1>运营控制台</h1>
    <p class="lead">
      P1 提供题目草稿、审核发布、下线、版本历史和批量导入。用户管理、举报处理、看板和审计查询将在 P2
      接入。
    </p>

    <div class="quick-grid">
      <RouterLink class="quick-card" to="/admin/questions">
        <span class="quick-title">题库管理</span>
        <span class="quick-desc">检索、编辑、查看版本与下线题目</span>
      </RouterLink>
      <RouterLink class="quick-card" to="/admin/review">
        <span class="quick-title">待审核</span>
        <span class="quick-desc">审核通过并发布，或退回草稿</span>
      </RouterLink>
      <RouterLink class="quick-card" to="/admin/import">
        <span class="quick-title">批量导入</span>
        <span class="quick-desc">先校验预览，无错误再确认导入</span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.page {
  width: min(56rem, 100%);
  margin: 0 auto;
  padding: clamp(1.5rem, 4vw, 2.5rem);
  border: 1px solid #dbe2ef;
  border-radius: 1rem;
  background: #ffffff;
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: #3157c8;
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
  color: #5b6780;
  line-height: 1.7;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 1rem;
}

.quick-card {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 1.1rem 1.2rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #f8fafc;
  color: inherit;
  text-decoration: none;
}

.quick-title {
  font-weight: 700;
}

.quick-desc {
  color: #5b6780;
  font-size: 0.85rem;
}
</style>
