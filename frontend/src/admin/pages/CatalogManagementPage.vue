<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import type {
  AdminCertificateView,
  AdminChapterView,
  AdminSubjectView,
  AdminTagView,
  CatalogEntryPayload,
  CatalogStatus,
} from '@/features/api/types'
import {
  createAdminCertificate,
  createAdminChapter,
  createAdminSubject,
  createAdminTag,
  listAdminCertificates,
  listAdminChapters,
  listAdminSubjects,
  listAdminTags,
  updateAdminCertificate,
  updateAdminChapter,
  updateAdminSubject,
  updateAdminTag,
} from '@/features/admin/api'

type EditorKind = 'certificate' | 'subject' | 'chapter' | 'tag'

interface EditableEntry {
  id: string
  code?: string
  name: string
  status: CatalogStatus
  sortOrder?: number
}

const kindLabels: Record<EditorKind, string> = {
  certificate: '证书',
  subject: '科目',
  chapter: '章节',
  tag: '标签',
}

const certificates = ref<AdminCertificateView[]>([])
const subjects = ref<AdminSubjectView[]>([])
const chapters = ref<AdminChapterView[]>([])
const tags = ref<AdminTagView[]>([])
const selectedCertificateId = ref('')
const selectedSubjectId = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editorKind = ref<EditorKind>('certificate')
const editingId = ref<string | null>(null)
const formError = ref<string | null>(null)
const submitting = ref(false)
const form = reactive({
  code: '',
  name: '',
  status: 'ACTIVE' as CatalogStatus,
  sortOrder: 0,
})

const selectedCertificate = computed(
  () => certificates.value.find((item) => item.id === selectedCertificateId.value) ?? null,
)
const selectedSubject = computed(
  () => subjects.value.find((item) => item.id === selectedSubjectId.value) ?? null,
)
const dialogTitle = computed(
  () => `${dialogMode.value === 'create' ? '新增' : '编辑'}${kindLabels[editorKind.value]}`,
)
const showEntryFields = computed(() => editorKind.value !== 'tag')
const parentHint = computed(() => {
  if (editorKind.value === 'subject') {
    return selectedCertificate.value
      ? `归属证书：${selectedCertificate.value.name}`
      : '请先选择证书'
  }
  if (editorKind.value === 'chapter') {
    return selectedSubject.value ? `归属科目：${selectedSubject.value.name}` : '请先选择科目'
  }
  return ''
})

async function refresh(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    certificates.value = await listAdminCertificates()
    if (!certificates.value.some((item) => item.id === selectedCertificateId.value)) {
      selectedCertificateId.value = certificates.value[0]?.id ?? ''
    }
    await refreshSubjects()
    await refreshChapters()
    tags.value = await listAdminTags()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '目录加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshSubjects(): Promise<void> {
  subjects.value = selectedCertificateId.value
    ? await listAdminSubjects(selectedCertificateId.value)
    : []
  if (!subjects.value.some((item) => item.id === selectedSubjectId.value)) {
    selectedSubjectId.value = subjects.value[0]?.id ?? ''
  }
}

async function refreshChapters(): Promise<void> {
  chapters.value = selectedSubjectId.value ? await listAdminChapters(selectedSubjectId.value) : []
}

async function onCertificateSelect(row: AdminCertificateView | null): Promise<void> {
  if (!row || row.id === selectedCertificateId.value) {
    return
  }
  selectedCertificateId.value = row.id
  selectedSubjectId.value = ''
  error.value = null
  try {
    await refreshSubjects()
    await refreshChapters()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '科目加载失败'
  }
}

async function onSubjectSelect(row: AdminSubjectView | null): Promise<void> {
  if (!row || row.id === selectedSubjectId.value) {
    return
  }
  selectedSubjectId.value = row.id
  error.value = null
  try {
    await refreshChapters()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '章节加载失败'
  }
}

function nextSortOrder(kind: EditorKind): number {
  if (kind === 'tag') {
    return 0
  }
  const values =
    kind === 'certificate'
      ? certificates.value.map((item) => item.sortOrder)
      : kind === 'subject'
        ? subjects.value.map((item) => item.sortOrder)
        : chapters.value.map((item) => item.sortOrder)
  return (values.length === 0 ? 0 : Math.max(...values)) + 10
}

function openCreate(kind: EditorKind): void {
  if (kind === 'subject' && !selectedCertificateId.value) {
    error.value = '请先选择证书后再新增科目。'
    return
  }
  if (kind === 'chapter' && !selectedSubjectId.value) {
    error.value = '请先选择科目后再新增章节。'
    return
  }
  editorKind.value = kind
  dialogMode.value = 'create'
  editingId.value = null
  formError.value = null
  form.code = ''
  form.name = ''
  form.status = 'ACTIVE'
  form.sortOrder = nextSortOrder(kind)
  dialogVisible.value = true
}

function openEdit(kind: EditorKind, entry: EditableEntry): void {
  editorKind.value = kind
  dialogMode.value = 'edit'
  editingId.value = entry.id
  formError.value = null
  form.code = entry.code ?? ''
  form.name = entry.name
  form.status = entry.status
  form.sortOrder = entry.sortOrder ?? 0
  dialogVisible.value = true
}

async function submitDialog(): Promise<void> {
  formError.value = null
  if (!form.name.trim()) {
    formError.value = '名称不能为空。'
    return
  }
  if (showEntryFields.value) {
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(form.code.trim())) {
      formError.value =
        '编码需以字母或数字开头，仅含字母、数字、点、下划线或连字符，最长 64 个字符。'
      return
    }
    if (form.sortOrder < 0) {
      formError.value = '排序值不能为负数。'
      return
    }
  }
  submitting.value = true
  try {
    await persistEntry()
    notice.value = `${kindLabels[editorKind.value]}已保存。`
    dialogVisible.value = false
    await refresh()
  } catch (caught) {
    formError.value = caught instanceof Error ? caught.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

async function persistEntry(): Promise<void> {
  const entry: CatalogEntryPayload = {
    code: form.code.trim().toUpperCase(),
    name: form.name.trim(),
    status: form.status,
    sortOrder: form.sortOrder,
  }
  const editing = editingId.value
  switch (editorKind.value) {
    case 'certificate':
      if (editing) {
        await updateAdminCertificate(editing, entry)
      } else {
        await createAdminCertificate(entry)
      }
      break
    case 'subject':
      if (editing) {
        await updateAdminSubject(editing, entry)
      } else if (selectedCertificateId.value) {
        await createAdminSubject({ ...entry, certificateId: selectedCertificateId.value })
      } else {
        throw new Error('请先选择证书。')
      }
      break
    case 'chapter':
      if (editing) {
        await updateAdminChapter(editing, entry)
      } else if (selectedSubjectId.value) {
        await createAdminChapter({ ...entry, subjectId: selectedSubjectId.value })
      } else {
        throw new Error('请先选择科目。')
      }
      break
    case 'tag':
      if (editing) {
        await updateAdminTag(editing, { name: form.name.trim(), status: form.status })
      } else {
        await createAdminTag({ name: form.name.trim(), status: form.status })
      }
      break
  }
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString()
}

function statusTagType(status: CatalogStatus): 'success' | 'info' {
  return status === 'ACTIVE' ? 'success' : 'info'
}

function statusLabel(status: CatalogStatus): string {
  return status === 'ACTIVE' ? '启用' : '停用'
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">运营管理</p>
        <h1>目录与标签</h1>
        <p class="lead">
          维护证书、科目、章节和标签。停用只会从用户端目录与标签筛选中隐藏，不会删除历史题目引用。
        </p>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="true" />

    <div v-loading="loading">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>证书</h2>
            <p>证书是目录树的根节点；编码按大写保存且全局唯一。</p>
          </div>
          <el-button type="primary" @click="openCreate('certificate')">新增证书</el-button>
        </div>
        <el-table
          :data="certificates"
          row-key="id"
          highlight-current-row
          :current-row-key="selectedCertificateId"
          @current-change="onCertificateSelect"
        >
          <el-table-column prop="code" label="编码" min-width="140" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button text type="primary" @click="openEdit('certificate', row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>科目</h2>
            <p>当前证书：{{ selectedCertificate?.name ?? '未选择' }}；科目编码在同一证书内唯一。</p>
          </div>
          <el-button
            type="primary"
            :disabled="!selectedCertificateId"
            @click="openCreate('subject')"
          >
            新增科目
          </el-button>
        </div>
        <el-table
          :data="subjects"
          row-key="id"
          highlight-current-row
          :current-row-key="selectedSubjectId"
          empty-text="该证书下暂无科目"
          @current-change="onSubjectSelect"
        >
          <el-table-column prop="code" label="编码" min-width="140" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button text type="primary" @click="openEdit('subject', row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>章节</h2>
            <p>当前科目：{{ selectedSubject?.name ?? '未选择' }}；章节编码在同一科目内唯一。</p>
          </div>
          <el-button type="primary" :disabled="!selectedSubjectId" @click="openCreate('chapter')">
            新增章节
          </el-button>
        </div>
        <el-table :data="chapters" row-key="id" empty-text="该科目下暂无章节">
          <el-table-column prop="code" label="编码" min-width="140" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button text type="primary" @click="openEdit('chapter', row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>标签</h2>
            <p>标签名称按归一化结果唯一；停用后不再出现在用户端标签筛选中。</p>
          </div>
          <el-button type="primary" @click="openCreate('tag')">新增标签</el-button>
        </div>
        <el-table :data="tags" row-key="id">
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="normalizedName" label="归一化名称" min-width="160" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button text type="primary" @click="openEdit('tag', row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(34rem, 92vw)">
      <div class="dialog-body">
        <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" />
        <p v-if="parentHint" class="hint">{{ parentHint }}</p>
        <el-form label-position="top">
          <template v-if="showEntryFields">
            <el-form-item label="编码">
              <el-input v-model="form.code" maxlength="64" placeholder="例如 CERT-DEMO" />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="form.name" maxlength="128" placeholder="显示名称" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" :max="100000" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="标签名称">
              <el-input v-model="form.name" maxlength="64" placeholder="标签名称" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDialog">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page {
  width: min(72rem, 100%);
  margin: 0 auto;
}

.page-head {
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

.lead {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
}

.panel {
  margin-top: 1.25rem;
  padding: 1rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.panel-head h2 {
  margin: 0;
  font-size: 1.1rem;
}

.panel-head p,
.hint {
  margin: 0.25rem 0 0;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
</style>
