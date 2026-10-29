<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import type { AuditLogView, PageResponse } from '@/features/api/types'
import { searchAuditLogs } from '@/features/admin/api'

const logs = ref<PageResponse<AuditLogView>>({ items: [], page: 0, size: 20, total: 0 })
const loading = ref(false)
const error = ref<string | null>(null)
const filters = reactive({
  actorUserId: '',
  action: '',
  targetType: '',
  outcome: '',
})

async function load(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  try {
    logs.value = await searchAuditLogs({
      actorUserId: filters.actorUserId.trim() || undefined,
      action: filters.action.trim() || undefined,
      targetType: filters.targetType.trim() || undefined,
      outcome: filters.outcome || undefined,
      page,
      size: logs.value.size,
    })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '审计日志加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  void load(page - 1)
}

function outcomeType(outcome: string): 'success' | 'danger' | 'warning' {
  if (outcome === 'SUCCESS') {
    return 'success'
  }
  return outcome === 'FAILED' ? 'danger' : 'warning'
}

function outcomeLabel(outcome: string): string {
  if (outcome === 'SUCCESS') {
    return '成功'
  }
  return outcome === 'FAILED' ? '失败' : '拒绝'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">操作审计</p>
        <h1>审计日志</h1>
        <p class="lead">
          记录题目发布、内容治理、账号管理和公告等管理操作；日志只追加，不提供修改入口。
        </p>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div class="filters">
      <el-input v-model="filters.actorUserId" placeholder="操作者用户 ID" clearable />
      <el-input
        v-model="filters.action"
        placeholder="操作，如 USER_DISABLE"
        clearable
        @keyup.enter="load(0)"
      />
      <el-input v-model="filters.targetType" placeholder="目标类型，如 APP_USER" clearable />
      <el-select v-model="filters.outcome" placeholder="结果" clearable @change="load(0)">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="拒绝" value="REJECTED" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load(0)">查询</el-button>
    </div>

    <p class="result-count">共 {{ logs.total }} 条审计记录</p>

    <el-table v-loading="loading" :data="logs.items" stripe>
      <el-table-column label="时间" min-width="170">
        <template #default="{ row }">
          {{ new Date(row.createdAt).toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column prop="actorUserId" label="操作者" min-width="180" />
      <el-table-column prop="action" label="操作" min-width="190" />
      <el-table-column label="目标" min-width="220">
        <template #default="{ row }">
          <span>{{ row.targetType }}</span>
          <span v-if="row.targetId" class="target-id">{{ row.targetId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="outcomeType(row.outcome)">
            {{ outcomeLabel(row.outcome) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
      <el-table-column prop="traceId" label="Trace ID" min-width="160" show-overflow-tooltip />
    </el-table>

    <el-pagination
      v-if="logs.total > logs.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="logs.page + 1"
      :page-size="logs.size"
      :total="logs.total"
      @current-change="changePage"
    />
  </section>
</template>

<style scoped>
.page {
  width: min(72rem, 100%);
  margin: 0 auto;
}

.page-head {
  margin-bottom: 1.25rem;
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

.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 0.75rem;
  margin: 1rem 0;
  padding: 1rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.result-count {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.target-id {
  margin-left: 0.4rem;
  color: var(--color-muted);
  font-size: 0.78rem;
}

.pagination {
  margin-top: 1rem;
}
</style>
