import type {
  AuthResponse,
  ChatResponse,
  ConversationDetail,
  ConversationSummary,
} from './types'

export const TOKEN_KEY = 'tts.token'
export const USERNAME_KEY = 'tts.username'

const GENERIC_ERROR = 'Đã xảy ra lỗi, vui lòng thử lại'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function readStored(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeStored(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // storage unavailable: ignore
  }
}

const defaultUnauthorized = () => window.location.assign('/login')
let onUnauthorized: () => void = defaultUnauthorized

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler ?? defaultUnauthorized
}

async function parseBody(res: Response): Promise<unknown> {
  const text = await res.text().catch(() => '')
  if (!text) return undefined
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isAuthEndpoint = path.startsWith('/api/auth/')
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = readStored(TOKEN_KEY)
  if (token && !isAuthEndpoint) headers.set('Authorization', `Bearer ${token}`)

  let res: Response
  try {
    res = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Không thể kết nối tới máy chủ')
  }

  const body = await parseBody(res)

  if (!res.ok) {
    if (res.status === 401 && !isAuthEndpoint) {
      writeStored(TOKEN_KEY, null)
      writeStored(USERNAME_KEY, null)
      onUnauthorized()
    }
    const message =
      body && typeof body === 'object' && typeof (body as { message?: unknown }).message === 'string'
        ? (body as { message: string }).message
        : GENERIC_ERROR
    throw new ApiError(res.status, message)
  }
  return body as T
}

function postJson<T>(path: string, payload: unknown): Promise<T> {
  return apiFetch<T>(path, { method: 'POST', body: JSON.stringify(payload) })
}

export const login = (username: string, password: string) =>
  postJson<AuthResponse>('/api/auth/login', { username, password })

export const register = (username: string, password: string) =>
  postJson<AuthResponse>('/api/auth/register', { username, password })

export const chat = (question: string, conversationId?: string) =>
  postJson<ChatResponse>('/api/chat', { question, ...(conversationId ? { conversationId } : {}) })

export const listConversations = () => apiFetch<ConversationSummary[]>('/api/conversations')

export const getConversation = (id: string) =>
  apiFetch<ConversationDetail>(`/api/conversations/${encodeURIComponent(id)}`)

export const deleteConversation = (id: string) =>
  apiFetch<void>(`/api/conversations/${encodeURIComponent(id)}`, { method: 'DELETE' })
