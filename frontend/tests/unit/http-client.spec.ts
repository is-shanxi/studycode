import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, apiClient } from '@/features/http/client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
  })

  it('adds credentials, trace and CSRF headers to unsafe requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ sessionId: 'session-1' }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await apiClient.post<{ sessionId: string }>('/practice-sessions', {
      requestedCount: 10,
    })

    expect(result.sessionId).toBe('session-1')
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('/api/v1/practice-sessions')
    expect(init.credentials).toBe('include')
    const headers = new Headers(init.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('token-123')
    expect(headers.get('X-Trace-Id')).toBeTruthy()
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('maps RFC Problem Details into ApiError', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          type: 'https://local.cert-practice/errors/answers-incomplete',
          title: 'Answers are incomplete',
          status: 422,
          detail: 'All session items must be answered before submission.',
          code: 'ANSWERS_INCOMPLETE',
          traceId: '9d0d37aa-1111-2222-3333-444455556666',
        },
        422,
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    const error = await apiClient
      .post('/practice-sessions/session-1/submit')
      .then(() => null)
      .catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    if (!(error instanceof ApiError)) {
      throw new Error('Expected ApiError')
    }
    expect(error.status).toBe(422)
    expect(error.code).toBe('ANSWERS_INCOMPLETE')
    expect(error.traceId).toBe('9d0d37aa-1111-2222-3333-444455556666')
  })
})
