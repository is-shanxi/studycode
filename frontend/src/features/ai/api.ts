import { apiClient } from '@/features/http/client'
import { newIdempotencyKey } from '@/features/http/idempotency'
import type {
  AiConfigView,
  AiConversationScope,
  AiConversationView,
  AiGenerationBatchView,
  AiGenerationCandidateView,
  AiQuotaView,
  AiSendMessageResponse,
  AiSettingsPayload,
  PageResponse,
} from '@/features/api/types'

export function getAiQuota(conversationId?: string): Promise<AiQuotaView> {
  const query = conversationId ? `?conversationId=${encodeURIComponent(conversationId)}` : ''
  return apiClient.get<AiQuotaView>(`/ai/quota${query}`)
}

export function createAiConversation(
  scope: AiConversationScope,
  sessionItemId?: string,
): Promise<AiConversationView> {
  return apiClient.post<AiConversationView>('/ai/conversations', {
    scope,
    ...(sessionItemId ? { sessionItemId } : {}),
  })
}

export function getAiConversation(conversationId: string): Promise<AiConversationView> {
  return apiClient.get<AiConversationView>(`/ai/conversations/${conversationId}`)
}

export { newIdempotencyKey }

export function sendAiMessage(
  conversationId: string,
  content: string,
  idempotencyKey: string = newIdempotencyKey(),
): Promise<AiSendMessageResponse> {
  return apiClient.post<AiSendMessageResponse>(
    `/ai/conversations/${conversationId}/messages`,
    { content },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
}

export function clearAiConversation(conversationId: string): Promise<void> {
  return apiClient.delete<void>(`/ai/conversations/${conversationId}`)
}

export interface GenerationBatchPayload {
  subjectId: string
  chapterId?: string
  sourceQuestionIds: string[]
  targetCount: number
  difficulty?: 'EASY' | 'MEDIUM' | 'HARD'
}

export function listGenerationBatches(
  page = 0,
  size = 20,
): Promise<PageResponse<AiGenerationBatchView>> {
  return apiClient.get<PageResponse<AiGenerationBatchView>>(
    `/admin/ai/generation-batches?page=${page}&size=${size}`,
  )
}

export function createGenerationBatch(
  payload: GenerationBatchPayload,
): Promise<AiGenerationBatchView> {
  return apiClient.post<AiGenerationBatchView>('/admin/ai/generation-batches', payload)
}

export function getGenerationBatch(batchId: string): Promise<AiGenerationBatchView> {
  return apiClient.get<AiGenerationBatchView>(`/admin/ai/generation-batches/${batchId}`)
}

export function reviewGenerationCandidate(
  candidateId: string,
  decision: 'APPROVE' | 'REJECT',
  comment?: string,
): Promise<AiGenerationCandidateView> {
  return apiClient.post<AiGenerationCandidateView>(
    `/admin/ai/generation-candidates/${candidateId}/review`,
    { decision, ...(comment ? { comment } : {}) },
  )
}

export function getAiConfig(): Promise<AiConfigView> {
  return apiClient.get<AiConfigView>('/admin/configs?key=ai.settings')
}

export function updateAiConfig(value: AiSettingsPayload): Promise<AiConfigView> {
  return apiClient.put<AiConfigView>('/admin/configs', { key: 'ai.settings', value })
}
