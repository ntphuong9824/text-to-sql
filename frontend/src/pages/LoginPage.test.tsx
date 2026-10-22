import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { AuthProvider } from '../auth'
import LoginPage from './LoginPage'

const loginMock = vi.hoisted(() => vi.fn())
vi.mock('../api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api')>()),
  login: loginMock,
}))

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Trang chủ</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear()
    loginMock.mockReset()
  })

  it('submits credentials, stores the session and navigates to /', async () => {
    loginMock.mockResolvedValue({ token: 'tok', username: 'alice' })
    renderLogin()
    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'alice')
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(loginMock).toHaveBeenCalledWith('alice', 'secret123')
    expect(await screen.findByText('Trang chủ')).toBeInTheDocument()
    expect(localStorage.getItem('tts.token')).toBe('tok')
    expect(localStorage.getItem('tts.username')).toBe('alice')
  })

  it('shows the error message and re-enables the button on failure', async () => {
    loginMock.mockRejectedValue(new ApiError(401, 'Sai tên đăng nhập hoặc mật khẩu'))
    renderLogin()
    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'alice')
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'wrongpass')
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Sai tên đăng nhập hoặc mật khẩu')
    expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeEnabled()
  })
})
