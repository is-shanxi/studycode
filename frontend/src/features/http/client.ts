const API_BASE = '/api/v1'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  traceId?: string
  errors?: Array<{ field: string; message: string }>
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string
  readonly fieldErrors?: Array<{ field: string; message: string }>

  constructor(status: number, problem: ProblemDetails) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code ?? 'UNKNOWN_ERROR'
    this.traceId = problem.traceId
    this.fieldErrors = problem.errors
  }
}

export interface RequestOptions {
  method?: string
  body?: unknown
  headers?: HeadersInit
  signal?: AbortSignal
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  const entry = document.cookie.split('; ').find((cookie) => cookie.startsWith(prefix))
  if (!entry) {
    return null
  }
  const value = decodeURIComponent(entry.slice(prefix.length))
  return value.length > 0 ? value : null
}

function nextTraceId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `trace-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetails = { status: response.status, title: response.statusText }
  try {
    problem = (await response.json()) as ProblemDetails
  } catch {
    // Keep the status-based fallback when the response is not JSON.
  }
  return new ApiError(response.status, problem)
}

export async function ensureCsrfToken(): Promise<string> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) {
    return existing
  }

  const response = await fetch(`${API_BASE}/auth/csrf`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw await toApiError(response)
  }

  const payload = (await response.json()) as { token?: string }
  const token = readCookie(CSRF_COOKIE) ?? payload.token
  if (!token) {
    throw new ApiError(500, {
      code: 'CSRF_TOKEN_MISSING',
      detail: 'Unable to obtain a CSRF token.',
    })
  }
  return token
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  headers.set('X-Trace-Id', nextTraceId())

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (UNSAFE_METHODS.has(method)) {
    headers.set(CSRF_HEADER, await ensureCsrfToken())
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    credentials: 'include',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (text.length === 0) {
    return undefined as T
  }
  const contentType = response.headers.get('Content-Type') ?? ''
  return contentType.includes('application/json') ? (JSON.parse(text) as T) : (text as T)
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'DELETE' }),
}
