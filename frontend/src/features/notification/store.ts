import { defineStore } from 'pinia'

import { getUnreadNotificationCount } from '@/features/community/api'

export const useNotificationStore = defineStore('notifications', {
  state: () => ({
    unreadCount: 0,
    loading: false,
    error: null as string | null,
  }),

  actions: {
    async refresh(): Promise<void> {
      this.loading = true
      try {
        const result = await getUnreadNotificationCount()
        this.unreadCount = result.unreadCount
        this.error = null
      } catch (caught) {
        this.error = caught instanceof Error ? caught.message : '通知数量加载失败'
      } finally {
        this.loading = false
      }
    },

    clear(): void {
      this.unreadCount = 0
      this.error = null
      this.loading = false
    },
  },
})
