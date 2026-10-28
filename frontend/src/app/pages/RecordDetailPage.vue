<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import PracticeResultPanel from '@/app/components/PracticeResultPanel.vue'
import type { ResultView } from '@/features/api/types'
import { getRecord } from '@/features/practice/api'

const route = useRoute()
const sessionId = computed(() => String(route.params.sessionId))
const result = ref<ResultView | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  loading.value = true
  try {
    result.value = await getRecord(sessionId.value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '记录加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">历史记录</p>
        <h1>记录详情</h1>
        <p class="lead">历史记录始终显示作答当时的题目版本和答案。</p>
      </div>
      <RouterLink class="back-link" to="/app/records">返回记录列表</RouterLink>
    </header>

    <div v-if="loading" class="state">正在加载记录...</div>
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />

    <PracticeResultPanel v-if="result" :result="result" />
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

.back-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
