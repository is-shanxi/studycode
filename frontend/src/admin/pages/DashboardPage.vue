<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import type { DashboardView } from '@/features/api/types'
import { getDashboard } from '@/features/admin/api'

const dashboard = ref<DashboardView | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const numberFormat = new Intl.NumberFormat('zh-CN')

const meters = computed(() => {
  if (!dashboard.value) {
    return []
  }
  const data = dashboard.value
  return [
    {
      label: '练习完成率',
      value: data.practiceCompletionRate,
      detail: `${numberFormat.format(data.completedPracticeSessions)} / ${numberFormat.format(data.totalPracticeSessions)} 次会话已完成`,
    },
    {
      label: '客观题错误率',
      value: data.questionErrorRate,
      detail: `${numberFormat.format(data.objectiveFailedItems)} / ${numberFormat.format(data.objectiveGradedItems)} 道已判分单选题`,
    },
    {
      label: '填空自动匹配率',
      value: data.autoMatchRate,
      detail: `${numberFormat.format(data.autoMatchedAnswers)} / ${numberFormat.format(data.fillAnswers)} 次填空作答匹配`,
    },
    {
      label: '自评通过率',
      value: data.selfAssessmentPassRate,
      detail: `${numberFormat.format(data.selfPassedItems)} 通过 · ${numberFormat.format(data.selfFailedItems)} 未通过`,
    },
  ]
})

function percent(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

function formatNumber(value: number): string {
  return numberFormat.format(value)
}

function formatCost(value: number): string {
  return Number(value).toFixed(4)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    dashboard.value = await getDashboard()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '看板加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="dashboard">
    <header class="page-head">
      <div>
        <p class="eyebrow">运营看板</p>
        <h1>学习与治理概览</h1>
        <p class="lead">客观判分、填空自动匹配和自评结果分别统计，不合并为统一的“正确率”。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div v-if="!dashboard && loading" class="state">正在加载指标...</div>

    <template v-if="dashboard">
      <section class="hero" aria-label="近 7 天活跃用户">
        <p class="hero-label">近 7 天活跃用户</p>
        <p class="hero-value">{{ formatNumber(dashboard.activeUsersLast7Days) }}</p>
        <p class="hero-note">按练习会话中的去重用户统计</p>
      </section>

      <section class="kpi-row" aria-label="关键指标">
        <article class="stat-tile">
          <p class="tile-label">正常账号</p>
          <p class="tile-value">{{ formatNumber(dashboard.activeUsers) }}</p>
          <p class="tile-note">状态为 ACTIVE 的用户</p>
        </article>
        <article class="stat-tile">
          <p class="tile-label">练习会话</p>
          <p class="tile-value">{{ formatNumber(dashboard.totalPracticeSessions) }}</p>
          <p class="tile-note">已完成 {{ formatNumber(dashboard.completedPracticeSessions) }} 次</p>
        </article>
        <article class="stat-tile">
          <p class="tile-label">待审核版本</p>
          <p class="tile-value">{{ formatNumber(dashboard.pendingReviewVersions) }}</p>
          <p class="tile-note">等待管理员审核的题目版本</p>
        </article>
        <article class="stat-tile">
          <p class="tile-label">待处理举报</p>
          <p class="tile-value">{{ formatNumber(dashboard.openReports) }}</p>
          <p class="tile-note">内容举报与题目纠错合计</p>
        </article>
        <article class="stat-tile">
          <p class="tile-label">AI 调用次数</p>
          <p class="tile-value">{{ formatNumber(dashboard.aiRequestCount) }}</p>
          <p class="tile-note">按日用量账本累计</p>
        </article>
        <article class="stat-tile">
          <p class="tile-label">AI 成本</p>
          <p class="tile-value">{{ formatCost(dashboard.aiCost) }}</p>
          <p class="tile-note">计费单位累计值</p>
        </article>
      </section>

      <section class="meters" aria-label="比例指标">
        <article v-for="meter in meters" :key="meter.label" class="meter-card">
          <div class="meter-head">
            <span>{{ meter.label }}</span>
            <strong>{{ percent(meter.value) }}</strong>
          </div>
          <div
            class="meter-track"
            role="progressbar"
            :aria-label="meter.label"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="Math.round(meter.value * 100)"
          >
            <div
              class="meter-fill"
              :style="{ width: `${Math.min(100, meter.value * 100)}%` }"
            ></div>
          </div>
          <p class="meter-note">{{ meter.detail }}</p>
        </article>
      </section>

      <p class="footnote">
        指标来自 PostgreSQL 直接聚合，不依赖独立 BI 存储；阈值和正式验收口径仍待产品确认。
      </p>
    </template>
  </section>
</template>

<style scoped>
.dashboard {
  width: min(68rem, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.eyebrow {
  margin: 0 0 0.4rem;
  color: #3157c8;
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

.hero {
  padding: 1.4rem 1.5rem;
  border: 1px solid #dbe2ef;
  border-radius: 1rem;
  background: #ffffff;
}

.hero-label {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.hero-value {
  margin: 0.25rem 0 0;
  font-size: clamp(3rem, 7vw, 3.75rem);
  font-weight: 650;
  line-height: 1.05;
  letter-spacing: -0.02em;
}

.hero-note {
  margin: 0.4rem 0 0;
  color: var(--color-muted);
  font-size: 0.8rem;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
  gap: 0.9rem;
}

.stat-tile {
  padding: 1rem 1.1rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.85rem;
  background: #ffffff;
}

.tile-label {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.82rem;
}

.tile-value {
  margin: 0.35rem 0 0;
  font-size: 1.7rem;
  font-weight: 650;
  line-height: 1.2;
}

.tile-note {
  margin: 0.3rem 0 0;
  color: var(--color-muted);
  font-size: 0.78rem;
}

.meters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
  gap: 0.9rem;
}

.meter-card {
  padding: 1rem 1.1rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.85rem;
  background: #ffffff;
}

.meter-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.meter-head strong {
  color: var(--color-text);
  font-size: 1.05rem;
}

.meter-track {
  height: 0.55rem;
  margin-top: 0.55rem;
  border-radius: 999px;
  background: #cde2fb;
  overflow: hidden;
}

.meter-fill {
  height: 100%;
  border-radius: 999px;
  background: #2a78d6;
}

.meter-note {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
  font-size: 0.78rem;
}

.footnote {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.8rem;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
