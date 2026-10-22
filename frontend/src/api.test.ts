import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, deleteConversation, setUnauthorizedHandler } from './api'

function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), { status })
}

describe('apiFetch', () => {
  const fetchMock = vi.fn()
  const onUnauthorized = vi.fn()

  beforeEach(() => {
    localStorage.clear()
    fetchMock.mockReset()
    onUnauthorized.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    setUnauthorizedHandler(onUnauthorized)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    setUnauthorizedHandler(null)
  })

  it('attaches the bearer token', async () => {
    localStorage.setItem('tts.token', 'x')
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }))
    await apiFetch('/api/conversations')
    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer x')
  })

  it('does not attach the token to auth endpoints', async () => {
    localStorage.setItem('tts.token', 'x')
    fetchMock.mockResolvedValue(jsonResponse(200, { token: 't', username: 'u' }))
    await apiFetch('/api/auth/login', { method: 'POST', body: '{}' })
    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.has('Authorization')).toBe(false)
  })

  it('clears token, calls onUnauthorized once and rejects on 401', async () => {
    localStorage.setItem('tts.token', 'x')
    fetchMock.mockResolvedValue(jsonResponse(401, { message: 'Hết hạn' }))
    await expect(apiFetch('/api/conversations')).rejects.toBeInstanceOf(ApiError)
    expect(localStorage.getItem('tts.token')).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('treats 401 on the login endpoint as a normal error', async () => {
    localStorage.setItem('tts.token', 'x')
    fetchMock.mockResolvedValue(jsonResponse(401, { message: 'Sai tên đăng nhập hoặc mật khẩu' }))
    await expect(apiFetch('/api/auth/login', { method: 'POST' })).rejects.toMatchObject({
      status: 401,
      message: 'Sai tên đăng nhập hoặc mật khẩu',
    })
    expect(onUnauthorized).not.toHaveBeenCalled()
    expect(localStorage.getItem('tts.token')).toBe('x')
  })

  it('uses the message from the error body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(409, { message: 'Tên đăng nhập đã tồn tại' }))
    await expect(apiFetch('/api/auth/register', { method: 'POST' })).rejects.toMatchObject({
      status: 409,
      message: 'Tên đăng nhập đã tồn tại',
    })
  })

  it('falls back to a generic message for non-JSON error bodies', async () => {
    fetchMock.mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }))
    const err = (await apiFetch('/api/chat').catch((e: unknown) => e)) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(502)
    expect(err.message).toBe('Đã xảy ra lỗi, vui lòng thử lại')
  })

  it('resolves with undefined on 204 empty bodies', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await expect(deleteConversation('abc')).resolves.toBeUndefined()
    expect(fetchMock.mock.calls[0][0]).toBe('/api/conversations/abc')
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
  })
})
