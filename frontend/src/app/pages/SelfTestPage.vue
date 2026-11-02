<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import type {
  CertificateNode,
  ChapterNode,
  PracticeFilters,
  SubjectNode,
  Tag,
} from '@/features/api/types'
import { createSession, getCatalogTree, getTags } from '@/features/practice/api'
import { newIdempotencyKey } from '@/features/http/idempotency'
import { buildSelfTestRequest } from '@/features/selftest/composition'

const router = useRouter()
const certificates = ref<CertificateNode[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)

const form = reactive({
  certificateId: '',
  subjectId: '',
  chapterIds: [] as string[],
  tagIds: [] as string[],
  historyDays: 90,
  requestedCount: 10,
  originalPercent: 60,
  aiPercent: 40,
})

const selectedCertificate = computed(
  () => certificates.value.find((item) => item.id === form.certificateId) ?? null,
)
const subjects = computed<SubjectNode[]>(() => selectedCertificate.value?.subjects ?? [])
const selectedSubject = computed(
  () => subjects.value.find((item) => item.id === form.subjectId) ?? null,
)
const chapters = computed<ChapterNode[]>(() => selectedSubject.value?.chapters ?? [])

onMounted(async () => {
  loading.value = true
  try {
    const [tree, tagList] = await Promise.all([getCatalogTree(), getTags()])
    certificates.value = tree.certificates
    tags.value = tagList
    const firstCertificate = tree.certificates[0]
    if (firstCertificate) {
      form.certificateId = firstCertificate.id
      form.subjectId = firstCertificate.subjects[0]?.id ?? ''
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '目录加载失败'
  } finally {
    loading.value = false
  }
})

function onCertificateChange(): void {
  form.subjectId = ''
  form.chapterIds = []
}

function onSubjectChange(): void {
  form.chapterIds = []
}

async function startSelfTest(): Promise<void> {
  error.value = null
  if (!form.subjectId) {
    error.value = '请选择科目后再开始自测。'
    return
  }
  const filters: PracticeFilters = {
    certificateId: form.certificateId || undefined,
    subjectId: form.subjectId,
    chapterIds: form.chapterIds.length > 0 ? form.chapterIds : undefined,
    tagIds: form.tagIds.length > 0 ? form.tagIds : undefined,
    historyDays: form.historyDays,
  }
  submitting.value = true
  try {
    const request = buildSelfTestRequest(
      filters,
      form.requestedCount,
      form.originalPercent / 100,
      form.aiPercent / 100,
    )
    const session = await createSession(request, newIdempotencyKey())
    await router.push({ name: 'app-practice', params: { sessionId: session.sessionId } })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '自测创建失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">个性化自测</p>
        <h1>按薄弱范围组一份自测</h1>
        <p class="lead">
          默认 10 题，按 6 道已审核原题与 4 道已审核 AI
          变式组卷；变式不足时用原题补齐，合法题不足时展示实际题量。
        </p>
      </div>
      <div class="head-links">
        <RouterLink to="/app/records">自测记录</RouterLink>
        <RouterLink to="/app/library">题库</RouterLink>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div v-if="loading" class="state">正在加载目录...</div>
    <div v-else class="layout">
      <el-form class="card" label-position="top" @submit.prevent="startSelfTest">
        <div class="grid-two">
          <el-form-item label="证书">
            <el-select
              v-model="form.certificateId"
              placeholder="选择证书"
              @change="onCertificateChange"
            >
              <el-option
                v-for="certificate in certificates"
                :key="certificate.id"
                :label="certificate.name"
                :value="certificate.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="科目（必选）">
            <el-select v-model="form.subjectId" placeholder="选择科目" @change="onSubjectChange">
              <el-option
                v-for="subject in subjects"
                :key="subject.id"
                :label="subject.name"
                :value="subject.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="章节（不选表示整个科目）">
          <el-select v-model="form.chapterIds" multiple collapse-tags placeholder="全部章节">
            <el-option
              v-for="chapter in chapters"
              :key="chapter.id"
              :label="chapter.name"
              :value="chapter.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标签（多选取交集）">
          <el-select v-model="form.tagIds" multiple collapse-tags placeholder="不限标签">
            <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>

        <div class="grid-three">
          <el-form-item label="历史窗口（天）">
            <el-input-number v-model="form.historyDays" :min="1" :max="3650" />
          </el-form-item>
          <el-form-item label="题量">
            <el-input-number v-model="form.requestedCount" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="原题占比（%）">
            <el-input-number v-model="form.originalPercent" :min="0" :max="100" />
          </el-form-item>
        </div>

        <el-form-item label="AI 变式占比（%）">
          <el-input-number v-model="form.aiPercent" :min="0" :max="100" />
          <span class="ratio-hint"> 实际比例会按两项之和归一化；默认 60% 原题 / 40% 变式。 </span>
        </el-form-item>

        <el-button type="primary" native-type="submit" :loading="submitting"> 开始自测 </el-button>
      </el-form>

      <aside class="card note">
        <h2>组卷规则</h2>
        <ul>
          <li>只抽取已发布原题和已审核发布的 AI 变式题，未审核候选不会进入自测。</li>
          <li>原题内部继续按未见、错题、普通已做和难度权重抽样，历史窗口参与薄弱点判断。</li>
          <li>变式题不足时优先用原题补足；合法题不足时使用现有题量；零题时无法开始。</li>
          <li>自测复用统一提交、填空自评、历史和错题规则，结果页会显示实际来源组成。</li>
        </ul>
      </aside>
    </div>
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
  max-width: 42rem;
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
  grid-template-columns: minmax(0, 1.6fr) minmax(16rem, 1fr);
  gap: 1rem;
  align-items: start;
}

.card {
  padding: 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.5rem 1rem;
}

.grid-three {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: 0.5rem 1rem;
}

.ratio-hint {
  margin-left: 0.75rem;
  color: var(--color-muted);
  font-size: 0.82rem;
}

.note h2 {
  margin: 0 0 0.75rem;
  font-size: 1.05rem;
}

.note ul {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--color-muted);
  line-height: 1.8;
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
