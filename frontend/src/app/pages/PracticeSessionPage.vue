<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { SessionItemView, SessionView } from '@/features/api/types'
import { getSession, saveAnswer, submitSession } from '@/features/practice/api'
import { answeredCount, canSubmit, compositionLabel } from '@/features/practice/session'

const route = useRoute()
const router = useRouter()
const sessionId = computed(() => String(route.params.sessionId))
const session = ref<SessionView | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const submitting = ref(false)
const notice = ref<string | null>(null)
const selectedOptions = reactive<Record<string, string>>({})
const textAnswers = reactive<Record<string, string>>({})
const saving = reactive<Record<string, boolean>>({})

const answered = computed(() => (session.value ? answeredCount(session.value) : 0))
const readyToSubmit = computed(() => (session.value ? canSubmit(session.value) : false))

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const loaded = await getSession(sessionId.value)
    session.value = loaded
    for (const item of loaded.items) {
      if (item.answer?.selectedOptionId) {
        selectedOptions[item.itemId] = item.answer.selectedOptionId
      }
      if (item.answer?.text) {
        textAnswers[item.itemId] = item.answer.text
      }
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '会话加载失败'
  } finally {
    loading.value = false
  }
}

async function persist(item: SessionItemView): Promise<void> {
  if (!session.value) {
    return
  }
  saving[item.itemId] = true
  notice.value = null
  try {
    const isChoice = item.question.type === 'SINGLE_CHOICE'
    const answer = isChoice
      ? { selectedOptionId: selectedOptions[item.itemId] }
      : { text: textAnswers[item.itemId] }
    if (isChoice && !answer.selectedOptionId) {
      return
    }
    if (!isChoice && !answer.text?.trim()) {
      return
    }
    const response = await saveAnswer(session.value.sessionId, item.itemId, {
      answerRevision: item.answer?.answerRevision ?? 0,
      answer,
    })
    item.answer = { ...answer, answerRevision: response.answerRevision }
    notice.value = `第 ${item.ordinal} 题答案已保存`
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '保存答案失败'
  } finally {
    saving[item.itemId] = false
  }
}

async function submit(): Promise<void> {
  if (!session.value || !readyToSubmit.value) {
    error.value = '请先完成所有题目再提交'
    return
  }
  submitting.value = true
  error.value = null
  try {
    await submitSession(session.value.sessionId, crypto.randomUUID())
    await router.push({ name: 'app-result', params: { sessionId: session.value.sessionId } })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '提交失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page">
    <div v-if="loading" class="state">正在加载练习...</div>
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />

    <template v-if="session">
      <header class="page-head">
        <div>
          <p class="eyebrow">练习会话</p>
          <h1>{{ session.mode === 'NORMAL' ? '普通练习' : '个性化自测' }}</h1>
          <p class="lead">
            共 {{ session.actualCount }} 题（请求 {{ session.requestedCount }} 题）·
            {{ compositionLabel(session) }}
          </p>
        </div>
        <div class="progress">
          <span class="progress-number">{{ answered }} / {{ session.items.length }}</span>
          <span class="progress-label">已作答</span>
        </div>
      </header>

      <el-alert
        v-if="session.status !== 'IN_PROGRESS'"
        type="info"
        :closable="false"
        show-icon
        title="该会话已提交，答案不可修改。"
      >
        <RouterLink
          class="result-link"
          :to="{ name: 'app-result', params: { sessionId: session.sessionId } }"
        >
          查看结果
        </RouterLink>
      </el-alert>

      <p v-if="notice" class="notice">{{ notice }}</p>

      <article v-for="item in session.items" :key="item.itemId" class="item-card">
        <header class="item-head">
          <span class="ordinal">第 {{ item.ordinal }} 题</span>
          <el-tag size="small" type="info" effect="plain">
            {{ item.question.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
          </el-tag>
          <el-tag size="small" type="warning" effect="plain">
            {{ item.question.difficulty }}
          </el-tag>
          <el-tag v-if="item.answer" size="small" type="success" effect="plain">已作答</el-tag>
        </header>

        <p class="stem">{{ item.question.stem }}</p>

        <template v-if="item.question.type === 'SINGLE_CHOICE'">
          <el-radio-group
            v-model="selectedOptions[item.itemId]"
            class="option-group"
            :disabled="session.status !== 'IN_PROGRESS'"
            @change="persist(item)"
          >
            <el-radio
              v-for="option in item.question.options"
              :key="option.id"
              :value="option.id"
              class="option-radio"
            >
              <span class="option-key">{{ option.key }}</span>
              {{ option.content }}
            </el-radio>
          </el-radio-group>
        </template>

        <template v-else>
          <div class="fill-row">
            <el-input
              v-model="textAnswers[item.itemId]"
              placeholder="输入你的答案"
              :disabled="session.status !== 'IN_PROGRESS'"
              @keyup.enter="persist(item)"
            />
            <el-button
              v-if="session.status === 'IN_PROGRESS'"
              :loading="saving[item.itemId]"
              @click="persist(item)"
            >
              保存
            </el-button>
            <RouterLink
              class="fill-link"
              :to="{
                name: 'app-fill',
                params: { sessionId: session.sessionId, itemId: item.itemId },
              }"
            >
              填空详情
            </RouterLink>
          </div>
        </template>
      </article>

      <div v-if="session.status === 'IN_PROGRESS'" class="submit-bar">
        <span class="submit-hint">
          {{
            readyToSubmit
              ? '所有题目已作答，可以提交。'
              : '所有题目作答后统一提交，提交后不可修改。'
          }}
        </span>
        <el-button type="primary" size="large" :loading="submitting" @click="submit">
          提交练习
        </el-button>
      </div>
    </template>
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
  font-size: 0.9rem;
}

.progress {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding: 0.75rem 1rem;
  border-radius: 0.75rem;
  background: var(--color-chip);
}

.progress-number {
  font-size: 1.4rem;
  font-weight: 700;
}

.progress-label {
  color: var(--color-chip-text);
  font-size: 0.8rem;
}

.notice {
  margin: 0.75rem 0;
  color: #15803d;
  font-size: 0.9rem;
}

.item-card {
  margin-bottom: 1rem;
  padding: 1.1rem 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 0.8rem;
  background: var(--color-surface);
}

.item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.ordinal {
  font-weight: 700;
}

.stem {
  margin: 0.8rem 0;
  line-height: 1.75;
  white-space: pre-wrap;
}

.option-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0.35rem;
}

.option-radio {
  height: auto;
  padding: 0.6rem 0.75rem;
  margin-right: 0;
  border: 1px solid var(--color-border);
  border-radius: 0.6rem;
  white-space: normal;
}

.option-key {
  display: inline-block;
  min-width: 1.5rem;
  font-weight: 700;
}

.fill-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.fill-row :deep(.el-input) {
  flex: 1 1 16rem;
}

.fill-link,
.result-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.submit-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1.5rem;
  padding: 1rem 1.25rem;
  border-top: 1px solid var(--color-border);
  border-radius: 0.8rem;
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(8px);
}

.submit-hint {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
