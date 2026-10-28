import { defineStore } from 'pinia'

import { ApiError, apiClient } from '@/features/http/client'

export type Role = 'USER' | 'ADMIN'

export type SessionStatus = 'unknown' | 'loading' | 'authenticated' | 'anonymous'

export interface SessionUser {
  userId: string
  username: string
  nickname?: string
  roles: Role[]
}

export interface LoginCredentials {
  username: string
  password: string
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    user: null as SessionUser | null,
    status: 'unknown' as SessionStatus,
    error: null as string | null,
  }),

  getters: {
    isAuthenticated: (state): boolean => state.status === 'authenticated' && state.user !== null,
    isAdmin: (state): boolean => state.user?.roles.includes('ADMIN') ?? false,
  },

  actions: {
    hasRole(role: Role): boolean {
      return this.user?.roles.includes(role) ?? false
    },

    async fetchCurrentUser(): Promise<void> {
      this.status = 'loading'
      this.error = null
      try {
        this.user = await apiClient.get<SessionUser>('/auth/me')
        this.status = 'authenticated'
      } catch (error) {
        this.user = null
        this.status = 'anonymous'
        if (!(error instanceof ApiError) || error.status !== 401) {
          this.error = error instanceof Error ? error.message : '会话加载失败'
        }
      }
    },

    async login(credentials: LoginCredentials): Promise<void> {
      await apiClient.post<SessionUser>('/auth/sessions', credentials)
      await this.fetchCurrentUser()
    },

    async register(payload: {
      username: string
      password: string
      passwordConfirmation: string
    }): Promise<void> {
      await apiClient.post<SessionUser>('/auth/register', payload)
      await this.login({ username: payload.username, password: payload.password })
    },

    async logout(): Promise<void> {
      try {
        await apiClient.delete<void>('/auth/sessions/current')
      } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401) {
          console.warn('Logout request failed; clearing local session state.', error)
        }
      } finally {
        this.user = null
        this.status = 'anonymous'
        this.error = null
      }
    },

    reset(): void {
      this.user = null
      this.status = 'unknown'
      this.error = null
    },
  },
})
