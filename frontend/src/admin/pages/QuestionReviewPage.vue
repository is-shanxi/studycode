<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import type { AdminQuestionSummary } from '@/features/api/types'
import {
  approveVersion,
  getAdminQuestion,
  rejectVersion,
  searchAdminQuestions,
} from '@/features/admin/api'

const questions = ref<AdminQuestionSummary[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await searchAdminQuestions({ status: 'PENDING_REVIEW', size: 100 })
    questions.value = page.items
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '待审核列表加载失败'
  } finally {
    loading.value = false
  }
}

async function pendingVersionId(questionId: string): Promise<string | null> {
  const detail = await getAdminQuestion(questionId)
  return detail.versions.find((version) => version.status === 'PENDING_REVIEW')?.versionId ?? null
}

async function approve(question: AdminQuestionSummary): Promise<void> {
  busy.value = true
  error.value = null
  notice.value = null
  try {
    const versionId = await pendingVersionId(question.questionId)
    if (!versionId) {
      error.value = '未找到待审核版本'
      return
    }
    await approveVersion(versionId, true)
    notice.value = '已通过并发布'
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '审核失败'
  } finally {
    busy.value = false
  }
}

async function reject(question: AdminQuestionSummary): Promise<void> {
  const comment = window.prompt('请输入退回原因（可留空）') ?? undefined
  busy.value = true
  error.value = null
  notice.value = null
  try {
    const versionId = await pendingVersionId(question.questionId)
    if (!versionId) {
      error.value = '未找到待审核版本'
      return
    }
    await rejectVersion(versionId, comment)
    notice.value = '已退回草稿'
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '退回失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">内容审核</p>
        <h1>待审核题目</h1>
        <p class="lead">AI 生成的候选题和人工草稿都必须经过审核后才能发布。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="false" />

    <div v-if="loading" class="state">正在加载待审核题目...</div>
    <div v-else-if="questions.length === 0" class="empty-state">当前没有待审核题目。</div>

    <article v-for="question in questions" :key="question.questionId" class="review-card">
      <div class="card-main">
        <div class="meta">
          <el-tag size="small" type="info" effect="plain">
            {{ question.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
          </el-tag>
          <el-tag size="small" type="warning" effect="plain">待审核</el-tag>
          <el-tag size="small" effect="plain">{{ question.difficulty }}</el-tag>
        </div>
        <p class="stem">{{ question.stem ?? '（无题干）' }}</p>
      </div>
      <div class="actions">
        <RouterLink
          class="edit-link"
          :to="{ name: 'admin-question-edit', params: { questionId: question.questionId } }"
        >
          查看详情
        </RouterLink>
        <el-button type="success" :loading="busy" @click="approve(question)">通过并发布</el-button>
        <el-button type="danger" plain :loading="busy" @click="reject(question)">退回</el-button>
      </div>
    </article>
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

.review-card {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 0.75rem;
  padding: 1rem 1.1rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.75rem;
  background: #ffffff;
}

.card-main {
  flex: 1 1 22rem;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.stem {
  margin: 0.6rem 0 0;
  line-height: 1.7;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.edit-link {
  color: #3157c8;
  font-weight: 600;
  text-decoration: none;
}

.state,
.empty-state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
