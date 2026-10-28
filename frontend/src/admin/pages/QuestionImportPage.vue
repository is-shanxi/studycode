<script setup lang="ts">
import { ref } from 'vue'

import type { ImportBatchView } from '@/features/api/types'
import { cancelImport, confirmImport, uploadImport } from '@/features/admin/api'

const selectedFile = ref<File | null>(null)
const batch = ref<ImportBatchView | null>(null)
const uploading = ref(false)
const confirming = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
  batch.value = null
  error.value = null
  notice.value = null
}

async function upload(): Promise<void> {
  if (!selectedFile.value) {
    error.value = '请选择 CSV 或 XLSX 文件'
    return
  }
  uploading.value = true
  error.value = null
  notice.value = null
  try {
    batch.value = await uploadImport(selectedFile.value)
    notice.value = '校验完成，请确认后导入'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '上传失败'
  } finally {
    uploading.value = false
  }
}

async function confirm(): Promise<void> {
  if (!batch.value) {
    return
  }
  confirming.value = true
  error.value = null
  try {
    batch.value = await confirmImport(batch.value.batchId)
    notice.value = '导入成功，题目已创建为草稿，仍需送审后才能发布'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '确认导入失败'
  } finally {
    confirming.value = false
  }
}

async function cancel(): Promise<void> {
  if (!batch.value) {
    return
  }
  try {
    await cancelImport(batch.value.batchId)
    batch.value = null
    selectedFile.value = null
    notice.value = '已取消导入任务'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '取消失败'
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">批量导入</p>
        <h1>题目导入</h1>
        <p class="lead">
          上传后先校验并预览错误，整批无错误才能确认。导入的题目仍处于草稿状态，必须送审发布。
        </p>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="false" />

    <section class="upload-card">
      <h2>选择文件</h2>
      <p class="hint">
        支持 CSV 与 XLSX。列名：type, subjectCode, chapterCode, stem, options, correctOption,
        fillAnswer, aliases, explanation, difficulty, source, tags。选项格式为
        <code>A:内容|B:内容</code>，别名和标签使用 <code>|</code> 分隔。
      </p>
      <input type="file" accept=".csv,.xlsx" @change="onFileChange" />
      <div class="upload-actions">
        <el-button type="primary" :loading="uploading" @click="upload">上传并校验</el-button>
        <span v-if="selectedFile" class="file-name">{{ selectedFile.name }}</span>
      </div>
    </section>

    <section v-if="batch" class="batch-card">
      <h2>校验结果</h2>
      <div class="batch-meta">
        <el-tag :type="batch.invalidRows > 0 ? 'danger' : 'success'">{{ batch.status }}</el-tag>
        <span>文件：{{ batch.fileName }}</span>
        <span>总行数：{{ batch.totalRows }}</span>
        <span>有效：{{ batch.validRows }}</span>
        <span>错误：{{ batch.invalidRows }}</span>
      </div>

      <div v-if="batch.errors.length > 0" class="error-table">
        <div class="error-head">
          <span>行号</span>
          <span>字段</span>
          <span>错误码</span>
          <span>说明</span>
        </div>
        <div v-for="(item, index) in batch.errors" :key="index" class="error-row">
          <span>{{ item.rowNumber }}</span>
          <span>{{ item.field ?? '—' }}</span>
          <span>{{ item.code }}</span>
          <span>{{ item.message }}</span>
        </div>
      </div>

      <div class="batch-actions">
        <el-button
          type="primary"
          :disabled="batch.invalidRows > 0 || batch.status === 'CONFIRMED'"
          :loading="confirming"
          @click="confirm"
        >
          确认导入
        </el-button>
        <el-button v-if="batch.status !== 'CONFIRMED'" type="danger" plain @click="cancel">
          取消任务
        </el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.page {
  width: min(64rem, 100%);
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

h2 {
  margin: 0 0 0.75rem;
  font-size: 1.05rem;
}

.lead {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
}

.upload-card,
.batch-card {
  margin-top: 1rem;
  padding: 1.25rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.hint {
  color: var(--color-muted);
  font-size: 0.88rem;
  line-height: 1.7;
}

code {
  padding: 0.1rem 0.3rem;
  border-radius: 0.25rem;
  background: #eef2f7;
}

.upload-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.9rem;
}

.file-name {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.batch-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  color: var(--color-muted);
  font-size: 0.9rem;
}

.error-table {
  margin-top: 1rem;
  border: 1px solid #fecaca;
  border-radius: 0.6rem;
  overflow: hidden;
}

.error-head,
.error-row {
  display: grid;
  grid-template-columns: 5rem 8rem 12rem minmax(0, 1fr);
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.85rem;
}

.error-head {
  background: #fef2f2;
  font-weight: 700;
  color: #991b1b;
}

.error-row {
  border-top: 1px solid #fee2e2;
}

.batch-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1rem;
}
</style>
