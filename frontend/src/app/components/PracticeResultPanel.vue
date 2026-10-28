<script setup lang="ts">
import type { ResultItemView, ResultView } from '@/features/api/types'

defineProps<{ result: ResultView }>()

function optionClass(item: ResultItemView, optionId: string): string {
  if (item.correctOptionId === optionId) {
    return 'option correct'
  }
  if (item.selectedOptionId === optionId) {
    return 'option selected'
  }
  return 'option'
}

function resultLabel(value?: string): string {
  if (value === 'PASS') {
    return '成功'
  }
  if (value === 'FAIL') {
    return '失败'
  }
  return '待确认'
}
</script>

<template>
  <div class="result-panel">
    <div class="summary-grid">
      <div class="summary-card">
        <span class="summary-label">状态</span>
        <strong>{{ result.status === 'COMPLETED' ? '已完成' : '待完成自评' }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">单选</span>
        <strong>{{ result.objective.correct }} / {{ result.objective.answered }} 正确</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">填空自动匹配</span>
        <strong>{{ result.fill.autoMatched }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">自评通过 / 失败</span>
        <strong>{{ result.fill.selfPassed }} / {{ result.fill.selfFailed }}</strong>
      </div>
    </div>

    <p class="composition-note">
      实际 {{ result.actualCount }} 题（请求
      {{ result.requestedCount }}
      题），客观单选结果、自动匹配建议和自评结果分别展示，不合并为统一正确率。
    </p>

    <article v-for="(item, index) in result.items" :key="item.itemId" class="result-item">
      <header class="item-head">
        <span class="ordinal">第 {{ index + 1 }} 题</span>
        <el-tag size="small" type="info" effect="plain">
          {{ item.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
        </el-tag>
        <el-tag
          v-if="item.finalResult"
          size="small"
          :type="item.finalResult === 'PASS' ? 'success' : 'danger'"
          effect="plain"
        >
          {{ resultLabel(item.finalResult) }}
        </el-tag>
      </header>

      <p class="stem">{{ item.stem }}</p>

      <template v-if="item.type === 'SINGLE_CHOICE'">
        <ul class="option-list">
          <li v-for="option in item.options" :key="option.id" :class="optionClass(item, option.id)">
            <span class="option-key">{{ option.key }}</span>
            {{ option.content }}
            <span v-if="item.correctOptionId === option.id" class="option-tag">正确答案</span>
            <span
              v-if="item.selectedOptionId === option.id && item.correctOptionId !== option.id"
              class="option-tag wrong"
            >
              你的选择
            </span>
          </li>
        </ul>
      </template>

      <template v-else>
        <div class="fill-summary">
          <p><span>你的答案：</span>{{ item.text ?? '—' }}</p>
          <p><span>参考答案：</span>{{ item.referenceAnswer ?? '—' }}</p>
          <p>
            <span>自动匹配：</span>
            {{ item.autoMatchResult === 'MATCH' ? '匹配' : '不匹配' }}
          </p>
          <p><span>自评：</span>{{ resultLabel(item.selfAssessmentResult) }}</p>
          <p><span>最终结果：</span>{{ resultLabel(item.finalResult) }}</p>
        </div>
      </template>

      <div v-if="item.explanation" class="explanation">
        <h3>官方解析</h3>
        <p>{{ item.explanation }}</p>
      </div>
    </article>
  </div>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.9rem 1rem;
  border-radius: 0.75rem;
  background: var(--color-chip);
}

.summary-label {
  color: var(--color-chip-text);
  font-size: 0.8rem;
}

.composition-note {
  margin: 0 0 1.25rem;
  color: var(--color-muted);
  font-size: 0.9rem;
  line-height: 1.7;
}

.result-item {
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
  margin: 0.75rem 0;
  line-height: 1.75;
  white-space: pre-wrap;
}

.option-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.option {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.75rem;
  margin-bottom: 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: 0.55rem;
}

.option.correct {
  border-color: #16a34a;
  background: #f0fdf4;
}

.option.selected {
  border-color: #dc2626;
  background: #fef2f2;
}

.option-key {
  font-weight: 700;
}

.option-tag {
  margin-left: auto;
  color: #15803d;
  font-size: 0.8rem;
}

.option-tag.wrong {
  color: #b91c1c;
}

.fill-summary {
  padding: 0.75rem 1rem;
  border-radius: 0.6rem;
  background: #f8fafc;
}

.fill-summary p {
  margin: 0.3rem 0;
}

.fill-summary span {
  color: var(--color-muted);
}

.explanation {
  margin-top: 0.9rem;
  padding: 0.75rem 1rem;
  border-left: 3px solid var(--color-accent);
  background: #f8fafc;
}

.explanation h3 {
  margin: 0 0 0.35rem;
  font-size: 0.9rem;
}

.explanation p {
  margin: 0;
  color: var(--color-muted);
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
