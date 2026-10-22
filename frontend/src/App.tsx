import { Route, Routes } from 'react-router-dom'
import { AuthProvider, RequireAuth } from './auth'
import ChatPage from './pages/ChatPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<RequireAuth><ChatPage /></RequireAuth>} />
        <Route path="/c/:id" element={<RequireAuth><ChatPage /></RequireAuth>} />
      </Routes>
    </AuthProvider>
  )
}
