<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import type { FavoriteView } from '@/features/api/types'
import { listFavorites, removeFavorite } from '@/features/practice/api'

const favorites = ref<FavoriteView[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    favorites.value = await listFavorites()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '收藏加载失败'
  } finally {
    loading.value = false
  }
}

async function remove(questionId: string): Promise<void> {
  try {
    await removeFavorite(questionId)
    favorites.value = favorites.value.filter((item) => item.questionId !== questionId)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '取消收藏失败'
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">收藏夹</p>
        <h1>我的收藏</h1>
        <p class="lead">收藏关联稳定题目身份，题目发布新版本后仍保持收藏。</p>
      </div>
      <RouterLink class="back-link" to="/app/library">去题库</RouterLink>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="state">正在加载收藏...</div>
    <div v-else-if="favorites.length === 0" class="empty-state">还没有收藏题目。</div>

    <article v-for="item in favorites" :key="item.questionId" class="favorite-card">
      <div class="card-main">
        <div class="meta">
          <el-tag size="small" type="info" effect="plain">
            {{ item.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
          </el-tag>
          <el-tag size="small" type="warning" effect="plain">{{ item.difficulty }}</el-tag>
          <el-tag v-if="item.status !== 'PUBLISHED'" size="small" type="danger" effect="plain">
            {{ item.status }}
          </el-tag>
        </div>
        <p class="stem">{{ item.stem }}</p>
      </div>
      <el-button text type="danger" @click="remove(item.questionId)">取消收藏</el-button>
    </article>
  </section>
</template>

<style scoped>
.page {
  width: min(56rem, 100%);
  margin: 0 auto;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.eyebrow {
  margin: 0 0 0.4rem;
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(1.4rem, 3vw, 1.9rem);
}

.lead {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
}

.back-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.favorite-card {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.75rem;
  background: var(--color-surface);
}

.card-main {
  flex: 1 1 20rem;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.stem {
  margin: 0.6rem 0 0;
  line-height: 1.7;
}

.state,
.empty-state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
