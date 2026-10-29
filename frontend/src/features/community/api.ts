import { apiClient } from '@/features/http/client'
import type {
  CreateCommentPayload,
  CreateDiscussionPayload,
  DiscussionCommentView,
  DiscussionPostDetail,
  DiscussionPostSummary,
  MarkAllReadResponse,
  NotificationView,
  PageResponse,
  ReportCreated,
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

export function listDiscussions(
  questionId: string,
  params: { page?: number; size?: number } = {},
): Promise<PageResponse<DiscussionPostSummary>> {
  return apiClient.get<PageResponse<DiscussionPostSummary>>(
    `/questions/${questionId}/discussions${queryString(params)}`,
  )
}

export function createDiscussion(
  questionId: string,
  payload: CreateDiscussionPayload,
): Promise<DiscussionPostSummary> {
  return apiClient.post<DiscussionPostSummary>(`/questions/${questionId}/discussions`, payload)
}

export function getDiscussion(postId: string): Promise<DiscussionPostDetail> {
  return apiClient.get<DiscussionPostDetail>(`/discussions/${postId}`)
}

export function createComment(
  postId: string,
  payload: CreateCommentPayload,
): Promise<DiscussionCommentView> {
  return apiClient.post<DiscussionCommentView>(`/discussions/${postId}/comments`, payload)
}

export function reportPost(postId: string, reason: string): Promise<ReportCreated> {
  return apiClient.post<ReportCreated>(`/discussions/${postId}/report`, { reason })
}

export function reportComment(commentId: string, reason: string): Promise<ReportCreated> {
  return apiClient.post<ReportCreated>(`/comments/${commentId}/report`, { reason })
}

export function createCorrectionReport(payload: {
  questionId: string
  questionVersionId: string
  target: 'STEM' | 'ANSWER' | 'EXPLANATION' | 'TAG' | 'OTHER'
  description: string
}): Promise<ReportCreated> {
  return apiClient.post<ReportCreated>('/correction-reports', payload)
}

export function listNotifications(
  params: {
    unreadOnly?: boolean
    page?: number
    size?: number
  } = {},
): Promise<PageResponse<NotificationView>> {
  return apiClient.get<PageResponse<NotificationView>>(`/me/notifications${queryString(params)}`)
}

export function markNotificationRead(notificationId: string): Promise<void> {
  return apiClient.put<void>(`/me/notifications/${notificationId}/read`)
}

export function markAllNotificationsRead(): Promise<MarkAllReadResponse> {
  return apiClient.put<MarkAllReadResponse>('/me/notifications/read-all')
}
