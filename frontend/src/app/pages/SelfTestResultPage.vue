<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import PracticeResultPanel from '@/app/components/PracticeResultPanel.vue'
import type { ResultView } from '@/features/api/types'
import { getResult } from '@/features/practice/api'

const route = useRoute()
const sessionId = computed(() => String(route.params.sessionId))
const result = ref<ResultView | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const fallback = computed(
  () => !!result.value && result.value.actualCount < result.value.requestedCount,
)

onMounted(async () => {
  loading.value = true
  try {
    result.value = await getResult(sessionId.value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '自测结果加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">自测分析</p>
        <h1>本次自测的题目来源</h1>
        <p class="lead">来源组成与规则快照在创建会话时固定，后续题库调整不会改写本次结果。</p>
      </div>
      <div class="head-links">
        <RouterLink :to="{ name: 'app-result', params: { sessionId } }">完整结果</RouterLink>
        <RouterLink to="/app/records">全部记录</RouterLink>
        <RouterLink to="/app/selftest">再来一次</RouterLink>
      </div>
    </header>

    <div v-if="loading" class="state">正在加载自测分析...</div>
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />

    <template v-if="result">
      <div class="composition-grid">
        <div class="composition-card">
          <span class="label">实际题量</span>
          <strong>{{ result.actualCount }} / {{ result.requestedCount }}</strong>
          <span class="hint">请求 {{ result.requestedCount }} 题</span>
        </div>
        <div class="composition-card original">
          <span class="label">已审核原题</span>
          <strong>{{ result.composition.original }}</strong>
          <span class="hint"
            >未见 {{ result.composition.buckets.unseen }} · 错题
            {{ result.composition.buckets.wrong }} · 已做
            {{ result.composition.buckets.normal }}</span
          >
        </div>
        <div class="composition-card variant">
          <span class="label">已审核 AI 变式</span>
          <strong>{{ result.composition.aiVariant }}</strong>
          <span class="hint">未审核候选不会进入自测</span>
        </div>
      </div>

      <el-alert
        v-if="fallback"
        type="warning"
        show-icon
        :closable="false"
        title="合法题量不足，本次按实际题量组卷；不会引入未审核题目。"
      />

      <PracticeResultPanel :result="result" />
    </template>
  </section>
</template>

<style scoped>
.page {
  width: min(64rem, 100%);
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

.head-links {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}

.head-links a {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.composition-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.composition-card {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background: var(--color-surface);
}

.composition-card.original {
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
}

.composition-card.variant {
  border-color: color-mix(in srgb, #7c3aed 35%, var(--color-border));
}

.label {
  color: var(--color-muted);
  font-size: 0.8rem;
}

strong {
  font-size: 1.6rem;
}

.hint {
  color: var(--color-muted);
  font-size: 0.8rem;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
