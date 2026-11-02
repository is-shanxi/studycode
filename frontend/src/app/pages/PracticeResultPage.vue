<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import PracticeResultPanel from '@/app/components/PracticeResultPanel.vue'
import type { ResultItemView, ResultView } from '@/features/api/types'
import { getResult } from '@/features/practice/api'

const route = useRoute()
const sessionId = computed(() => String(route.params.sessionId))
const result = ref<ResultView | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const pendingFills = computed<ResultItemView[]>(
  () =>
    result.value?.items.filter(
      (item) => item.type === 'SINGLE_BLANK' && !item.selfAssessmentResult,
    ) ?? [],
)
const canUseAi = computed(() => result.value?.allowedActions.includes('AI_TUTOR') ?? false)

onMounted(async () => {
  loading.value = true
  try {
    result.value = await getResult(sessionId.value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '结果加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">练习结果</p>
        <h1>本次练习结果</h1>
        <p class="lead">结果按会话创建时固定的题目版本展示，不受后续编辑影响。</p>
      </div>
      <div class="head-links">
        <RouterLink
          v-if="result?.mode === 'SELF_TEST'"
          :to="{ name: 'app-selftest-result', params: { sessionId } }"
        >
          自测分析
        </RouterLink>
        <RouterLink to="/app/library">继续练习</RouterLink>
        <RouterLink to="/app/records">全部记录</RouterLink>
      </div>
    </header>

    <div v-if="loading" class="state">正在加载结果...</div>
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />

    <template v-if="result">
      <el-alert
        v-if="pendingFills.length > 0"
        type="warning"
        :closable="false"
        show-icon
        title="还有填空题没有完成自评，完成全部自评后才会生成最终结果。"
      >
        <div class="pending-links">
          <RouterLink
            v-for="fill in pendingFills"
            :key="fill.itemId"
            :to="{
              name: 'app-fill',
              params: { sessionId: sessionId, itemId: fill.itemId },
            }"
          >
            去自评：{{ fill.stem.slice(0, 24) }}{{ fill.stem.length > 24 ? '…' : '' }}
          </RouterLink>
        </div>
      </el-alert>

      <PracticeResultPanel :result="result" />

      <section v-if="canUseAi" class="ai-entry">
        <div class="ai-entry-head">
          <div>
            <p class="eyebrow">AI 辅导</p>
            <h2>本题讲解</h2>
          </div>
          <p class="ai-entry-note">仅本人已完成会话可访问；上下文默认不包含官方答案和解析。</p>
        </div>
        <div class="ai-links">
          <RouterLink
            v-for="(item, index) in result.items"
            :key="item.itemId"
            :to="{
              name: 'app-assistant',
              query: { sessionItemId: item.itemId, sessionId },
            }"
          >
            第 {{ index + 1 }} 题 · {{ item.stem.slice(0, 22)
            }}{{ item.stem.length > 22 ? '…' : '' }}
          </RouterLink>
        </div>
      </section>
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
  gap: 1rem;
}

.head-links a {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.pending-links {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin-top: 0.5rem;
}

.pending-links a {
  color: var(--color-accent);
}

.ai-entry {
  margin-top: 1.5rem;
  padding: 1.1rem 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
}

.ai-entry-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
}

.ai-entry h2 {
  margin: 0;
  font-size: 1.1rem;
}

.ai-entry-note {
  max-width: 24rem;
  margin: 0;
  color: var(--color-muted);
  font-size: 0.82rem;
  line-height: 1.6;
}

.ai-links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 0.5rem;
}

.ai-links a {
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 0.65rem;
  background: var(--color-chip);
  color: var(--color-text);
  font-size: 0.85rem;
  text-decoration: none;
}

.ai-links a:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
