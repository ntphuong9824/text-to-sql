/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { TOKEN_KEY, USERNAME_KEY, readStored, setUnauthorizedHandler, writeStored } from './api'
import type { AuthResponse } from './types'

interface AuthContextValue {
  username: string | null
  token: string | null
  signIn: (auth: AuthResponse) => void
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [token, setToken] = useState<string | null>(() => readStored(TOKEN_KEY))
  const [username, setUsername] = useState<string | null>(() => readStored(USERNAME_KEY))

  const signIn = useCallback((auth: AuthResponse) => {
    writeStored(TOKEN_KEY, auth.token)
    writeStored(USERNAME_KEY, auth.username)
    setToken(auth.token)
    setUsername(auth.username)
  }, [])

  const signOut = useCallback(() => {
    writeStored(TOKEN_KEY, null)
    writeStored(USERNAME_KEY, null)
    setToken(null)
    setUsername(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      signOut()
      navigate('/login', { replace: true })
    })
    return () => setUnauthorizedHandler(null)
  }, [signOut, navigate])

  const value = useMemo(() => ({ username, token, signIn, signOut }), [username, token, signIn, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}
