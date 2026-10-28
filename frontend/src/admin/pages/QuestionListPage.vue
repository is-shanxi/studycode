<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import type { AdminQuestionSummary, PageResponse } from '@/features/api/types'
import { offlineQuestion, searchAdminQuestions } from '@/features/admin/api'

const router = useRouter()
const questions = ref<PageResponse<AdminQuestionSummary>>({
  items: [],
  page: 0,
  size: 10,
  total: 0,
})
const loading = ref(false)
const error = ref<string | null>(null)
const filters = reactive({ keyword: '', status: '', type: '' })

onMounted(() => {
  void load()
})

async function load(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  try {
    questions.value = await searchAdminQuestions({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      type: filters.type || undefined,
      page,
      size: questions.value.size,
    })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '题目加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  void load(page - 1)
}

async function takeOffline(question: AdminQuestionSummary): Promise<void> {
  if (!window.confirm('下线后该题不会进入新练习，历史记录仍可查看。确定下线吗？')) {
    return
  }
  try {
    await offlineQuestion(question.questionId)
    await load(questions.value.page)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '下线失败'
  }
}

function edit(question: AdminQuestionSummary): void {
  void router.push({ name: 'admin-question-edit', params: { questionId: question.questionId } })
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">题库管理</p>
        <h1>题目列表</h1>
        <p class="lead">管理端可以检索题干、解析、选项和别名等全部字段。</p>
      </div>
      <el-button type="primary" size="large" @click="router.push({ name: 'admin-question-new' })">
        新建题目
      </el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div class="filters">
      <el-input v-model="filters.keyword" placeholder="关键词" clearable @keyup.enter="load(0)" />
      <el-select v-model="filters.status" placeholder="状态" clearable @change="load(0)">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="待审核" value="PENDING_REVIEW" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已下线" value="OFFLINE" />
      </el-select>
      <el-select v-model="filters.type" placeholder="题型" clearable @change="load(0)">
        <el-option label="单选题" value="SINGLE_CHOICE" />
        <el-option label="填空题" value="SINGLE_BLANK" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load(0)">搜索</el-button>
    </div>

    <p class="result-count">共 {{ questions.total }} 道题</p>
    <div v-if="loading" class="state">正在加载题目...</div>

    <article v-for="question in questions.items" :key="question.questionId" class="question-card">
      <div class="card-main">
        <div class="meta">
          <el-tag size="small" type="info" effect="plain">
            {{ question.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
          </el-tag>
          <el-tag size="small" :type="question.status === 'PUBLISHED' ? 'success' : 'warning'">
            {{ question.status }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">{{ question.difficulty }}</el-tag>
        </div>
        <p class="stem">{{ question.stem ?? '（无题干）' }}</p>
      </div>
      <div class="actions">
        <el-button text type="primary" @click="edit(question)">编辑 / 版本</el-button>
        <el-button
          v-if="question.status === 'PUBLISHED'"
          text
          type="danger"
          @click="takeOffline(question)"
        >
          下线
        </el-button>
      </div>
    </article>

    <div v-if="!loading && questions.items.length === 0" class="empty-state">没有匹配的题目。</div>

    <el-pagination
      v-if="questions.total > questions.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="questions.page + 1"
      :page-size="questions.size"
      :total="questions.total"
      @current-change="changePage"
    />
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

.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
  padding: 1rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.result-count {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.question-card {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
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
  gap: 0.25rem;
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
