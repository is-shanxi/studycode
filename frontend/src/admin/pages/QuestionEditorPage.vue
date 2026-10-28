<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type {
  AdminQuestionDetail,
  CatalogTree,
  CreateQuestionPayload,
  Difficulty,
  OptionInput,
  QuestionType,
  Tag,
  UpdateDraftPayload,
  VersionView,
} from '@/features/api/types'
import {
  approveVersion,
  cloneVersion,
  createQuestion,
  deleteDraft,
  getAdminQuestion,
  rejectVersion,
  submitReview,
  updateDraft,
} from '@/features/admin/api'
import { getCatalogTree, getTags } from '@/features/practice/api'

const route = useRoute()
const router = useRouter()
const isNew = computed(() => route.name === 'admin-question-new')
const questionId = computed(() => (route.params.questionId ? String(route.params.questionId) : ''))

const catalog = ref<CatalogTree>({ certificates: [] })
const tags = ref<Tag[]>([])
const detail = ref<AdminQuestionDetail | null>(null)
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

const form = reactive({
  subjectId: '',
  chapterId: '',
  type: 'SINGLE_CHOICE' as QuestionType,
  stem: '',
  explanation: '',
  difficulty: 'MEDIUM' as Difficulty,
  sourceText: '',
  fillReferenceAnswer: '',
})
const options = ref<OptionInput[]>([])
const aliasesText = ref('')
const tagIds = ref<string[]>([])

const subjects = computed(() =>
  catalog.value.certificates.flatMap((certificate) =>
    certificate.subjects.map((subject) => ({ ...subject, certificateName: certificate.name })),
  ),
)
const chapters = computed(
  () => subjects.value.find((subject) => subject.id === form.subjectId)?.chapters ?? [],
)
const draftVersion = computed<VersionView | undefined>(() =>
  detail.value?.versions.find((version) => version.status === 'DRAFT'),
)
const pendingVersion = computed<VersionView | undefined>(() =>
  detail.value?.versions.find((version) => version.status === 'PENDING_REVIEW'),
)
const canEdit = computed(() => isNew.value || draftVersion.value !== undefined)

watch(
  () => form.subjectId,
  () => {
    if (!chapters.value.some((chapter) => chapter.id === form.chapterId)) {
      form.chapterId = chapters.value[0]?.id ?? ''
    }
  },
)

onMounted(async () => {
  loading.value = true
  try {
    const [tree, tagList] = await Promise.all([getCatalogTree(), getTags()])
    catalog.value = tree
    tags.value = tagList
    if (isNew.value) {
      form.subjectId = subjects.value[0]?.id ?? ''
      form.chapterId = subjects.value[0]?.chapters[0]?.id ?? ''
      options.value = [
        { key: 'A', content: '', correct: false },
        { key: 'B', content: '', correct: true },
      ]
    } else {
      await loadDetail()
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '编辑器初始化失败'
  } finally {
    loading.value = false
  }
})

async function loadDetail(): Promise<void> {
  if (!questionId.value) {
    return
  }
  detail.value = await getAdminQuestion(questionId.value)
  populate(detail.value)
}

function populate(value: AdminQuestionDetail): void {
  form.subjectId = value.subjectId
  form.chapterId = value.chapterId ?? ''
  form.type = value.type
  const version =
    value.versions.find((candidate) => candidate.status === 'DRAFT') ?? value.versions[0]
  if (!version) {
    return
  }
  form.stem = version.stem
  form.explanation = version.explanation ?? ''
  form.difficulty = version.difficulty
  form.sourceText = version.sourceText ?? ''
  form.fillReferenceAnswer = version.fillReferenceAnswer ?? ''
  aliasesText.value = version.aliases.join('|')
  tagIds.value = version.tags.map((tag) => tag.id)
  options.value = version.options.map((option) => ({
    key: option.key,
    content: option.content,
    correct: option.correct,
  }))
  if (value.type === 'SINGLE_CHOICE' && options.value.length === 0) {
    options.value = [
      { key: 'A', content: '', correct: false },
      { key: 'B', content: '', correct: true },
    ]
  }
}

function addOption(): void {
  const nextKey = String.fromCharCode(65 + options.value.length)
  options.value.push({ key: nextKey, content: '', correct: false })
}

function removeOption(index: number): void {
  options.value.splice(index, 1)
}

function selectCorrect(index: number): void {
  options.value.forEach((option, current) => {
    option.correct = current === index
  })
}

function commonPayload(): UpdateDraftPayload {
  const isChoice = form.type === 'SINGLE_CHOICE'
  return {
    chapterId: form.chapterId,
    stem: form.stem.trim(),
    explanation: form.explanation || undefined,
    fillReferenceAnswer: isChoice ? null : form.fillReferenceAnswer || null,
    difficulty: form.difficulty,
    sourceText: form.sourceText || undefined,
    options: isChoice
      ? options.value.map((option) => ({
          key: option.key.trim(),
          content: option.content.trim(),
          correct: option.correct,
        }))
      : [],
    aliases: isChoice
      ? []
      : aliasesText.value
          .split('|')
          .map((value) => value.trim())
          .filter((value) => value.length > 0),
    tagIds: tagIds.value,
  }
}

function validate(payload: UpdateDraftPayload): boolean {
  if (!form.subjectId || !form.chapterId) {
    error.value = '请选择科目和章节'
    return false
  }
  if (!payload.stem) {
    error.value = '题干不能为空'
    return false
  }
  if (form.type === 'SINGLE_CHOICE') {
    const validOptions = (payload.options ?? []).filter((option) => option.key && option.content)
    if (validOptions.length < 2) {
      error.value = '单选题至少需要两个选项'
      return false
    }
    if (validOptions.filter((option) => option.correct).length !== 1) {
      error.value = '单选题必须恰好有一个正确选项'
      return false
    }
  } else if (!payload.fillReferenceAnswer) {
    error.value = '填空题必须提供规范答案'
    return false
  }
  return true
}

async function create(): Promise<void> {
  const common = commonPayload()
  const payload: CreateQuestionPayload = {
    ...common,
    subjectId: form.subjectId,
    type: form.type,
    chapterId: form.chapterId,
    stem: form.stem.trim(),
    difficulty: form.difficulty,
  }
  if (!validate(payload)) {
    return
  }
  busy.value = true
  error.value = null
  try {
    const created = await createQuestion(payload)
    await router.replace({
      name: 'admin-question-edit',
      params: { questionId: created.questionId },
    })
    detail.value = created
    populate(created)
    success.value = '草稿已创建'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '创建失败'
  } finally {
    busy.value = false
  }
}

async function saveDraft(): Promise<boolean> {
  const draft = draftVersion.value
  if (!draft) {
    error.value = '当前没有可编辑的草稿版本'
    return false
  }
  const payload = commonPayload()
  if (!validate(payload)) {
    return false
  }
  busy.value = true
  error.value = null
  try {
    detail.value = await updateDraft(draft.versionId, payload)
    success.value = '草稿已保存'
    return true
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '保存失败'
    return false
  } finally {
    busy.value = false
  }
}

async function submitForReview(): Promise<void> {
  const saved = await saveDraft()
  if (!saved) {
    return
  }
  const draft = draftVersion.value
  if (!draft) {
    return
  }
  busy.value = true
  try {
    await submitReview(draft.versionId)
    await loadDetail()
    success.value = '已送审'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '送审失败'
  } finally {
    busy.value = false
  }
}

async function approve(): Promise<void> {
  if (!pendingVersion.value) {
    return
  }
  busy.value = true
  error.value = null
  try {
    await approveVersion(pendingVersion.value.versionId, true)
    await loadDetail()
    success.value = '已审核通过并发布'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '审核失败'
  } finally {
    busy.value = false
  }
}

async function reject(): Promise<void> {
  if (!pendingVersion.value) {
    return
  }
  const comment = window.prompt('请输入退回原因（可留空）') ?? undefined
  busy.value = true
  error.value = null
  try {
    await rejectVersion(pendingVersion.value.versionId, comment)
    await loadDetail()
    success.value = '已退回草稿'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '退回失败'
  } finally {
    busy.value = false
  }
}

async function createNewDraft(): Promise<void> {
  if (!questionId.value) {
    return
  }
  busy.value = true
  error.value = null
  try {
    detail.value = await cloneVersion(questionId.value)
    populate(detail.value)
    success.value = '已基于最新版本创建新草稿'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '创建草稿失败'
  } finally {
    busy.value = false
  }
}

async function removeDraft(): Promise<void> {
  if (!draftVersion.value || !window.confirm('确定删除该草稿版本吗？')) {
    return
  }
  busy.value = true
  try {
    await deleteDraft(draftVersion.value.versionId)
    await router.push({ name: 'admin-questions' })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '删除草稿失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">题目编辑</p>
        <h1>{{ isNew ? '新建题目' : '编辑题目与版本' }}</h1>
        <p class="lead">已发布内容不可原地修改；编辑会创建新的不可变版本。</p>
      </div>
      <div class="head-links">
        <RouterLink to="/admin/questions">返回列表</RouterLink>
        <RouterLink v-if="!isNew" to="/admin/review">去审核</RouterLink>
      </div>
    </header>

    <div v-if="loading" class="state">正在加载编辑器...</div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />

    <template v-if="!loading">
      <el-alert
        v-if="!isNew && !draftVersion"
        type="info"
        :closable="false"
        show-icon
        title="当前没有草稿版本。要修改已发布题目，请基于最新版本创建新草稿。"
      >
        <el-button class="inline-action" type="primary" size="small" @click="createNewDraft">
          创建新草稿
        </el-button>
      </el-alert>

      <div v-if="canEdit" class="editor-grid">
        <section class="editor-card">
          <h2>基础信息</h2>
          <el-form label-position="top">
            <el-form-item label="科目">
              <el-select v-model="form.subjectId" :disabled="!isNew" placeholder="选择科目">
                <el-option
                  v-for="subject in subjects"
                  :key="subject.id"
                  :label="`${subject.certificateName} / ${subject.name}`"
                  :value="subject.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="章节">
              <el-select v-model="form.chapterId" placeholder="选择章节">
                <el-option
                  v-for="chapter in chapters"
                  :key="chapter.id"
                  :label="chapter.name"
                  :value="chapter.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="题型">
              <el-select v-model="form.type" :disabled="!isNew">
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="填空题" value="SINGLE_BLANK" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度">
              <el-select v-model="form.difficulty">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </el-form-item>
            <el-form-item label="标签">
              <el-select v-model="tagIds" multiple collapse-tags placeholder="选择标签">
                <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </section>

        <section class="editor-card wide">
          <h2>题目内容</h2>
          <el-form label-position="top">
            <el-form-item label="题干">
              <el-input v-model="form.stem" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="官方解析">
              <el-input v-model="form.explanation" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="来源说明">
              <el-input v-model="form.sourceText" type="textarea" :rows="2" />
            </el-form-item>

            <template v-if="form.type === 'SINGLE_CHOICE'">
              <div class="option-head">
                <h3>选项与正确答案</h3>
                <el-button size="small" @click="addOption">添加选项</el-button>
              </div>
              <div v-for="(option, index) in options" :key="index" class="option-row">
                <el-input v-model="option.key" class="key-input" maxlength="8" />
                <el-input v-model="option.content" placeholder="选项内容" />
                <el-radio
                  :model-value="option.correct ? index : -1"
                  :value="index"
                  @change="selectCorrect(index)"
                >
                  正确
                </el-radio>
                <el-button text type="danger" @click="removeOption(index)">删除</el-button>
              </div>
            </template>

            <template v-else>
              <el-form-item label="规范答案">
                <el-input v-model="form.fillReferenceAnswer" placeholder="例如：SYN" />
              </el-form-item>
              <el-form-item label="可接受别名（用 | 分隔）">
                <el-input v-model="aliasesText" placeholder="别名1|别名2" />
              </el-form-item>
            </template>
          </el-form>
        </section>
      </div>

      <div v-if="canEdit" class="action-bar">
        <el-button v-if="isNew" type="primary" :loading="busy" @click="create">创建草稿</el-button>
        <template v-else>
          <el-button type="primary" :loading="busy" @click="saveDraft">保存草稿</el-button>
          <el-button :loading="busy" @click="submitForReview">送审</el-button>
          <el-button text type="danger" :loading="busy" @click="removeDraft">删除草稿</el-button>
        </template>
      </div>

      <div v-if="pendingVersion" class="review-bar">
        <span>待审核版本 v{{ pendingVersion.versionNo }}</span>
        <el-button type="success" :loading="busy" @click="approve">通过并发布</el-button>
        <el-button type="danger" plain :loading="busy" @click="reject">退回草稿</el-button>
      </div>

      <section v-if="detail" class="history-card">
        <h2>版本历史</h2>
        <div v-for="version in detail.versions" :key="version.versionId" class="version-row">
          <span class="version-no">v{{ version.versionNo }}</span>
          <el-tag size="small" effect="plain">{{ version.status }}</el-tag>
          <span class="version-time">
            {{
              version.publishedAt ? new Date(version.publishedAt).toLocaleString('zh-CN') : '未发布'
            }}
          </span>
          <span v-if="version.supersededAt" class="version-time">已被新版本替代</span>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.page {
  width: min(72rem, 100%);
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
  color: #3157c8;
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
  margin: 0 0 0.9rem;
  font-size: 1.05rem;
}

h3 {
  margin: 0;
  font-size: 0.95rem;
}

.lead {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
}

.head-links {
  display: flex;
  gap: 1rem;
}

.head-links a {
  color: #3157c8;
  font-weight: 600;
  text-decoration: none;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 1rem;
  margin-top: 1rem;
}

.editor-card {
  padding: 1.25rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.option-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0.5rem 0;
}

.option-row {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.key-input :deep(.el-input__inner) {
  text-align: center;
}

.action-bar,
.review-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-top: 1rem;
  padding: 1rem 1.25rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.review-bar {
  border-color: #fde68a;
  background: #fffbeb;
}

.inline-action {
  margin-left: 0.75rem;
}

.history-card {
  margin-top: 1rem;
  padding: 1.25rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.version-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0;
  border-top: 1px solid #eef2f7;
  font-size: 0.9rem;
}

.version-no {
  font-weight: 700;
}

.version-time {
  color: var(--color-muted);
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>
