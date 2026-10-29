<script setup lang="ts">
import { computed } from 'vue'

import type { DiscussionCommentView } from '@/features/api/types'

const props = defineProps<{
  comments: DiscussionCommentView[]
}>()

const emit = defineEmits<{
  reply: [comment: DiscussionCommentView]
  report: [comment: DiscussionCommentView]
}>()

interface Thread {
  root: DiscussionCommentView
  replies: DiscussionCommentView[]
}

const threads = computed<Thread[]>(() => {
  const roots = props.comments.filter((comment) => !comment.parentCommentId)
  return roots.map((root) => ({
    root,
    replies: props.comments.filter((comment) => comment.parentCommentId === root.commentId),
  }))
})

function formatTime(value: string): string {
  return new Date(value).toLocaleString()
}
</script>

<template>
  <section class="threads">
    <article v-for="thread in threads" :key="thread.root.commentId" class="thread">
      <div class="comment root">
        <div class="comment-head">
          <strong>{{ thread.root.authorName }}</strong>
          <span>{{ formatTime(thread.root.createdAt) }}</span>
        </div>
        <p>{{ thread.root.body }}</p>
        <div class="comment-actions">
          <el-button text size="small" type="primary" @click="emit('reply', thread.root)">
            回复
          </el-button>
          <el-button text size="small" @click="emit('report', thread.root)">举报</el-button>
        </div>
      </div>

      <div v-for="reply in thread.replies" :key="reply.commentId" class="comment reply">
        <div class="comment-head">
          <strong>{{ reply.authorName }}</strong>
          <span>{{ formatTime(reply.createdAt) }}</span>
        </div>
        <blockquote v-if="reply.quotedPreview">引用：{{ reply.quotedPreview }}</blockquote>
        <p>{{ reply.body }}</p>
        <div class="comment-actions">
          <el-button text size="small" type="primary" @click="emit('reply', reply)">
            回复
          </el-button>
          <el-button text size="small" @click="emit('report', reply)">举报</el-button>
        </div>
      </div>

      <p v-if="thread.replies.length === 0" class="no-replies">还没有回复。</p>
    </article>

    <p v-if="threads.length === 0" class="no-comments">还没有评论，来补充第一条思路。</p>
  </section>
</template>

<style scoped>
.threads {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.thread {
  padding: 0.9rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background: var(--color-surface);
}

.comment + .comment {
  margin-top: 0.75rem;
}

.comment.reply {
  padding-left: 1rem;
  border-left: 2px solid var(--color-border);
}

.comment-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.5rem;
  color: var(--color-muted);
  font-size: 0.8rem;
}

.comment p {
  margin: 0.45rem 0;
  line-height: 1.65;
  white-space: pre-wrap;
}

blockquote {
  margin: 0.5rem 0;
  padding: 0.45rem 0.7rem;
  border-radius: 0.5rem;
  background: var(--color-chip);
  color: var(--color-chip-text);
  font-size: 0.85rem;
}

.comment-actions {
  display: flex;
  gap: 0.25rem;
}

.no-replies,
.no-comments {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.no-comments {
  padding: 1.25rem;
  border: 1px dashed var(--color-border);
  border-radius: 0.8rem;
  text-align: center;
}
</style>
