<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import type {
  CatalogTree,
  Difficulty,
  PageResponse,
  QuestionSummary,
  QuestionType,
  Tag,
} from '@/features/api/types'
import {
  addFavorite,
  createSession,
  getCatalogTree,
  getTags,
  removeFavorite,
  searchQuestions,
} from '@/features/practice/api'

const router = useRouter()
const catalog = ref<CatalogTree>({ certificates: [] })
const tags = ref<Tag[]>([])
const results = ref<PageResponse<QuestionSummary>>({ items: [], page: 0, size: 10, total: 0 })
const loading = ref(false)
const error = ref<string | null>(null)
const starting = ref(false)
const favorites = ref<Set<string>>(new Set())

const filters = reactive({
  certificateId: '',
  subjectId: '',
  chapterId: '',
  tagIds: [] as string[],
  keyword: '',
  difficulty: [] as Difficulty[],
  type: '' as QuestionType | '',
})

const subjectOptions = computed(() => {
  const certificate = catalog.value.certificates.find(
    (candidate) => candidate.id === filters.certificateId,
  )
  return certificate?.subjects ?? []
})

const chapterOptions = computed(() => {
  const subject = subjectOptions.value.find((candidate) => candidate.id === filters.subjectId)
  return subject?.chapters ?? []
})

watch(
  () => filters.certificateId,
  () => {
    filters.subjectId = ''
    filters.chapterId = ''
  },
)

watch(
  () => filters.subjectId,
  () => {
    filters.chapterId = ''
  },
)

async function loadCatalog(): Promise<void> {
  try {
    const [tree, tagList] = await Promise.all([getCatalogTree(), getTags()])
    catalog.value = tree
    tags.value = tagList
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '目录加载失败'
  }
}

async function search(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  try {
    results.value = await searchQuestions({
      certificateId: filters.certificateId || undefined,
      subjectId: filters.subjectId || undefined,
      chapterIds: filters.chapterId ? [filters.chapterId] : undefined,
      tagIds: filters.tagIds.length > 0 ? filters.tagIds : undefined,
      keyword: filters.keyword.trim() || undefined,
      difficulty: filters.difficulty.length > 0 ? filters.difficulty : undefined,
      type: filters.type || undefined,
      page,
      size: results.value.size,
    })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '搜索失败'
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  void search(page - 1)
}

async function startPractice(): Promise<void> {
  starting.value = true
  error.value = null
  try {
    const session = await createSession(
      {
        mode: 'NORMAL',
        filters: {
          certificateId: filters.certificateId || undefined,
          subjectId: filters.subjectId || undefined,
          chapterIds: filters.chapterId ? [filters.chapterId] : undefined,
          tagIds: filters.tagIds.length > 0 ? filters.tagIds : undefined,
          difficulty: filters.difficulty.length > 0 ? filters.difficulty : undefined,
          historyDays: 90,
        },
        requestedCount: 10,
      },
      crypto.randomUUID(),
    )
    await router.push({ name: 'app-practice', params: { sessionId: session.sessionId } })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '创建练习失败'
  } finally {
    starting.value = false
  }
}

async function toggleFavorite(questionId: string): Promise<void> {
  const active = favorites.value.has(questionId)
  try {
    if (active) {
      await removeFavorite(questionId)
    } else {
      await addFavorite(questionId)
    }
    const next = new Set(favorites.value)
    if (active) {
      next.delete(questionId)
    } else {
      next.add(questionId)
    }
    favorites.value = next
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '收藏操作失败'
  }
}

onMounted(async () => {
  await loadCatalog()
  await search()
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">题库</p>
        <h1>筛选与练习</h1>
        <p class="lead">关键词仅匹配题干、章节和标签；多标签筛选取交集。</p>
      </div>
      <el-button type="primary" size="large" :loading="starting" @click="startPractice">
        用当前筛选开始 10 题练习
      </el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div class="filter-panel">
      <el-select v-model="filters.certificateId" placeholder="证书" clearable>
        <el-option
          v-for="certificate in catalog.certificates"
          :key="certificate.id"
          :label="certificate.name"
          :value="certificate.id"
        />
      </el-select>
      <el-select v-model="filters.subjectId" placeholder="科目" clearable>
        <el-option
          v-for="subject in subjectOptions"
          :key="subject.id"
          :label="subject.name"
          :value="subject.id"
        />
      </el-select>
      <el-select v-model="filters.chapterId" placeholder="章节" clearable>
        <el-option
          v-for="chapter in chapterOptions"
          :key="chapter.id"
          :label="chapter.name"
          :value="chapter.id"
        />
      </el-select>
      <el-select v-model="filters.tagIds" multiple collapse-tags placeholder="标签（交集）">
        <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
      </el-select>
      <el-select v-model="filters.difficulty" multiple collapse-tags placeholder="难度">
        <el-option label="简单" value="EASY" />
        <el-option label="中等" value="MEDIUM" />
        <el-option label="困难" value="HARD" />
      </el-select>
      <el-select v-model="filters.type" placeholder="题型" clearable>
        <el-option label="单选题" value="SINGLE_CHOICE" />
        <el-option label="填空题" value="SINGLE_BLANK" />
      </el-select>
      <el-input v-model="filters.keyword" placeholder="关键词" clearable @keyup.enter="search(0)" />
      <el-button type="primary" :loading="loading" @click="search(0)">搜索</el-button>
    </div>

    <p class="result-count">共 {{ results.total }} 道题</p>

    <div v-if="results.items.length === 0 && !loading" class="empty-state">
      没有匹配的题目，试试放宽筛选条件。
    </div>

    <article v-for="question in results.items" :key="question.questionId" class="question-card">
      <div class="question-meta">
        <el-tag size="small">
          {{ question.type === 'SINGLE_CHOICE' ? '单选' : '填空' }}
        </el-tag>
        <el-tag size="small" type="warning">{{ question.difficulty }}</el-tag>
        <span v-if="question.chapter" class="chapter">{{ question.chapter.name }}</span>
        <el-tag v-for="tag in question.tags" :key="tag.id" size="small" type="info" effect="plain">
          {{ tag.name }}
        </el-tag>
      </div>
      <p class="stem">{{ question.stem }}</p>
      <div class="card-actions">
        <el-button text type="primary" @click="toggleFavorite(question.questionId)">
          {{ favorites.has(question.questionId) ? '取消收藏' : '收藏' }}
        </el-button>
      </div>
    </article>

    <el-pagination
      v-if="results.total > results.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="results.page + 1"
      :page-size="results.size"
      :total="results.total"
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

.filter-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 0.75rem;
  margin: 1.25rem 0;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.8rem;
  background: var(--color-surface);
}

.result-count {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.question-card {
  padding: 1rem 1.2rem;
  margin-bottom: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 0.8rem;
  background: var(--color-surface);
}

.question-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
}

.chapter {
  color: var(--color-muted);
}

.stem {
  margin: 0.75rem 0 0.25rem;
  line-height: 1.7;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  padding: 2rem;
  border: 1px dashed var(--color-border);
  border-radius: 0.8rem;
  color: var(--color-muted);
  text-align: center;
}

.pagination {
  margin-top: 1.25rem;
}
</style>
