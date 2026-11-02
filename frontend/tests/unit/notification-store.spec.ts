import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useNotificationStore } from '@/features/notification/store'

const { unreadCountMock } = vi.hoisted(() => ({
  unreadCountMock: vi.fn(),
}))

vi.mock('@/features/community/api', () => ({
  getUnreadNotificationCount: unreadCountMock,
}))

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    unreadCountMock.mockReset()
  })

  it('refreshes the global unread badge count', async () => {
    unreadCountMock.mockResolvedValue({ unreadCount: 3 })

    const store = useNotificationStore()
    await store.refresh()

    expect(store.unreadCount).toBe(3)
    expect(unreadCountMock).toHaveBeenCalledOnce()
  })

  it('keeps the last known count when refresh fails', async () => {
    unreadCountMock.mockResolvedValue({ unreadCount: 2 })
    const store = useNotificationStore()
    await store.refresh()

    unreadCountMock.mockRejectedValue(new Error('network down'))
    await store.refresh()

    expect(store.unreadCount).toBe(2)
    expect(store.error).toBe('network down')
  })

  it('clears the badge when the session ends', async () => {
    unreadCountMock.mockResolvedValue({ unreadCount: 4 })
    const store = useNotificationStore()
    await store.refresh()

    store.clear()

    expect(store.unreadCount).toBe(0)
    expect(store.error).toBeNull()
  })
})
