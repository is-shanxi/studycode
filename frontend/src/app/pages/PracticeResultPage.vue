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

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
