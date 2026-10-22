import { useState } from 'react'
import type { TurnDto } from '../types'
import ResultChart from './ResultChart'
import ResultTable from './ResultTable'

function SqlBlock({ sql }: { sql: string }) {
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    try {
      await navigator.clipboard?.writeText(sql)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // clipboard unavailable
    }
  }

  return (
    <div className="sql-block">
      <div className="sql-bar">
        <button type="button" className="link-btn" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
          {open ? 'Ẩn SQL' : 'Xem SQL'}
        </button>
        {open && (
          <button type="button" className="link-btn" onClick={copy}>
            {copied ? 'Đã chép' : 'Sao chép SQL'}
          </button>
        )}
      </div>
      {open && (
        <pre className="sql-code">
          <code>{sql}</code>
        </pre>
      )}
    </div>
  )
}

export default function TurnView({ turn }: { turn: TurnDto }) {
  const showRewritten = turn.rewrittenQuestion && turn.rewrittenQuestion.trim() !== turn.question.trim()
  const result = turn.result

  return (
    <section className="turn">
      <div className="bubble user">{turn.question}</div>
      <div className="answer">
        {showRewritten && <p className="rewritten">Hiểu là: {turn.rewrittenQuestion}</p>}
        {turn.sql && <SqlBlock sql={turn.sql} />}
        {turn.status === 'BLOCKED' && (
          <div className="notice warn" role="alert">
            Đã chặn: {turn.message}
          </div>
        )}
        {turn.status === 'ERROR' && (
          <div className="notice error" role="alert">
            {turn.message ?? 'Đã xảy ra lỗi'}
          </div>
        )}
        {turn.status === 'OK' &&
          result &&
          (result.rows.length === 0 ? (
            <p className="empty">Không có dữ liệu</p>
          ) : (
            <>
              <ResultChart chart={turn.chart} result={result} />
              <ResultTable result={result} />
              {result.truncated && <p className="note">Chỉ hiển thị 1000 dòng đầu</p>}
            </>
          ))}
      </div>
    </section>
  )
}
