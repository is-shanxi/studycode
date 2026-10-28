<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import type {
  AssessmentResult,
  ResultItemView,
  ResultView,
  SessionItemView,
  SessionView,
} from '@/features/api/types'
import { getResult, getSession, saveAnswer, selfAssess } from '@/features/practice/api'

const route = useRoute()
const sessionId = computed(() => String(route.params.sessionId))
const itemId = computed(() => String(route.params.itemId))
const session = ref<SessionView | null>(null)
const result = ref<ResultView | null>(null)
const textAnswer = ref('')
const loading = ref(false)
const saving = ref(false)
const assessing = ref(false)
const error = ref<string | null>(null)
const assessmentResult = ref<AssessmentResult | null>(null)
const finalResult = ref<AssessmentResult | null>(null)
const sessionStatus = ref<string>('')

const item = computed<SessionItemView | undefined>(() =>
  session.value?.items.find((candidate) => candidate.itemId === itemId.value),
)
const resultItem = computed<ResultItemView | undefined>(() =>
  result.value?.items.find((candidate) => candidate.itemId === itemId.value),
)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const loaded = await getSession(sessionId.value)
    session.value = loaded
    sessionStatus.value = loaded.status
    const current = loaded.items.find((candidate) => candidate.itemId === itemId.value)
    if (!current) {
      error.value = '未找到该会话题目'
      return
    }
    if (current.answer?.text) {
      textAnswer.value = current.answer.text
    }
    assessmentResult.value = current.selfAssessmentResult ?? null
    finalResult.value = current.finalResult ?? null
    if (loaded.status !== 'IN_PROGRESS') {
      result.value = await getResult(sessionId.value)
      const stored = result.value.items.find((candidate) => candidate.itemId === itemId.value)
      if (stored) {
        assessmentResult.value = stored.selfAssessmentResult ?? null
        finalResult.value = stored.finalResult ?? null
      }
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '题目加载失败'
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  const current = item.value
  if (!current || !textAnswer.value.trim()) {
    error.value = '请输入答案'
    return
  }
  saving.value = true
  error.value = null
  try {
    const response = await saveAnswer(sessionId.value, itemId.value, {
      answerRevision: current.answer?.answerRevision ?? 0,
      answer: { text: textAnswer.value },
    })
    current.answer = { text: textAnswer.value, answerRevision: response.answerRevision }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '保存答案失败'
  } finally {
    saving.value = false
  }
}

async function assess(resultValue: AssessmentResult): Promise<void> {
  assessing.value = true
  error.value = null
  try {
    const response = await selfAssess(sessionId.value, itemId.value, resultValue)
    assessmentResult.value = response.finalResult as AssessmentResult
    finalResult.value = response.finalResult as AssessmentResult
    sessionStatus.value = response.sessionStatus
    result.value = await getResult(sessionId.value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '自评失败'
  } finally {
    assessing.value = false
  }
}
</script>

<template>
  <section class="page">
    <div v-if="loading" class="state">正在加载题目...</div>
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />

    <template v-if="item">
      <header class="page-head">
        <div>
          <p class="eyebrow">填空题</p>
          <h1>第 {{ item.ordinal }} 题</h1>
          <p class="lead">自动匹配只是建议，最终结果由你逐题自评确认。</p>
        </div>
        <RouterLink class="back-link" :to="{ name: 'app-practice', params: { sessionId } }">
          返回练习
        </RouterLink>
      </header>

      <div class="split-layout">
        <aside class="left-pane">
          <h2>题干</h2>
          <p class="stem">{{ item.question.stem }}</p>
          <div class="meta">
            <span v-if="item.question.chapter">{{ item.question.chapter.name }}</span>
            <el-tag
              v-for="tag in item.question.tags"
              :key="tag.id"
              size="small"
              type="info"
              effect="plain"
            >
              {{ tag.name }}
            </el-tag>
          </div>

          <template v-if="resultItem">
            <h2>参考答案</h2>
            <p class="reference">{{ resultItem.referenceAnswer ?? '—' }}</p>
            <h2>官方解析</h2>
            <p class="explanation">{{ resultItem.explanation ?? '暂无解析' }}</p>
          </template>
          <p v-else class="locked">提交后可查看参考答案与官方解析，提交前不会返回答案字段。</p>
        </aside>

        <div class="right-pane">
          <h2>你的作答</h2>
          <el-input
            v-model="textAnswer"
            type="textarea"
            :rows="3"
            placeholder="输入答案后保存"
            :disabled="sessionStatus !== 'IN_PROGRESS'"
          />
          <el-button
            v-if="sessionStatus === 'IN_PROGRESS'"
            type="primary"
            :loading="saving"
            class="save-button"
            @click="save"
          >
            保存答案
          </el-button>

          <template v-if="resultItem">
            <div class="assessment-card">
              <p class="assessment-line">
                自动匹配：
                <strong>{{ resultItem.autoMatchResult === 'MATCH' ? '匹配' : '不匹配' }}</strong>
              </p>
              <p class="assessment-line">
                自评结果：
                <strong>{{ assessmentResult ?? '待确认' }}</strong>
              </p>
              <p class="assessment-line">
                最终结果：
                <strong>{{ finalResult ?? '待自评' }}</strong>
              </p>

              <div
                v-if="sessionStatus === 'PENDING_SELF_ASSESSMENT' && !assessmentResult"
                class="assessment-actions"
              >
                <el-button type="success" :loading="assessing" @click="assess('PASS')">
                  自评成功
                </el-button>
                <el-button type="danger" :loading="assessing" @click="assess('FAIL')">
                  自评失败
                </el-button>
              </div>

              <RouterLink
                v-if="sessionStatus === 'COMPLETED'"
                class="result-link"
                :to="{ name: 'app-result', params: { sessionId } }"
              >
                查看完整结果
              </RouterLink>
            </div>
          </template>
        </div>
      </div>
    </template>
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

h2 {
  margin: 1.4rem 0 0.5rem;
  font-size: 1rem;
}

.lead {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
}

.back-link,
.result-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.split-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 1rem;
}

.left-pane,
.right-pane {
  padding: 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 0.8rem;
  background: var(--color-surface);
}

.stem {
  line-height: 1.8;
  white-space: pre-wrap;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.reference {
  padding: 0.6rem 0.8rem;
  border-radius: 0.5rem;
  background: var(--color-chip);
  font-size: 1.05rem;
  font-weight: 700;
}

.explanation {
  color: var(--color-muted);
  line-height: 1.75;
  white-space: pre-wrap;
}

.locked {
  margin-top: 1.5rem;
  padding: 0.75rem;
  border-radius: 0.5rem;
  background: #fff7ed;
  color: #9a3412;
  font-size: 0.9rem;
}

.save-button {
  margin-top: 0.75rem;
}

.assessment-card {
  margin-top: 1.25rem;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.7rem;
  background: #f8fafc;
}

.assessment-line {
  margin: 0.35rem 0;
  font-size: 0.9rem;
}

.assessment-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
