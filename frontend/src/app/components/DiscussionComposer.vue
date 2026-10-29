<script setup lang="ts">
import { ref } from 'vue'

import type { DiscussionPostSummary } from '@/features/api/types'
import { createDiscussion } from '@/features/community/api'

const props = defineProps<{
  questionId: string
  questionVersionId: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  created: [post: DiscussionPostSummary]
}>()

const title = ref('')
const body = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)

async function submit(): Promise<void> {
  if (!title.value.trim() || !body.value.trim()) {
    error.value = '标题和内容都不能为空。'
    return
  }
  submitting.value = true
  error.value = null
  try {
    const post = await createDiscussion(props.questionId, {
      questionVersionId: props.questionVersionId,
      title: title.value.trim(),
      body: body.value.trim(),
    })
    title.value = ''
    body.value = ''
    emit('created', post)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '发布讨论失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="composer">
    <header>
      <h2>发起讨论</h2>
      <p>讨论绑定当前发布版本；外部链接和敏感词会在发布前过滤。</p>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-input
      v-model="title"
      maxlength="200"
      show-word-limit
      placeholder="用一个清晰的标题描述你的问题"
      :disabled="disabled"
    />
    <el-input
      v-model="body"
      type="textarea"
      :rows="4"
      maxlength="10000"
      show-word-limit
      placeholder="补充你的思路、疑问或题解"
      :disabled="disabled"
    />
    <div class="actions">
      <el-button type="primary" :loading="submitting" :disabled="disabled" @click="submit">
        发布讨论
      </el-button>
    </div>
  </section>
</template>

<style scoped>
.composer {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.1rem 1.2rem;
  border: 1px solid var(--color-border);
  border-radius: 0.9rem;
  background: var(--color-surface);
}

header h2 {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
}

header p {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
