<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { ReportCreated } from '@/features/api/types'
import { createCorrectionReport, reportComment, reportPost } from '@/features/community/api'

type ReportMode = 'post' | 'comment' | 'correction'

const props = defineProps<{
  modelValue: boolean
  mode: ReportMode
  targetId: string
  questionId?: string
  questionVersionId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  submitted: [report: ReportCreated]
}>()

const reason = ref('')
const correctionTarget = ref<'STEM' | 'ANSWER' | 'EXPLANATION' | 'TAG' | 'OTHER'>('STEM')
const submitting = ref(false)
const error = ref<string | null>(null)

const dialogTitle = computed(() => {
  if (props.mode === 'correction') {
    return '报告题目错误'
  }
  return props.mode === 'comment' ? '举报评论' : '举报帖子'
})

const submitLabel = computed(() => (props.mode === 'correction' ? '提交纠错' : '提交举报'))

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      error.value = null
    }
  },
)

function close(): void {
  emit('update:modelValue', false)
}

async function submit(): Promise<void> {
  const text = reason.value.trim()
  if (!text) {
    error.value = props.mode === 'correction' ? '请填写纠错说明。' : '请填写举报原因。'
    return
  }
  submitting.value = true
  error.value = null
  try {
    let result: ReportCreated
    if (props.mode === 'correction') {
      if (!props.questionId || !props.questionVersionId) {
        throw new Error('缺少题目版本信息')
      }
      result = await createCorrectionReport({
        questionId: props.questionId,
        questionVersionId: props.questionVersionId,
        target: correctionTarget.value,
        description: text,
      })
    } else if (props.mode === 'comment') {
      result = await reportComment(props.targetId, text)
    } else {
      result = await reportPost(props.targetId, text)
    }
    reason.value = ''
    emit('submitted', result)
    close()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '提交失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="min(32rem, 92vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dialog-body">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-select v-if="mode === 'correction'" v-model="correctionTarget" placeholder="错误类型">
        <el-option label="题干" value="STEM" />
        <el-option label="答案" value="ANSWER" />
        <el-option label="解析" value="EXPLANATION" />
        <el-option label="标签" value="TAG" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <el-input
        v-model="reason"
        type="textarea"
        :rows="4"
        maxlength="5000"
        show-word-limit
        :placeholder="mode === 'correction' ? '说明你发现的错误' : '说明举报原因'"
      />
      <p class="hint">管理员会在处理队列中查看；处理结果会通过站内通知告知你。</p>
    </div>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ submitLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.8rem;
}
</style>
