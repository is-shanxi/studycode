<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import AiQuotaPanel from '@/app/components/AiQuotaPanel.vue'
import type { AiConversationView, AiMessageView, AiQuotaView } from '@/features/api/types'
import { ApiError } from '@/features/http/client'
import {
  clearAiConversation,
  createAiConversation,
  getAiConversation,
  getAiQuota,
  sendAiMessage,
} from '@/features/ai/api'

const route = useRoute()
const router = useRouter()
const sessionItemId = computed(() =>
  typeof route.query.sessionItemId === 'string' ? route.query.sessionItemId : '',
)
const sessionId = computed(() =>
  typeof route.query.sessionId === 'string' ? route.query.sessionId : '',
)
const conversationId = computed(() =>
  typeof route.query.conversationId === 'string' ? route.query.conversationId : '',
)
const conversation = ref<AiConversationView | null>(null)
const quota = ref<AiQuotaView | null>(null)
const messages = ref<AiMessageView[]>([])
const input = ref('')
const loading = ref(false)
const sending = ref(false)
const error = ref<string | null>(null)
const messageList = ref<HTMLElement | null>(null)
const starterPrompts = [
  '帮我梳理这个科目的高频易错点。',
  '给我一个两周的复习安排建议。',
  '解释错题复习时应该如何做归纳。',
]

const scope = computed<'GENERAL' | 'QUESTION'>(() => (sessionItemId.value ? 'QUESTION' : 'GENERAL'))
const scopeLabel = computed(() => (scope.value === 'QUESTION' ? '本题讲解' : '通用辅导'))
const cleared = computed(() => conversation.value?.status === 'CLEARED')

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    if (conversationId.value) {
      conversation.value = await getAiConversation(conversationId.value)
    } else {
      conversation.value = await createAiConversation(scope.value, sessionItemId.value || undefined)
      await router.replace({
        query: {
          ...(sessionItemId.value ? { sessionItemId: sessionItemId.value } : {}),
          ...(sessionId.value ? { sessionId: sessionId.value } : {}),
          conversationId: conversation.value.conversationId,
        },
      })
    }
    messages.value = conversation.value.messages
    quota.value = await getAiQuota(conversation.value.conversationId)
    await scrollToBottom()
  } catch (caught) {
    error.value = describeError(caught)
  } finally {
    loading.value = false
  }
}

async function send(): Promise<void> {
  const content = input.value.trim()
  if (!content || !conversation.value || sending.value) {
    return
  }
  sending.value = true
  error.value = null
  try {
    const response = await sendAiMessage(conversation.value.conversationId, content)
    messages.value.push(response.userMessage, response.assistantMessage)
    quota.value = response.quota
    input.value = ''
    await scrollToBottom()
  } catch (caught) {
    error.value = describeError(caught)
    if (
      caught instanceof ApiError &&
      (caught.status === 429 || caught.status === 503 || caught.status === 504) &&
      conversation.value
    ) {
      quota.value = await getAiQuota(conversation.value.conversationId).catch(() => quota.value)
    }
  } finally {
    sending.value = false
  }
}

async function restart(): Promise<void> {
  if (!conversation.value) {
    return
  }
  try {
    await clearAiConversation(conversation.value.conversationId)
    const fresh = await createAiConversation(scope.value, sessionItemId.value || undefined)
    conversation.value = fresh
    messages.value = []
    error.value = null
    quota.value = await getAiQuota(fresh.conversationId)
    await router.replace({
      query: {
        ...(sessionItemId.value ? { sessionItemId: sessionItemId.value } : {}),
        ...(sessionId.value ? { sessionId: sessionId.value } : {}),
        conversationId: fresh.conversationId,
      },
    })
    ElMessage.success('对话记录已清空，成本记录仍保留在服务端。')
  } catch (caught) {
    error.value = describeError(caught)
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void send()
  }
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
}

function describeError(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.status === 429) {
      return '个人额度或会话轮次已用尽，请等待额度重置或联系管理员调整。'
    }
    if (caught.status === 409) {
      return '该题尚未完成作答和自评，完成后才能使用本题 AI 讲解。'
    }
    if (caught.status === 503 || caught.status === 504) {
      return 'AI 服务暂不可用或响应超时，未消耗的额度会自动退还。'
    }
    if (caught.status === 404) {
      return '会话不存在或无权访问。'
    }
  }
  return caught instanceof Error ? caught.message : '请求失败，请稍后再试。'
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">AI 辅导</p>
        <h1>{{ scopeLabel }}</h1>
        <p class="lead">
          {{
            scope === 'QUESTION'
              ? '完成作答和自评后才能打开本题讲解；上下文只包含本人已完成会话的必要信息。'
              : '通用助手不会自动携带正在作答的题目，也不能保证阻止手动粘贴题目。'
          }}
        </p>
      </div>
      <div class="head-links">
        <RouterLink
          v-if="sessionItemId && sessionId"
          :to="{ name: 'app-result', params: { sessionId } }"
        >
          返回结果
        </RouterLink>
        <RouterLink to="/app">工作台</RouterLink>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />

    <div v-if="loading" class="state">正在准备会话...</div>

    <div v-else class="layout">
      <section class="chat-card">
        <header class="chat-head">
          <div>
            <span class="chat-title">{{ scopeLabel }}</span>
            <span v-if="cleared" class="cleared-tag">记录已清空，开始新对话后继续</span>
          </div>
          <el-button size="small" :disabled="!conversation" @click="restart"> 清空记录 </el-button>
        </header>

        <div ref="messageList" class="message-list">
          <div v-if="messages.length === 0" class="empty">
            <p>还没有消息。可以先问一个复习或解题问题。</p>
            <div v-if="scope === 'GENERAL'" class="starters">
              <button
                v-for="prompt in starterPrompts"
                :key="prompt"
                type="button"
                class="starter"
                @click="input = prompt"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <article
            v-for="message in messages"
            :key="message.messageId"
            :class="['bubble', message.role === 'USER' ? 'user' : 'assistant']"
          >
            <span class="role">{{ message.role === 'USER' ? '我' : 'AI 助手' }}</span>
            <p>{{ message.content }}</p>
            <span v-if="message.role === 'ASSISTANT' && message.modelName" class="meta">
              {{ message.provider }} · {{ message.modelName }} · 输入
              {{ message.inputTokens ?? 0 }} / 输出 {{ message.outputTokens ?? 0 }} tokens
            </span>
          </article>
        </div>

        <div class="composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            :maxlength="quota?.maxInputChars ?? 2000"
            show-word-limit
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            @keydown="onKeydown"
          />
          <div class="composer-actions">
            <span class="hint"> 额度与轮次由服务端控制；失败请求会尽量退还个人次数。 </span>
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!input.trim() || !conversation || cleared"
              @click="send"
            >
              发送
            </el-button>
          </div>
        </div>
      </section>

      <aside class="side">
        <AiQuotaPanel :quota="quota" />
        <div class="side-card">
          <h2>安全边界</h2>
          <ul>
            <li>题目会话只能引用本人已完成且已自评的会话项。</li>
            <li>官方答案和解析默认不发送给模型，管理员可显式开启。</li>
            <li>清空会话只移除可见消息，团队成本记录继续保留。</li>
          </ul>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.page {
  width: min(68rem, 100%);
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
  max-width: 44rem;
  margin: 0.5rem 0 0;
  color: var(--color-muted);
  line-height: 1.7;
}

.head-links {
  display: flex;
  gap: 1rem;
}

.head-links a {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(17rem, 1fr);
  gap: 1rem;
  align-items: start;
}

.chat-card {
  display: flex;
  flex-direction: column;
  min-height: 34rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1.1rem;
  border-bottom: 1px solid var(--color-border);
}

.chat-title {
  font-weight: 700;
}

.cleared-tag {
  margin-left: 0.75rem;
  color: var(--color-muted);
  font-size: 0.8rem;
}

.message-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  max-height: 30rem;
  padding: 1.1rem;
  overflow-y: auto;
}

.empty {
  margin: auto;
  color: var(--color-muted);
  text-align: center;
}

.starters {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin-top: 0.9rem;
}

.starter {
  border: 1px solid var(--color-border);
  border-radius: 0.6rem;
  padding: 0.55rem 0.75rem;
  background: var(--color-chip);
  color: var(--color-text);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.starter:hover {
  border-color: var(--color-accent);
}

.bubble {
  max-width: 88%;
  padding: 0.75rem 0.9rem;
  border-radius: 0.85rem;
  background: var(--color-chip);
}

.bubble.user {
  align-self: flex-end;
  background: color-mix(in srgb, var(--color-accent) 14%, var(--color-surface));
}

.bubble.assistant {
  align-self: flex-start;
}

.role {
  color: var(--color-muted);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.bubble p {
  margin: 0.35rem 0 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.meta {
  display: block;
  margin-top: 0.5rem;
  color: var(--color-muted);
  font-size: 0.72rem;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.9rem 1.1rem 1.1rem;
  border-top: 1px solid var(--color-border);
}

.composer-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.hint {
  color: var(--color-muted);
  font-size: 0.78rem;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.side-card {
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.9rem;
  background: var(--color-surface);
}

.side-card h2 {
  margin: 0 0 0.6rem;
  font-size: 1rem;
}

.side-card ul {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--color-muted);
  line-height: 1.75;
  font-size: 0.85rem;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
