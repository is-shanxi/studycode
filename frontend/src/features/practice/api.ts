import { apiClient } from '@/features/http/client'
import type {
  CatalogTree,
  CreateSessionRequest,
  FavoriteView,
  PageResponse,
  PublicQuestionView,
  QuestionSummary,
  RecordSummary,
  ResultView,
  SaveAnswerRequest,
  SaveAnswerResponse,
  SearchParams,
  SessionView,
  Tag,
  WrongQuestionView,
} from '@/features/api/types'

function queryString(params: Record<string, unknown>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue
    }
    if (Array.isArray(value)) {
      if (value.length > 0) {
        search.set(key, value.join(','))
      }
    } else {
      search.set(key, String(value))
    }
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

export function getCatalogTree(): Promise<CatalogTree> {
  return apiClient.get<CatalogTree>('/catalog/tree')
}

export function getTags(): Promise<Tag[]> {
  return apiClient.get<Tag[]>('/tags')
}

export function searchQuestions(params: SearchParams): Promise<PageResponse<QuestionSummary>> {
  return apiClient.get<PageResponse<QuestionSummary>>(
    `/questions/search${queryString(params as Record<string, unknown>)}`,
  )
}

export function getQuestion(questionId: string): Promise<PublicQuestionView> {
  return apiClient.get<PublicQuestionView>(`/questions/${questionId}`)
}

export function createSession(
  body: CreateSessionRequest,
  idempotencyKey?: string,
): Promise<SessionView> {
  return apiClient.post<SessionView>('/practice-sessions', body, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  })
}

export function getSession(sessionId: string): Promise<SessionView> {
  return apiClient.get<SessionView>(`/practice-sessions/${sessionId}`)
}

export function saveAnswer(
  sessionId: string,
  itemId: string,
  body: SaveAnswerRequest,
): Promise<SaveAnswerResponse> {
  return apiClient.put<SaveAnswerResponse>(
    `/practice-sessions/${sessionId}/items/${itemId}/answer`,
    body,
  )
}

export function submitSession(sessionId: string, idempotencyKey?: string): Promise<ResultView> {
  return apiClient.post<ResultView>(`/practice-sessions/${sessionId}/submit`, undefined, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  })
}

export function selfAssess(
  sessionId: string,
  itemId: string,
  result: 'PASS' | 'FAIL',
  idempotencyKey?: string,
): Promise<{ itemId: string; finalResult: string; sessionStatus: string }> {
  return apiClient.post(
    `/practice-sessions/${sessionId}/items/${itemId}/self-assessment`,
    { result },
    idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined,
  )
}

export function abandonSession(sessionId: string): Promise<void> {
  return apiClient.post<void>(`/practice-sessions/${sessionId}/abandon`)
}

export function getResult(sessionId: string): Promise<ResultView> {
  return apiClient.get<ResultView>(`/practice-sessions/${sessionId}/result`)
}

export function listRecords(params: {
  mode?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<RecordSummary>> {
  return apiClient.get<PageResponse<RecordSummary>>(
    `/me/records${queryString(params as Record<string, unknown>)}`,
  )
}

export function getRecord(sessionId: string): Promise<ResultView> {
  return apiClient.get<ResultView>(`/me/records/${sessionId}`)
}

export function listFavorites(): Promise<FavoriteView[]> {
  return apiClient.get<FavoriteView[]>('/me/favorites')
}

export function addFavorite(questionId: string): Promise<void> {
  return apiClient.put<void>(`/me/favorites/${questionId}`)
}

export function removeFavorite(questionId: string): Promise<void> {
  return apiClient.delete<void>(`/me/favorites/${questionId}`)
}

export function listWrongQuestions(): Promise<WrongQuestionView[]> {
  return apiClient.get<WrongQuestionView[]>('/me/wrong-questions')
}

export function removeWrongQuestion(questionId: string): Promise<void> {
  return apiClient.delete<void>(`/me/wrong-questions/${questionId}`)
}
