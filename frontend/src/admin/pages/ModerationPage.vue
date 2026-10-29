<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import type {
  ModerationReportView,
  PageResponse,
  ReportSource,
  ReportStatus,
} from '@/features/api/types'
import { listModerationReports, resolveModerationReport } from '@/features/admin/api'

const reports = ref<PageResponse<ModerationReportView>>({
  items: [],
  page: 0,
  size: 10,
  total: 0,
})
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const filters = reactive({ source: '', status: '' })

const selected = ref<ModerationReportView | null>(null)
const dialogVisible = ref(false)
const resolution = ref<'RESOLVED' | 'REJECTED'>('RESOLVED')
const moderationAction = ref<'NONE' | 'HIDE' | 'REMOVE'>('NONE')
const note = ref('')
const submitting = ref(false)

const statusLabels: Record<ReportStatus, string> = {
  OPEN: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已处理',
  REJECTED: '未采纳',
}

const targetLabels: Record<string, string> = {
  POST: '帖子',
  COMMENT: '评论',
  QUESTION: '题目',
  STEM: '题干',
  ANSWER: '答案',
  EXPLANATION: '解析',
  TAG: '标签',
  OTHER: '其他',
}

async function load(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  try {
    reports.value = await listModerationReports({
      source: filters.source || undefined,
      status: filters.status || undefined,
      page,
      size: reports.value.size,
    })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '举报加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  void load(page - 1)
}

function openResolve(report: ModerationReportView): void {
  selected.value = report
  resolution.value = 'RESOLVED'
  moderationAction.value = 'NONE'
  note.value = ''
  dialogVisible.value = true
}

function statusType(status: ReportStatus): 'warning' | 'success' | 'info' | 'danger' {
  if (status === 'OPEN') {
    return 'warning'
  }
  if (status === 'PROCESSING') {
    return 'info'
  }
  return status === 'RESOLVED' ? 'success' : 'danger'
}

async function submitResolution(): Promise<void> {
  if (!selected.value) {
    return
  }
  submitting.value = true
  error.value = null
  try {
    const payload: {
      source: ReportSource
      resolution: 'RESOLVED' | 'REJECTED'
      moderationAction?: 'HIDE' | 'REMOVE'
      note?: string
    } = {
      source: selected.value.source,
      resolution: resolution.value,
      note: note.value.trim() || undefined,
    }
    if (
      selected.value.source === 'CONTENT' &&
      resolution.value === 'RESOLVED' &&
      moderationAction.value !== 'NONE'
    ) {
      payload.moderationAction = moderationAction.value
    }
    const result = await resolveModerationReport(selected.value.reportId, payload)
    notice.value =
      result.targetStatus === undefined
        ? '举报已处理，举报人将收到站内通知。'
        : `举报已处理，目标内容状态：${result.targetStatus}。`
    dialogVisible.value = false
    await load(reports.value.page)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '处理失败'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">内容治理</p>
        <h1>举报与纠错处理</h1>
        <p class="lead">
          内容举报可以隐藏或下架目标内容；题目纠错只记录处理结果，修正内容需走新版本发布流程。
        </p>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="true" />

    <div class="filters">
      <el-select v-model="filters.source" placeholder="来源" clearable @change="load(0)">
        <el-option label="内容举报" value="CONTENT" />
        <el-option label="题目纠错" value="CORRECTION" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable @change="load(0)">
        <el-option label="待处理" value="OPEN" />
        <el-option label="处理中" value="PROCESSING" />
        <el-option label="已处理" value="RESOLVED" />
        <el-option label="未采纳" value="REJECTED" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load(0)">刷新</el-button>
    </div>

    <p class="result-count">共 {{ reports.total }} 条待处理与历史记录</p>

    <el-table v-loading="loading" :data="reports.items" stripe>
      <el-table-column label="来源" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.source === 'CORRECTION' ? 'warning' : 'info'">
            {{ row.source === 'CORRECTION' ? '题目纠错' : '内容举报' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标" width="120">
        <template #default="{ row }">
          {{ targetLabels[row.targetType] ?? row.targetType }}
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="原因 / 说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">
            {{ statusLabels[row.status as ReportStatus] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reporterName" label="举报人" width="120" />
      <el-table-column label="提交时间" min-width="170">
        <template #default="{ row }">
          {{ new Date(row.createdAt).toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button
            text
            type="primary"
            :disabled="row.status === 'RESOLVED' || row.status === 'REJECTED'"
            @click="openResolve(row)"
          >
            处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="reports.total > reports.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="reports.page + 1"
      :page-size="reports.size"
      :total="reports.total"
      @current-change="changePage"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="selected?.source === 'CORRECTION' ? '处理题目纠错' : '处理内容举报'"
      width="min(34rem, 92vw)"
    >
      <div v-if="selected" class="dialog-body">
        <p class="target-info">
          <el-tag size="small" type="info">
            {{ targetLabels[selected.targetType] ?? selected.targetType }}
          </el-tag>
          <code>{{ selected.targetId }}</code>
        </p>
        <p class="reason">{{ selected.reason }}</p>

        <label>处理结果</label>
        <el-radio-group v-model="resolution">
          <el-radio-button value="RESOLVED">采纳并处理</el-radio-button>
          <el-radio-button value="REJECTED">不采纳</el-radio-button>
        </el-radio-group>

        <template v-if="selected.source === 'CONTENT' && resolution === 'RESOLVED'">
          <label>内容处置</label>
          <el-select v-model="moderationAction">
            <el-option label="仅记录结果，不改动内容" value="NONE" />
            <el-option label="隐藏内容" value="HIDE" />
            <el-option label="下架内容" value="REMOVE" />
          </el-select>
        </template>

        <label>处理说明（可选）</label>
        <el-input
          v-model="note"
          type="textarea"
          :rows="3"
          maxlength="2000"
          show-word-limit
          placeholder="说明处理依据，便于审计"
        />
        <p class="hint">处理结果会写入审计日志，并通过站内通知发送给举报人。</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResolution">
          确认处理
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page {
  width: min(68rem, 100%);
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

.pagination {
  margin-top: 1rem;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.dialog-body label {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.target-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
}

.target-info code {
  color: var(--color-muted);
  font-size: 0.78rem;
}

.reason {
  margin: 0;
  padding: 0.7rem 0.8rem;
  border-radius: 0.6rem;
  background: var(--color-chip);
  line-height: 1.6;
}

.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.8rem;
}
</style>
