import type { APIRequestContext } from '@playwright/test'

export interface SessionUser {
  userId: string
  username: string
  nickname?: string
  roles: string[]
}

export async function csrfToken(request: APIRequestContext): Promise<string> {
  let state = await request.storageState()
  let token = state.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  if (!token) {
    const response = await request.get('/api/v1/auth/csrf')
    if (!response.ok()) {
      throw new Error(`csrf bootstrap failed: ${response.status()}`)
    }
    state = await request.storageState()
    token = state.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  }
  if (!token) {
    throw new Error('XSRF-TOKEN cookie is missing')
  }
  return token
}

export async function apiLogin(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<SessionUser> {
  const token = await csrfToken(request)
  const response = await request.post('/api/v1/auth/sessions', {
    headers: { 'X-XSRF-TOKEN': token },
    data: { username, password },
  })
  if (!response.ok()) {
    throw new Error(`login failed: ${response.status()} ${await response.text()}`)
  }
  return (await response.json()) as SessionUser
}

export async function apiJson<T>(
  request: APIRequestContext,
  method: string,
  path: string,
  data?: unknown,
  headers: Record<string, string> = {},
): Promise<T> {
  const token = method === 'GET' ? undefined : await csrfToken(request)
  const response = await request.fetch(path, {
    method,
    data,
    headers: {
      ...(token ? { 'X-XSRF-TOKEN': token } : {}),
      ...headers,
    },
  })
  if (!response.ok()) {
    throw new Error(`${method} ${path} failed: ${response.status()} ${await response.text()}`)
  }
  if (response.status() === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
