<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import ContentReportDialog from '@/app/components/ContentReportDialog.vue'
import DiscussionComposer from '@/app/components/DiscussionComposer.vue'
import type {
  DiscussionPostSummary,
  PageResponse,
  PublicQuestionView,
  ReportCreated,
} from '@/features/api/types'
import { listDiscussions } from '@/features/community/api'
import { ApiError } from '@/features/http/client'
import { getQuestion } from '@/features/practice/api'

const route = useRoute()
const questionId = String(route.params.questionId)

const question = ref<PublicQuestionView | null>(null)
const posts = ref<PageResponse<DiscussionPostSummary>>({
  items: [],
  page: 0,
  size: 10,
  total: 0,
})
const loading = ref(false)
const blocked = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

const reportVisible = ref(false)
const reportMode = ref<'post' | 'comment' | 'correction'>('post')
const reportTargetId = ref('')

async function loadQuestion(): Promise<void> {
  try {
    question.value = await getQuestion(questionId)
  } catch {
    question.value = null
    notice.value = '题目未发布或已下线，仅保留历史讨论。'
  }
}

async function loadPosts(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  blocked.value = false
  try {
    posts.value = await listDiscussions(questionId, { page, size: posts.value.size })
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 409) {
      blocked.value = true
      error.value = '本题练习尚未提交，讨论入口暂时隐藏。完成提交后可查看。'
    } else {
      error.value = caught instanceof Error ? caught.message : '讨论加载失败'
    }
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  void loadPosts(page - 1)
}

function onPostCreated(): void {
  void loadPosts(0)
}

function openReport(post: DiscussionPostSummary): void {
  reportMode.value = 'post'
  reportTargetId.value = post.postId
  reportVisible.value = true
}

function openCorrection(): void {
  reportMode.value = 'correction'
  reportTargetId.value = questionId
  reportVisible.value = true
}

function onReportSubmitted(report: ReportCreated): void {
  notice.value =
    report.status === 'OPEN'
      ? '已提交，管理员处理后会通过站内通知告知你。'
      : `已存在待处理举报（${report.status}），无需重复提交。`
}

onMounted(async () => {
  await loadQuestion()
  await loadPosts()
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">题目讨论</p>
        <h1>讨论与题解</h1>
        <p class="lead">讨论绑定题目版本；历史版本内容不会随新版本发布而改写。</p>
      </div>
      <el-button :disabled="!question" @click="openCorrection">报告题目错误</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="info" show-icon :closable="true" />

    <div v-if="question" class="question-context">
      <el-tag size="small" type="success" effect="plain">当前发布版本</el-tag>
      <p class="stem">{{ question.stem }}</p>
    </div>

    <DiscussionComposer
      v-if="question && !blocked"
      :question-id="questionId"
      :question-version-id="question.questionVersionId"
      @created="onPostCreated"
    />

    <div class="list-head">
      <h2>全部讨论</h2>
      <span>{{ posts.total }} 条</span>
    </div>

    <div v-if="loading" class="state">正在加载讨论...</div>

    <article v-for="post in posts.items" :key="post.postId" class="post-card">
      <div class="post-main">
        <div class="post-meta">
          <el-tag size="small" :type="post.versionCurrent ? 'success' : 'info'" effect="plain">
            题目版本 v{{ post.versionNo }}{{ post.versionCurrent ? ' · 当前' : ' · 历史' }}
          </el-tag>
          <span>{{ post.authorName }}</span>
          <span>{{ new Date(post.createdAt).toLocaleString() }}</span>
          <span>{{ post.commentCount }} 条评论</span>
        </div>
        <RouterLink
          class="post-title"
          :to="{ name: 'app-discussion-detail', params: { postId: post.postId } }"
        >
          {{ post.title }}
        </RouterLink>
        <p v-if="post.bodyPreview" class="post-preview">{{ post.bodyPreview }}</p>
      </div>
      <div class="post-actions">
        <RouterLink :to="{ name: 'app-discussion-detail', params: { postId: post.postId } }">
          <el-button text type="primary">查看讨论</el-button>
        </RouterLink>
        <el-button text @click="openReport(post)">举报</el-button>
      </div>
    </article>

    <div v-if="!loading && posts.items.length === 0 && !blocked" class="empty-state">
      还没有讨论，发布第一条吧。
    </div>

    <el-pagination
      v-if="posts.total > posts.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="posts.page + 1"
      :page-size="posts.size"
      :total="posts.total"
      @current-change="changePage"
    />

    <ContentReportDialog
      v-model="reportVisible"
      :mode="reportMode"
      :target-id="reportTargetId"
      :question-id="questionId"
      :question-version-id="question?.questionVersionId"
      @submitted="onReportSubmitted"
    />
  </section>
</template>

<style scoped>
.page {
  width: min(60rem, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
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

.question-context {
  padding: 0.9rem 1.1rem;
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-accent);
  border-radius: 0.8rem;
  background: var(--color-surface);
}

.stem {
  margin: 0.5rem 0 0;
  line-height: 1.7;
}

.list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 0.5rem;
}

.list-head h2 {
  margin: 0;
  font-size: 1.1rem;
}

.list-head span {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.post-card {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.15rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background: var(--color-surface);
}

.post-main {
  flex: 1 1 22rem;
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.9rem;
  color: var(--color-muted);
  font-size: 0.8rem;
}

.post-title {
  display: inline-block;
  margin-top: 0.45rem;
  color: var(--color-text);
  font-size: 1rem;
  font-weight: 700;
  text-decoration: none;
}

.post-title:hover {
  color: var(--color-accent);
}

.post-preview {
  margin: 0.45rem 0 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.post-actions {
  display: flex;
  align-items: flex-start;
  gap: 0.25rem;
}

.state,
.empty-state {
  padding: 2rem;
  border: 1px dashed var(--color-border);
  border-radius: 0.8rem;
  color: var(--color-muted);
  text-align: center;
}

.pagination {
  margin-top: 0.5rem;
}
</style>
