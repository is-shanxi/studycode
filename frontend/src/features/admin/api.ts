import { apiClient } from '@/features/http/client'
import type {
  AdminCertificateView,
  AdminChapterView,
  AdminQuestionDetail,
  AdminQuestionSummary,
  AdminSubjectView,
  AdminTagView,
  AdminUserView,
  AnnouncementResponse,
  AuditLogView,
  CatalogEntryPayload,
  ChapterCreatePayload,
  CreateQuestionPayload,
  DashboardView,
  ImportBatchView,
  ModerationReportView,
  PageResponse,
  QuestionStatusResponse,
  ResetPasswordPayload,
  ResolveReportPayload,
  ResolveReportResponse,
  SubjectCreatePayload,
  TagPayload,
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

export function searchUsers(params: {
  keyword?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<AdminUserView>> {
  return apiClient.get<PageResponse<AdminUserView>>(
    `/admin/users${queryString(params as Record<string, unknown>)}`,
  )
}

export function disableUser(userId: string): Promise<AdminUserView> {
  return apiClient.post<AdminUserView>(`/admin/users/${userId}/disable`)
}

export function enableUser(userId: string): Promise<AdminUserView> {
  return apiClient.post<AdminUserView>(`/admin/users/${userId}/enable`)
}

export function resetUserPassword(userId: string, payload: ResetPasswordPayload): Promise<void> {
  return apiClient.post<void>(`/admin/users/${userId}/reset-password`, payload)
}

export function listModerationReports(params: {
  source?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<ModerationReportView>> {
  return apiClient.get<PageResponse<ModerationReportView>>(
    `/admin/reports${queryString(params as Record<string, unknown>)}`,
  )
}

export function resolveModerationReport(
  reportId: string,
  payload: ResolveReportPayload,
): Promise<ResolveReportResponse> {
  return apiClient.post<ResolveReportResponse>(`/admin/reports/${reportId}/resolve`, payload)
}

export function getDashboard(): Promise<DashboardView> {
  return apiClient.get<DashboardView>('/admin/dashboard')
}

export function searchAuditLogs(params: {
  actorUserId?: string
  action?: string
  targetType?: string
  outcome?: string
  page?: number
  size?: number
}): Promise<PageResponse<AuditLogView>> {
  return apiClient.get<PageResponse<AuditLogView>>(
    `/admin/audit-logs${queryString(params as Record<string, unknown>)}`,
  )
}

export function sendAnnouncement(payload: {
  title: string
  body: string
}): Promise<AnnouncementResponse> {
  return apiClient.post<AnnouncementResponse>('/admin/notifications/announcements', payload)
}

export function listAdminCertificates(): Promise<AdminCertificateView[]> {
  return apiClient.get<AdminCertificateView[]>('/admin/catalog/certificates')
}

export function createAdminCertificate(
  payload: CatalogEntryPayload,
): Promise<AdminCertificateView> {
  return apiClient.post<AdminCertificateView>('/admin/catalog/certificates', payload)
}

export function updateAdminCertificate(
  certificateId: string,
  payload: CatalogEntryPayload,
): Promise<AdminCertificateView> {
  return apiClient.put<AdminCertificateView>(
    `/admin/catalog/certificates/${certificateId}`,
    payload,
  )
}

export function listAdminSubjects(certificateId?: string): Promise<AdminSubjectView[]> {
  return apiClient.get<AdminSubjectView[]>(
    `/admin/catalog/subjects${queryString({ certificateId })}`,
  )
}

export function createAdminSubject(payload: SubjectCreatePayload): Promise<AdminSubjectView> {
  return apiClient.post<AdminSubjectView>('/admin/catalog/subjects', payload)
}

export function updateAdminSubject(
  subjectId: string,
  payload: CatalogEntryPayload,
): Promise<AdminSubjectView> {
  return apiClient.put<AdminSubjectView>(`/admin/catalog/subjects/${subjectId}`, payload)
}

export function listAdminChapters(subjectId?: string): Promise<AdminChapterView[]> {
  return apiClient.get<AdminChapterView[]>(`/admin/catalog/chapters${queryString({ subjectId })}`)
}

export function createAdminChapter(payload: ChapterCreatePayload): Promise<AdminChapterView> {
  return apiClient.post<AdminChapterView>('/admin/catalog/chapters', payload)
}

export function updateAdminChapter(
  chapterId: string,
  payload: CatalogEntryPayload,
): Promise<AdminChapterView> {
  return apiClient.put<AdminChapterView>(`/admin/catalog/chapters/${chapterId}`, payload)
}

export function listAdminTags(): Promise<AdminTagView[]> {
  return apiClient.get<AdminTagView[]>('/admin/catalog/tags')
}

export function createAdminTag(payload: TagPayload): Promise<AdminTagView> {
  return apiClient.post<AdminTagView>('/admin/catalog/tags', payload)
}

export function updateAdminTag(tagId: string, payload: TagPayload): Promise<AdminTagView> {
  return apiClient.put<AdminTagView>(`/admin/catalog/tags/${tagId}`, payload)
}
