import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, chat, deleteConversation, getConversation, listConversations } from '../api'
import { useAuth } from '../auth'
import Sidebar from '../components/Sidebar'
import SuggestedQuestions from '../components/SuggestedQuestions'
import TurnView from '../components/TurnView'
import type { ConversationSummary, TurnDto } from '../types'

interface ConversationViewProps {
  id: string | undefined
  onChanged: () => void
}

/** Per-conversation state. Remounted (via key) for every conversation, so a late response can never leak into another one. */
function ConversationView({ id, onChanged }: ConversationViewProps) {
  const navigate = useNavigate()
  const [turns, setTurns] = useState<TurnDto[]>([])
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [input, setInput] = useState('')
  const mounted = useRef(true)
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    if (!id) return
    let cancelled = false
    getConversation(id)
      .then((c) => {
        if (!cancelled) setTurns(c.turns)
      })
      .catch((e) => {
        if (cancelled) return
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
      if (mounted.current) {
        setTurns((t) => [...t, res.turn])
        if (!id) navigate('/c/' + res.conversationId, { replace: true })
      }
    } catch (e) {
      if (mounted.current) {
        setError(e instanceof Error ? e.message : 'Đã xảy ra lỗi')
        setInput(question)
      }
    } finally {
      if (mounted.current) setPending(null)
      onChanged()
    }
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // keyCode 229: Safari fires keydown right after compositionend with isComposing === false
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing && e.keyCode !== 229) {
      e.preventDefault()
      void send(input)
    }
  }

  const empty = turns.length === 0 && pending === null && !notFound && !error

  return (
    <>
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
    </>
  )
}

export default function ChatPage() {
  const { username, signOut } = useAuth()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [menuOpen, setMenuOpen] = useState(false)
  const [listError, setListError] = useState<string | null>(null)

  const refreshList = useCallback(async () => {
    try {
      setConversations(await listConversations())
    } catch {
      // sidebar failure is non-fatal
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    listConversations()
      .then((list) => {
        if (!cancelled) setConversations(list)
      })
      .catch(() => {
        // sidebar failure is non-fatal
      })
    return () => {
      cancelled = true
    }
  }, [])

  const onDelete = async (c: ConversationSummary) => {
    if (!window.confirm(`Xóa hội thoại "${c.title}"?`)) return
    try {
      await deleteConversation(c.id)
      setListError(null)
    } catch (e) {
      setListError(e instanceof Error ? e.message : 'Đã xảy ra lỗi')
      return
    }
    if (c.id === id) navigate('/')
    void refreshList()
  }

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
        {listError && (
          <div className="notice error" role="alert">
            {listError}
          </div>
        )}
        <ConversationView key={id ?? 'new'} id={id} onChanged={() => void refreshList()} />
      </main>
    </div>
  )
}
