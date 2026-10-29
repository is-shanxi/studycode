<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CommentThread from '@/app/components/CommentThread.vue'
import ContentReportDialog from '@/app/components/ContentReportDialog.vue'
import type {
  DiscussionCommentView,
  DiscussionPostDetail,
  ReportCreated,
} from '@/features/api/types'
import { createComment, getDiscussion } from '@/features/community/api'
import { ApiError } from '@/features/http/client'

const route = useRoute()
const router = useRouter()
const postId = String(route.params.postId)

const post = ref<DiscussionPostDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

const replyTarget = ref<DiscussionCommentView | null>(null)
const replyBody = ref('')
const submittingReply = ref(false)

const reportVisible = ref(false)
const reportMode = ref<'post' | 'comment' | 'correction'>('post')
const reportTargetId = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    post.value = await getDiscussion(postId)
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 409) {
      error.value = '本题练习尚未提交，讨论入口暂时隐藏。'
    } else if (caught instanceof ApiError && caught.status === 404) {
      error.value = '讨论不存在或已被隐藏。'
    } else {
      error.value = caught instanceof Error ? caught.message : '讨论加载失败'
    }
    post.value = null
  } finally {
    loading.value = false
  }
}

function startReply(comment: DiscussionCommentView): void {
  replyTarget.value = comment
  replyBody.value = ''
}

function cancelReply(): void {
  replyTarget.value = null
  replyBody.value = ''
}

async function submitReply(): Promise<void> {
  if (!post.value || !replyTarget.value) {
    return
  }
  if (!replyBody.value.trim()) {
    error.value = '回复内容不能为空。'
    return
  }
  submittingReply.value = true
  error.value = null
  try {
    const target = replyTarget.value
    await createComment(post.value.postId, {
      parentCommentId: target.parentCommentId ?? target.commentId,
      quotedCommentId: target.commentId,
      body: replyBody.value.trim(),
    })
    cancelReply()
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '回复失败'
  } finally {
    submittingReply.value = false
  }
}

function openPostReport(): void {
  reportMode.value = 'post'
  reportTargetId.value = postId
  reportVisible.value = true
}

function openCommentReport(comment: DiscussionCommentView): void {
  reportMode.value = 'comment'
  reportTargetId.value = comment.commentId
  reportVisible.value = true
}

function openCorrection(): void {
  reportMode.value = 'correction'
  reportTargetId.value = post.value?.questionId ?? ''
  reportVisible.value = true
}

function onReportSubmitted(report: ReportCreated): void {
  notice.value =
    report.status === 'OPEN'
      ? '已提交，管理员处理后会通过站内通知告知你。'
      : `已存在待处理举报（${report.status}），无需重复提交。`
}

void load()
</script>

<template>
  <section class="page">
    <div class="back-row">
      <el-button text @click="router.back()">返回</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="info" show-icon :closable="true" />
    <div v-if="loading" class="state">正在加载讨论...</div>

    <template v-if="post">
      <header class="post-head">
        <div class="meta">
          <el-tag size="small" :type="post.versionCurrent ? 'success' : 'info'" effect="plain">
            题目版本 v{{ post.versionNo }}{{ post.versionCurrent ? ' · 当前' : ' · 历史' }}
          </el-tag>
          <span>{{ post.authorName }}</span>
          <span>{{ new Date(post.createdAt).toLocaleString() }}</span>
        </div>
        <h1>{{ post.title }}</h1>
        <div class="head-actions">
          <el-button text type="primary" @click="openPostReport">举报帖子</el-button>
          <el-button text @click="openCorrection">报告题目错误</el-button>
        </div>
      </header>

      <article class="post-body">{{ post.body }}</article>

      <section class="comments">
        <header class="comments-head">
          <h2>评论</h2>
          <span>{{ post.comments.length }} 条</span>
        </header>

        <CommentThread :comments="post.comments" @reply="startReply" @report="openCommentReport" />

        <section v-if="replyTarget" class="reply-composer">
          <header>
            <strong>回复 {{ replyTarget.authorName }}</strong>
            <el-button text size="small" @click="cancelReply">取消</el-button>
          </header>
          <blockquote v-if="replyTarget.body">引用：{{ replyTarget.body }}</blockquote>
          <el-input
            v-model="replyBody"
            type="textarea"
            :rows="3"
            maxlength="5000"
            show-word-limit
            placeholder="写下你的回复"
          />
          <div class="reply-actions">
            <el-button type="primary" :loading="submittingReply" @click="submitReply">
              发布回复
            </el-button>
          </div>
        </section>
      </section>

      <ContentReportDialog
        v-model="reportVisible"
        :mode="reportMode"
        :target-id="reportTargetId"
        :question-id="post.questionId"
        :question-version-id="post.questionVersionId"
        @submitted="onReportSubmitted"
      />
    </template>
  </section>
</template>

<style scoped>
.page {
  width: min(56rem, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.back-row {
  display: flex;
}

.post-head {
  padding: 1.1rem 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 0.9rem;
  background: var(--color-surface);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.9rem;
  color: var(--color-muted);
  font-size: 0.8rem;
}

h1 {
  margin: 0.5rem 0 0.25rem;
  font-size: clamp(1.3rem, 3vw, 1.7rem);
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.post-body {
  padding: 1.1rem 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 0.9rem;
  background: var(--color-surface);
  line-height: 1.75;
  white-space: pre-wrap;
}

.comments {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.comments-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.comments-head h2 {
  margin: 0;
  font-size: 1.1rem;
}

.comments-head span {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.reply-composer {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-accent);
  border-radius: 0.85rem;
  background: var(--color-surface);
}

.reply-composer header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

blockquote {
  margin: 0;
  padding: 0.5rem 0.75rem;
  border-radius: 0.5rem;
  background: var(--color-chip);
  color: var(--color-chip-text);
  font-size: 0.85rem;
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
