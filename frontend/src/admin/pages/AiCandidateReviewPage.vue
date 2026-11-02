<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import type { AiGenerationBatchView, AiGenerationCandidateView } from '@/features/api/types'
import { getGenerationBatch, reviewGenerationCandidate } from '@/features/ai/api'

const route = useRoute()
const batchId = computed(() => String(route.params.batchId))
const batch = ref<AiGenerationBatchView | null>(null)
const loading = ref(false)
const reviewingId = ref<string | null>(null)
const error = ref<string | null>(null)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    batch.value = await getGenerationBatch(batchId.value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '候选加载失败'
  } finally {
    loading.value = false
  }
}

async function review(
  candidate: AiGenerationCandidateView,
  decision: 'APPROVE' | 'REJECT',
): Promise<void> {
  reviewingId.value = candidate.candidateId
  error.value = null
  try {
    await reviewGenerationCandidate(candidate.candidateId, decision)
    ElMessage.success(decision === 'APPROVE' ? '候选已审核并发布为正式版本' : '候选已拒绝')
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '审核失败'
  } finally {
    reviewingId.value = null
  }
}

function reviewType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'APPROVED') {
    return 'success'
  }
  if (status === 'REJECTED') {
    return 'danger'
  }
  return 'warning'
}

function canReview(candidate: AiGenerationCandidateView): boolean {
  return candidate.reviewStatus === 'PENDING' && candidate.validationStatus === 'VALID'
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">AI 候选审核</p>
        <h1>审核变式题候选</h1>
        <p class="lead">
          只有结构校验通过且经人工批准的候选才会转换为已发布题目版本；候选表本身不是题库事实来源。
        </p>
      </div>
      <div class="head-links">
        <RouterLink to="/admin/ai">返回 AI 任务</RouterLink>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading && !batch" class="state">正在加载候选...</div>

    <template v-if="batch">
      <div class="summary-grid">
        <div class="summary-card">
          <span class="label">任务状态</span>
          <strong>{{ batch.status }}</strong>
        </div>
        <div class="summary-card">
          <span class="label">目标 / 候选</span>
          <strong>{{ batch.targetCount }} / {{ batch.candidates.length }}</strong>
        </div>
        <div class="summary-card">
          <span class="label">模型 / 提示词</span>
          <strong>{{ batch.modelName }} · {{ batch.promptVersion }}</strong>
        </div>
        <div class="summary-card">
          <span class="label">尝试次数</span>
          <strong>{{ batch.attemptCount }}</strong>
        </div>
      </div>

      <el-alert
        v-if="batch.lastError"
        type="warning"
        show-icon
        :closable="false"
        :title="`调度错误：${batch.lastError}`"
      />

      <div v-if="batch.candidates.length === 0" class="state">
        暂无候选。调度器处理后刷新本页查看结果。
      </div>

      <article
        v-for="candidate in batch.candidates"
        :key="candidate.candidateId"
        class="candidate-card"
      >
        <header class="candidate-head">
          <div class="tags">
            <el-tag size="small" effect="plain">
              {{ candidate.candidate.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
            </el-tag>
            <el-tag size="small" type="info" effect="plain">
              {{ candidate.candidate.difficulty }}
            </el-tag>
            <el-tag
              size="small"
              :type="candidate.validationStatus === 'VALID' ? 'success' : 'danger'"
              effect="plain"
            >
              校验 {{ candidate.validationStatus }}
            </el-tag>
            <el-tag size="small" :type="reviewType(candidate.reviewStatus)" effect="plain">
              审核 {{ candidate.reviewStatus }}
            </el-tag>
          </div>
          <div class="actions">
            <template v-if="canReview(candidate)">
              <el-button
                size="small"
                type="primary"
                :loading="reviewingId === candidate.candidateId"
                @click="review(candidate, 'APPROVE')"
              >
                批准并发布
              </el-button>
              <el-button
                size="small"
                :loading="reviewingId === candidate.candidateId"
                @click="review(candidate, 'REJECT')"
              >
                拒绝
              </el-button>
            </template>
            <span v-else-if="candidate.convertedQuestionVersionId" class="converted">
              已转换为版本 {{ candidate.convertedQuestionVersionId }}
            </span>
            <span v-else-if="candidate.validationStatus === 'INVALID'" class="invalid">
              结构校验未通过，不能审核
            </span>
          </div>
        </header>

        <p class="stem">{{ candidate.candidate.stem }}</p>

        <ul v-if="candidate.candidate.options?.length" class="options">
          <li
            v-for="option in candidate.candidate.options"
            :key="option.key"
            :class="{ correct: option.isCorrect }"
          >
            <span class="key">{{ option.key }}</span>
            {{ option.content }}
            <span v-if="option.isCorrect" class="correct-tag">正确答案</span>
          </li>
        </ul>
        <p v-else class="answer">
          <span>参考答案：</span>{{ candidate.candidate.referenceAnswer ?? '—' }}
        </p>

        <p class="explanation">{{ candidate.candidate.explanation }}</p>

        <footer class="meta">
          <span>来源版本 {{ candidate.sourceQuestionVersionId }}</span>
          <span>建议标签 {{ candidate.candidate.suggestedTags.join('、') || '无' }}</span>
          <span>内容哈希 {{ candidate.contentHash.slice(0, 16) }}…</span>
        </footer>
      </article>
    </template>
  </section>
</template>

<style scoped>
.page {
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
  max-width: 50rem;
  margin: 0.5rem 0 0;
  color: var(--color-muted);
  line-height: 1.7;
}

.head-links {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.head-links a {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 0.75rem;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background: var(--color-surface);
}

.label {
  color: var(--color-muted);
  font-size: 0.78rem;
}

.candidate-card {
  padding: 1.1rem 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
}

.candidate-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.converted,
.invalid {
  font-size: 0.8rem;
}

.converted {
  color: #15803d;
}

.invalid {
  color: #b91c1c;
}

.stem {
  margin: 0.9rem 0 0.6rem;
  font-size: 1rem;
  font-weight: 600;
  line-height: 1.7;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0 0 0.75rem;
  padding: 0;
  list-style: none;
}

.options li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 0.6rem;
  color: var(--color-muted);
}

.options li.correct {
  border-color: #16a34a;
  color: var(--color-text);
}

.key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 999px;
  background: var(--color-chip);
  font-size: 0.78rem;
  font-weight: 700;
}

.correct-tag {
  margin-left: auto;
  color: #15803d;
  font-size: 0.75rem;
}

.answer {
  color: var(--color-muted);
}

.explanation {
  margin: 0.75rem 0;
  padding: 0.7rem 0.85rem;
  border-radius: 0.7rem;
  background: var(--color-chip);
  color: var(--color-chip-text);
  line-height: 1.7;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.25rem;
  color: var(--color-muted);
  font-size: 0.75rem;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
