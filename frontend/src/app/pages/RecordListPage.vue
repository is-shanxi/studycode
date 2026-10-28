<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import type { PageResponse, RecordSummary } from '@/features/api/types'
import { listRecords } from '@/features/practice/api'

const records = ref<PageResponse<RecordSummary>>({ items: [], page: 0, size: 10, total: 0 })
const loading = ref(false)
const error = ref<string | null>(null)
const filters = reactive({ mode: '', status: '' })

onMounted(() => {
  void load()
})

async function load(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  try {
    records.value = await listRecords({
      mode: filters.mode || undefined,
      status: filters.status || undefined,
      page,
      size: records.value.size,
    })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '记录加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  void load(page - 1)
}

function formatTime(value?: string): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '—'
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    IN_PROGRESS: '进行中',
    SUBMITTED: '已提交',
    PENDING_SELF_ASSESSMENT: '待自评',
    COMPLETED: '已完成',
    ABANDONED: '已放弃',
  }
  return labels[status] ?? status
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">学习记录</p>
        <h1>刷题记录</h1>
        <p class="lead">客观单选成绩与填空题自评结果分别统计。</p>
      </div>
      <div class="filters">
        <el-select v-model="filters.mode" placeholder="模式" clearable @change="load(0)">
          <el-option label="普通练习" value="NORMAL" />
          <el-option label="个性化自测" value="SELF_TEST" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable @change="load(0)">
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="待自评" value="PENDING_SELF_ASSESSMENT" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已放弃" value="ABANDONED" />
        </el-select>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div v-if="loading" class="state">正在加载记录...</div>
    <div v-else-if="records.items.length === 0" class="empty-state">还没有练习记录。</div>

    <article v-for="record in records.items" :key="record.sessionId" class="record-card">
      <div class="record-main">
        <span class="record-mode">
          {{ record.mode === 'NORMAL' ? '普通练习' : '个性化自测' }}
        </span>
        <el-tag size="small" type="info" effect="plain">{{ statusLabel(record.status) }}</el-tag>
        <span class="record-time">{{ formatTime(record.completedAt ?? record.startedAt) }}</span>
      </div>
      <div class="record-stats">
        <span>题量 {{ record.actualCount }}</span>
        <span>单选 {{ record.objectiveCorrect }} / {{ record.objectiveAnswered }} 正确</span>
      </div>
      <RouterLink
        class="detail-link"
        :to="{ name: 'app-record-detail', params: { sessionId: record.sessionId } }"
      >
        查看详情
      </RouterLink>
    </article>

    <el-pagination
      v-if="records.total > records.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="records.page + 1"
      :page-size="records.size"
      :total="records.total"
      @current-change="changePage"
    />
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

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.record-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.25rem;
  margin-bottom: 0.75rem;
  padding: 0.9rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.75rem;
  background: var(--color-surface);
}

.record-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  flex: 1 1 18rem;
}

.record-mode {
  font-weight: 700;
}

.record-time {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.record-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  color: var(--color-muted);
  font-size: 0.9rem;
}

.detail-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.pagination {
  margin-top: 1rem;
}

.state,
.empty-state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
