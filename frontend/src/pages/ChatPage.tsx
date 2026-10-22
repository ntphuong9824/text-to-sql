import { useAuth } from '../auth'

// Placeholder: replaced by the real chat UI in Task 14.
export default function ChatPage() {
  const { username, signOut } = useAuth()
  return (
    <main>
      <h1>Text-to-SQL</h1>
      <p>Xin chào, {username}</p>
      <button type="button" onClick={signOut}>Đăng xuất</button>
    </main>
  )
}
