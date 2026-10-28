<script setup lang="ts">
import { onMounted, ref } from 'vue'

import type { WrongQuestionView } from '@/features/api/types'
import { listWrongQuestions, removeWrongQuestion } from '@/features/practice/api'

const wrongQuestions = ref<WrongQuestionView[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    wrongQuestions.value = await listWrongQuestions()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '错题加载失败'
  } finally {
    loading.value = false
  }
}

async function remove(questionId: string): Promise<void> {
  try {
    await removeWrongQuestion(questionId)
    wrongQuestions.value = wrongQuestions.value.filter((item) => item.questionId !== questionId)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '移出错题集失败'
  }
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">错题集</p>
        <h1>待复习错题</h1>
        <p class="lead">失败自动加入错题集；手动移出不会删除历史记录，再次失败会重新加入。</p>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="state">正在加载错题...</div>
    <div v-else-if="wrongQuestions.length === 0" class="empty-state">错题集是空的。</div>

    <article v-for="item in wrongQuestions" :key="item.questionId" class="wrong-card">
      <div class="card-main">
        <div class="meta">
          <el-tag size="small" type="info" effect="plain">
            {{ item.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
          </el-tag>
          <el-tag size="small" type="warning" effect="plain">{{ item.difficulty }}</el-tag>
          <span class="last-failed">最近失败：{{ formatTime(item.lastFailedAt) }}</span>
        </div>
        <p class="stem">{{ item.stem }}</p>
      </div>
      <el-button text @click="remove(item.questionId)">移出错题集</el-button>
    </article>
  </section>
</template>

<style scoped>
.page {
  width: min(56rem, 100%);
  margin: 0 auto;
}

.page-head {
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

.wrong-card {
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
  align-items: center;
  gap: 0.4rem;
}

.last-failed {
  color: var(--color-muted);
  font-size: 0.85rem;
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
