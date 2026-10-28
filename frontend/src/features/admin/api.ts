import { apiClient } from '@/features/http/client'
import type {
  AdminQuestionDetail,
  AdminQuestionSummary,
  CreateQuestionPayload,
  ImportBatchView,
  PageResponse,
  QuestionStatusResponse,
  UpdateDraftPayload,
} from '@/features/api/types'

function queryString(params: Record<string, unknown>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue
    }
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

export function searchAdminQuestions(params: {
  keyword?: string
  status?: string
  type?: string
  subjectId?: string
  page?: number
  size?: number
}): Promise<PageResponse<AdminQuestionSummary>> {
  return apiClient.get<PageResponse<AdminQuestionSummary>>(
    `/admin/questions${queryString(params as Record<string, unknown>)}`,
  )
}

export function createQuestion(payload: CreateQuestionPayload): Promise<AdminQuestionDetail> {
  return apiClient.post<AdminQuestionDetail>('/admin/questions', payload)
}

export function getAdminQuestion(questionId: string): Promise<AdminQuestionDetail> {
  return apiClient.get<AdminQuestionDetail>(`/admin/questions/${questionId}`)
}

export function cloneVersion(questionId: string): Promise<AdminQuestionDetail> {
  return apiClient.post<AdminQuestionDetail>(`/admin/questions/${questionId}/versions`)
}

export function updateDraft(
  versionId: string,
  payload: UpdateDraftPayload,
): Promise<AdminQuestionDetail> {
  return apiClient.put<AdminQuestionDetail>(`/admin/question-versions/${versionId}`, payload)
}

export function deleteDraft(versionId: string): Promise<void> {
  return apiClient.delete<void>(`/admin/question-versions/${versionId}`)
}

export function submitReview(versionId: string, comment?: string): Promise<QuestionStatusResponse> {
  return apiClient.post<QuestionStatusResponse>(
    `/admin/question-versions/${versionId}/submit-review`,
    { comment },
  )
}

export function approveVersion(
  versionId: string,
  publishNow = true,
  comment?: string,
): Promise<QuestionStatusResponse> {
  return apiClient.post<QuestionStatusResponse>(`/admin/question-versions/${versionId}/approve`, {
    publishNow,
    comment,
  })
}

export function rejectVersion(
  versionId: string,
  comment?: string,
): Promise<QuestionStatusResponse> {
  return apiClient.post<QuestionStatusResponse>(`/admin/question-versions/${versionId}/reject`, {
    comment,
  })
}

export function offlineQuestion(
  questionId: string,
  comment?: string,
): Promise<QuestionStatusResponse> {
  return apiClient.post<QuestionStatusResponse>(`/admin/questions/${questionId}/offline`, {
    comment,
  })
}

export function uploadImport(file: File): Promise<ImportBatchView> {
  const form = new FormData()
  form.append('file', file)
  return apiClient.post<ImportBatchView>('/admin/question-imports', form)
}

export function getImport(batchId: string): Promise<ImportBatchView> {
  return apiClient.get<ImportBatchView>(`/admin/question-imports/${batchId}`)
}

export function confirmImport(batchId: string): Promise<ImportBatchView> {
  return apiClient.post<ImportBatchView>(`/admin/question-imports/${batchId}/confirm`)
}

export function cancelImport(batchId: string): Promise<void> {
  return apiClient.delete<void>(`/admin/question-imports/${batchId}`)
}
