<script setup lang="ts">
import { computed } from 'vue'

import type { AiQuotaView } from '@/features/api/types'

const props = defineProps<{ quota: AiQuotaView | null }>()

const usagePercent = computed(() => {
  if (!props.quota || props.quota.dailyLimit === 0) {
    return 0
  }
  return Math.min(100, Math.round((props.quota.used / props.quota.dailyLimit) * 100))
})

const turnPercent = computed(() => {
  if (!props.quota || props.quota.sessionTurnLimit === 0) {
    return 0
  }
  return Math.min(100, Math.round((props.quota.turnsUsed / props.quota.sessionTurnLimit) * 100))
})

const budgetPercent = computed(() => {
  if (!props.quota || props.quota.teamBudget.limit <= 0) {
    return 0
  }
  return Math.min(
    100,
    Math.round((props.quota.teamBudget.used / props.quota.teamBudget.limit) * 100),
  )
})
</script>

<template>
  <section v-if="quota" class="quota-panel">
    <header class="quota-head">
      <div>
        <p class="eyebrow">AI 额度</p>
        <strong>{{ quota.remaining }} / {{ quota.dailyLimit }} 次可用</strong>
      </div>
      <el-tag :type="quota.teamBudget.available ? 'success' : 'danger'" effect="plain">
        {{ quota.teamBudget.available ? '团队预算可用' : '团队预算已用尽' }}
      </el-tag>
    </header>

    <div class="meter">
      <span>今日个人请求</span>
      <el-progress
        :percentage="usagePercent"
        :status="quota.remaining === 0 ? 'exception' : undefined"
        :show-text="false"
      />
      <small>{{ quota.used }} / {{ quota.dailyLimit }}</small>
    </div>

    <div class="meter">
      <span>本会话轮次</span>
      <el-progress :percentage="turnPercent" :show-text="false" />
      <small>{{ quota.turnsUsed }} / {{ quota.sessionTurnLimit }}</small>
    </div>

    <div class="meter">
      <span>团队日预算</span>
      <el-progress :percentage="budgetPercent" :show-text="false" />
      <small>{{ quota.teamBudget.used }} / {{ quota.teamBudget.limit }}</small>
    </div>

    <p class="policy">
      {{
        quota.officialAnswerContextEnabled
          ? '管理员已允许将官方答案和解析纳入本题上下文。'
          : '本题上下文默认不包含官方答案和解析。'
      }}
      单条输入最多 {{ quota.maxInputChars }} 字。
    </p>
  </section>
</template>

<style scoped>
.quota-panel {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.9rem;
  background: var(--color-surface);
}

.quota-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.5rem;
}

.quota-head strong {
  font-size: 1.15rem;
}

.eyebrow {
  margin: 0 0 0.25rem;
  color: var(--color-accent);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.meter {
  display: grid;
  grid-template-columns: 7.5rem minmax(0, 1fr) 4.5rem;
  align-items: center;
  gap: 0.6rem;
  color: var(--color-muted);
  font-size: 0.82rem;
}

.meter small {
  text-align: right;
}

.policy {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}
</style>
