import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '../api'
import { useAuth } from '../auth'

function validateRegistration(username: string, password: string): string | null {
  if (!username.trim()) return 'Tên đăng nhập không được để trống'
  if (username.length < 3 || username.length > 50) return 'Tên đăng nhập phải từ 3 đến 50 ký tự'
  if (!/^[a-zA-Z0-9_.]+$/.test(username)) {
    return 'Tên đăng nhập chỉ gồm chữ, số, dấu gạch dưới và dấu chấm'
  }
  if (!password.trim()) return 'Mật khẩu không được để trống'
  if (password.length < 8 || password.length > 100) return 'Mật khẩu phải từ 8 đến 100 ký tự'
  if (new TextEncoder().encode(password).length > 72) return 'Mật khẩu quá dài (tối đa 72 byte)'
  return null
}

export default function RegisterPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const invalid = validateRegistration(username, password)
    if (invalid) {
      setError(invalid)
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      const auth = await register(username, password)
      signIn(auth)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đã xảy ra lỗi, vui lòng thử lại')
      setSubmitting(false)
    }
  }

  return (
    <main>
      <h1>Đăng ký</h1>
      <form onSubmit={onSubmit} noValidate>
        <div>
          <label htmlFor="username">Tên đăng nhập</label>
          <input id="username" name="username" autoComplete="username" value={username}
            onChange={(e) => setUsername(e.target.value)} />
        </div>
        <div>
          <label htmlFor="password">Mật khẩu</label>
          <input id="password" name="password" type="password" autoComplete="new-password"
            value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Đang đăng ký...' : 'Đăng ký'}
        </button>
      </form>
      <p>Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
    </main>
  )
}
