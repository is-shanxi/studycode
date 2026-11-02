<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import type {
  AdminChapterView,
  AdminQuestionSummary,
  AdminSubjectView,
  AiConfigView,
  AiGenerationBatchView,
  AiSettingsPayload,
} from '@/features/api/types'
import { listAdminChapters, listAdminSubjects, searchAdminQuestions } from '@/features/admin/api'
import {
  createGenerationBatch,
  getAiConfig,
  listGenerationBatches,
  updateAiConfig,
} from '@/features/ai/api'

const router = useRouter()
const subjects = ref<AdminSubjectView[]>([])
const chapters = ref<AdminChapterView[]>([])
const sourceQuestions = ref<AdminQuestionSummary[]>([])
const batches = ref<AiGenerationBatchView[]>([])
const total = ref(0)
const config = ref<AiConfigView | null>(null)
const loading = ref(false)
const submitting = ref(false)
const savingConfig = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

const form = reactive({
  subjectId: '',
  chapterId: '',
  sourceQuestionIds: [] as string[],
  targetCount: 30,
  difficulty: 'MEDIUM' as 'EASY' | 'MEDIUM' | 'HARD',
})

const configForm = reactive<AiSettingsPayload>({
  dailyRequestLimit: 10,
  sessionTurnLimit: 20,
  maxInputChars: 2000,
  teamDailyBudget: 1,
  reservedCost: 0.001,
  sendOfficialAnswer: false,
})

onMounted(refreshAll)

async function refreshAll(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [subjectList, batchPage, configView] = await Promise.all([
      listAdminSubjects(),
      listGenerationBatches(0, 20),
      getAiConfig(),
    ])
    subjects.value = subjectList
    batches.value = batchPage.items
    total.value = batchPage.total
    config.value = configView
    Object.assign(configForm, configView.value)
    if (!form.subjectId && subjectList[0]) {
      form.subjectId = subjectList[0].id
    }
    if (form.subjectId) {
      await loadSubjectCatalog()
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'AI 任务加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshBatches(): Promise<void> {
  try {
    const page = await listGenerationBatches(0, 20)
    batches.value = page.items
    total.value = page.total
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'AI 任务刷新失败'
  }
}

async function loadSubjectCatalog(): Promise<void> {
  if (!form.subjectId) {
    chapters.value = []
    sourceQuestions.value = []
    return
  }
  const [chapterList, questionPage] = await Promise.all([
    listAdminChapters(form.subjectId),
    searchAdminQuestions({ subjectId: form.subjectId, status: 'PUBLISHED', size: 100 }),
  ])
  chapters.value = chapterList
  sourceQuestions.value = questionPage.items
  form.chapterId = ''
  form.sourceQuestionIds = []
}

async function submitBatch(): Promise<void> {
  error.value = null
  notice.value = null
  if (!form.subjectId) {
    error.value = '请选择科目。'
    return
  }
  if (form.sourceQuestionIds.length === 0) {
    error.value = '至少选择一个已发布原题作为生成源。'
    return
  }
  submitting.value = true
  try {
    await createGenerationBatch({
      subjectId: form.subjectId,
      chapterId: form.chapterId || undefined,
      sourceQuestionIds: form.sourceQuestionIds,
      targetCount: form.targetCount,
      difficulty: form.difficulty,
    })
    notice.value = '生成任务已创建，调度器会自动领取；请稍后刷新查看候选。'
    ElMessage.success('AI 生成任务已创建')
    await refreshBatches()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '创建生成任务失败'
  } finally {
    submitting.value = false
  }
}

async function saveConfig(): Promise<void> {
  error.value = null
  notice.value = null
  savingConfig.value = true
  try {
    config.value = await updateAiConfig({ ...configForm })
    ElMessage.success(`AI 配置已保存为版本 ${config.value.versionNo}`)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'AI 配置保存失败'
  } finally {
    savingConfig.value = false
  }
}

function openCandidates(batch: AiGenerationBatchView): void {
  void router.push({ name: 'admin-ai-candidates', params: { batchId: batch.batchId } })
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  if (status === 'PENDING_REVIEW' || status === 'RUNNING') {
    return 'warning'
  }
  return 'info'
}

function formatTime(value?: string): string {
  return value ? new Date(value).toLocaleString() : '—'
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">AI 变式生成</p>
        <h1>批量生成与审核</h1>
        <p class="lead">
          管理员按章节和已发布原题创建生成任务；Java
          调度器使用数据库租约领取任务，候选经校验后进入人工审核。
        </p>
      </div>
      <div class="head-links">
        <el-button size="small" :loading="loading" @click="refreshBatches">刷新任务</el-button>
        <RouterLink to="/admin/catalog">目录标签</RouterLink>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="false" />

    <div class="grid">
      <el-form class="card" label-position="top" @submit.prevent="submitBatch">
        <h2>创建生成任务</h2>
        <el-form-item label="科目">
          <el-select v-model="form.subjectId" placeholder="选择科目" @change="loadSubjectCatalog">
            <el-option
              v-for="subject in subjects"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="章节（可选）">
          <el-select v-model="form.chapterId" clearable placeholder="不限章节">
            <el-option
              v-for="chapter in chapters"
              :key="chapter.id"
              :label="chapter.name"
              :value="chapter.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="源原题（可多选）">
          <el-select
            v-model="form.sourceQuestionIds"
            multiple
            filterable
            collapse-tags
            placeholder="选择已发布原题"
          >
            <el-option
              v-for="question in sourceQuestions"
              :key="question.questionId"
              :label="question.stem.slice(0, 48)"
              :value="question.questionId"
            />
          </el-select>
        </el-form-item>
        <div class="two">
          <el-form-item label="目标数量">
            <el-input-number v-model="form.targetCount" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="form.difficulty">
              <el-option label="易" value="EASY" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="难" value="HARD" />
            </el-select>
          </el-form-item>
        </div>
        <el-button type="primary" native-type="submit" :loading="submitting"> 创建任务 </el-button>
      </el-form>

      <el-form class="card" label-position="top" @submit.prevent="saveConfig">
        <h2>
          AI 额度配置
          <span v-if="config" class="version">版本 {{ config.versionNo }}</span>
        </h2>
        <div class="two">
          <el-form-item label="个人每日请求">
            <el-input-number v-model="configForm.dailyRequestLimit" :min="1" :max="1000" />
          </el-form-item>
          <el-form-item label="单会话轮次">
            <el-input-number v-model="configForm.sessionTurnLimit" :min="1" :max="200" />
          </el-form-item>
          <el-form-item label="单条输入上限">
            <el-input-number v-model="configForm.maxInputChars" :min="1" :max="20000" />
          </el-form-item>
          <el-form-item label="团队每日预算">
            <el-input-number
              v-model="configForm.teamDailyBudget"
              :min="0"
              :step="0.5"
              :precision="3"
            />
          </el-form-item>
          <el-form-item label="单次预占成本">
            <el-input-number
              v-model="configForm.reservedCost"
              :min="0.0001"
              :step="0.001"
              :precision="4"
            />
          </el-form-item>
        </div>
        <el-form-item label="官方答案上下文">
          <el-switch
            v-model="configForm.sendOfficialAnswer"
            active-text="允许发送官方答案和解析"
            inactive-text="默认不发送"
          />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="savingConfig">
          保存配置
        </el-button>
      </el-form>
    </div>

    <section class="card table-card">
      <header class="table-head">
        <h2>生成任务</h2>
        <span>共 {{ total }} 个任务</span>
      </header>
      <el-table v-loading="loading" :data="batches" empty-text="还没有生成任务">
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetCount" label="目标" width="80" />
        <el-table-column label="候选" width="80">
          <template #default="{ row }">{{ row.candidates.length }}</template>
        </el-table-column>
        <el-table-column prop="attemptCount" label="尝试" width="80" />
        <el-table-column prop="modelName" label="模型" width="140" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="错误" min-width="140">
          <template #default="{ row }">
            <span class="error-text">{{ row.lastError ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openCandidates(row)">查看候选</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
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
  max-width: 52rem;
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

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(22rem, 1fr));
  gap: 1rem;
  align-items: start;
}

.card {
  padding: 1.1rem 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
}

.card h2 {
  margin: 0 0 0.9rem;
  font-size: 1.05rem;
}

.version {
  margin-left: 0.5rem;
  color: var(--color-muted);
  font-size: 0.8rem;
  font-weight: 400;
}

.two {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: 0 1rem;
}

.table-card {
  padding-bottom: 0.5rem;
}

.table-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.table-head span {
  color: var(--color-muted);
  font-size: 0.82rem;
}

.error-text {
  color: #b91c1c;
  font-size: 0.8rem;
}
</style>
