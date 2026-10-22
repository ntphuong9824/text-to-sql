import { Link } from 'react-router-dom'
import type { ConversationSummary } from '../types'

interface Props {
  conversations: ConversationSummary[]
  activeId: string | undefined
  username: string | null
  open: boolean
  onNavigate: () => void
  onDelete: (c: ConversationSummary) => void
  onSignOut: () => void
}

export default function Sidebar({ conversations, activeId, username, open, onNavigate, onDelete, onSignOut }: Props) {
  return (
    <aside className={`sidebar${open ? ' open' : ''}`} aria-label="Lịch sử hội thoại">
      <Link to="/" className="new-chat" onClick={onNavigate}>
        Hội thoại mới
      </Link>
      <ul className="conv-list">
        {conversations.map((c) => (
          <li key={c.id} className={c.id === activeId ? 'active' : undefined}>
            <Link to={`/c/${c.id}`} aria-current={c.id === activeId ? 'page' : undefined} onClick={onNavigate}>
              {c.title}
            </Link>
            <button type="button" className="icon-btn" aria-label={`Xóa hội thoại ${c.title}`} onClick={() => onDelete(c)}>
              ×
            </button>
          </li>
        ))}
      </ul>
      <div className="sidebar-foot">
        <span className="user">{username}</span>
        <button type="button" className="link-btn" onClick={onSignOut}>
          Đăng xuất
        </button>
      </div>
    </aside>
  )
}
