import { beforeEach, describe, expect, it, vi } from 'vitest'

import { selfAssess } from '@/features/practice/api'

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}))

vi.mock('@/features/http/client', () => ({
  apiClient: { post: postMock },
}))

describe('practice API', () => {
  beforeEach(() => {
    postMock.mockReset()
    postMock.mockResolvedValue({
      itemId: 'item-1',
      finalResult: 'PASS',
      sessionStatus: 'PENDING_SELF_ASSESSMENT',
    })
  })

  it('sends an idempotency key for self-assessment', async () => {
    await selfAssess('session-1', 'item-1', 'PASS', 'key-1')

    expect(postMock).toHaveBeenCalledWith(
      '/practice-sessions/session-1/items/item-1/self-assessment',
      { result: 'PASS' },
      { headers: { 'Idempotency-Key': 'key-1' } },
    )
  })

  it('omits the options object when no key is supplied', async () => {
    await selfAssess('session-1', 'item-1', 'FAIL')

    expect(postMock).toHaveBeenCalledWith(
      '/practice-sessions/session-1/items/item-1/self-assessment',
      { result: 'FAIL' },
      undefined,
    )
  })
})
