import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, chat, deleteConversation, getConversation, listConversations } from '../api'
import { useAuth } from '../auth'
import Sidebar from '../components/Sidebar'
import SuggestedQuestions from '../components/SuggestedQuestions'
import TurnView from '../components/TurnView'
import type { ConversationSummary, TurnDto } from '../types'

export default function ChatPage() {
  const { username, signOut } = useAuth()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [turns, setTurns] = useState<TurnDto[]>([])
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [input, setInput] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)
  const skipLoadFor = useRef<string | null>(null)
  const endRef = useRef<HTMLDivElement>(null)

  const refreshList = useCallback(async () => {
    try {
      setConversations(await listConversations())
    } catch {
      // sidebar failure is non-fatal
    }
  }, [])

  useEffect(() => {
    void refreshList()
  }, [refreshList])

  useEffect(() => {
    setError(null)
    setNotFound(false)
    if (!id) {
      setTurns([])
      return
    }
    if (skipLoadFor.current === id) {
      skipLoadFor.current = null
      return
    }
    let cancelled = false
    getConversation(id)
      .then((c) => {
        if (!cancelled) setTurns(c.turns)
      })
      .catch((e) => {
        if (cancelled) return
        setTurns([])
        if (e instanceof ApiError && e.status === 404) setNotFound(true)
        else setError(e instanceof Error ? e.message : 'Đã xảy ra lỗi')
      })
    return () => {
      cancelled = true
    }
  }, [id])

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' })
  }, [turns, pending, error])

  const send = async (text: string) => {
    const question = text.trim()
    if (!question || pending !== null) return
    setPending(question)
    setError(null)
    setInput('')
    try {
      const res = await chat(question, id)
      setTurns((t) => [...t, res.turn])
      if (!id) {
        skipLoadFor.current = res.conversationId
        navigate('/c/' + res.conversationId, { replace: true })
      }
      void refreshList()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Đã xảy ra lỗi')
      setInput(question)
    } finally {
      setPending(null)
    }
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      void send(input)
    }
  }

  const onDelete = async (c: ConversationSummary) => {
    if (!window.confirm(`Xóa hội thoại "${c.title}"?`)) return
    try {
      await deleteConversation(c.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Đã xảy ra lỗi')
      return
    }
    if (c.id === id) navigate('/')
    void refreshList()
  }

  const empty = turns.length === 0 && pending === null && !notFound

  return (
    <div className="app-shell">
      <Sidebar
        conversations={conversations}
        activeId={id}
        username={username}
        open={menuOpen}
        onNavigate={() => setMenuOpen(false)}
        onDelete={onDelete}
        onSignOut={signOut}
      />
      <main className="chat">
        <header className="chat-head">
          <button
            type="button"
            className="menu-btn"
            aria-label="Mở menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((o) => !o)}
          >
            ☰
          </button>
          <h1>Text-to-SQL</h1>
        </header>
        <div className="messages">
          {notFound ? (
            <div className="notice error">
              Không tìm thấy hội thoại. <Link to="/">Quay lại trang chính</Link>
            </div>
          ) : (
            <>
              {empty && <SuggestedQuestions onPick={(q) => void send(q)} disabled={pending !== null} />}
              {turns.map((t) => (
                <TurnView key={t.turnIndex} turn={t} />
              ))}
              {pending !== null && (
                <section className="turn">
                  <div className="bubble user">{pending}</div>
                  <p className="note answer" role="status">
                    Đang phân tích…
                  </p>
                </section>
              )}
              {error && (
                <div className="notice error" role="alert">
                  {error}
                </div>
              )}
            </>
          )}
          <div ref={endRef} />
        </div>
        {!notFound && (
          <form
            className="composer"
            onSubmit={(e) => {
              e.preventDefault()
              void send(input)
            }}
          >
            <label htmlFor="question" className="sr-only">
              Câu hỏi
            </label>
            <textarea
              id="question"
              rows={2}
              value={input}
              disabled={pending !== null}
              placeholder="Đặt câu hỏi về dữ liệu… (Enter để gửi, Shift+Enter xuống dòng)"
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <button type="submit" disabled={pending !== null || !input.trim()}>
              Gửi
            </button>
          </form>
        )}
      </main>
    </div>
  )
}
