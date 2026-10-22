import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth'
import RegisterPage from './RegisterPage'

const registerMock = vi.hoisted(() => vi.fn())
vi.mock('../api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api')>()),
  register: registerMock,
}))

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <AuthProvider>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<p>Trang chủ</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('RegisterPage', () => {
  beforeEach(() => {
    localStorage.clear()
    registerMock.mockReset()
  })

  it('rejects invalid input without calling the API', async () => {
    renderRegister()
    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'a b')
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ký' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Tên đăng nhập')
    expect(registerMock).not.toHaveBeenCalled()
  })

  it('registers, signs in and navigates to /', async () => {
    registerMock.mockResolvedValue({ token: 'tok', username: 'alice' })
    renderRegister()
    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'alice')
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ký' }))
    expect(registerMock).toHaveBeenCalledWith('alice', 'secret123')
    expect(await screen.findByText('Trang chủ')).toBeInTheDocument()
    expect(localStorage.getItem('tts.token')).toBe('tok')
  })
})
