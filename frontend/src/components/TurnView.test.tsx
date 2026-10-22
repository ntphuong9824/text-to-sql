import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { TurnDto } from '../types'
import TurnView from './TurnView'

const base: TurnDto = {
  turnIndex: 0,
  question: 'Doanh thu?',
  rewrittenQuestion: null,
  sql: 'SELECT 1',
  status: 'OK',
  message: null,
  result: {
    columns: [
      { name: 'region', type: 'text' },
      { name: 'total', type: 'number' },
    ],
    rows: [
      ['Bắc', 1234.5],
      ['Nam', null],
    ],
    truncated: false,
  },
  chart: { type: 'TABLE', xColumn: null, yColumn: null },
  createdAt: '2026-01-01T00:00:00Z',
}

describe('TurnView', () => {
  it('shows blocked notice and no table', () => {
    render(<TurnView turn={{ ...base, status: 'BLOCKED', message: 'Chỉ cho phép SELECT', result: null, chart: null }} />)
    expect(screen.getByText(/Đã chặn: Chỉ cho phép SELECT/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('shows empty message for zero rows', () => {
    render(<TurnView turn={{ ...base, result: { ...base.result!, rows: [] } }} />)
    expect(screen.getByText('Không có dữ liệu')).toBeInTheDocument()
  })

  it('hides rewritten question when same as original', () => {
    render(<TurnView turn={{ ...base, rewrittenQuestion: 'Doanh thu?' }} />)
    expect(screen.queryByText(/Hiểu là/)).not.toBeInTheDocument()
  })

  it('shows rewritten question when different', () => {
    render(<TurnView turn={{ ...base, rewrittenQuestion: 'Doanh thu theo vùng?' }} />)
    expect(screen.getByText(/Hiểu là: Doanh thu theo vùng\?/)).toBeInTheDocument()
  })

  it('hides SQL by default and reveals on click', async () => {
    render(<TurnView turn={base} />)
    expect(screen.queryByText('SELECT 1')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Xem SQL' }))
    expect(screen.getByText('SELECT 1')).toBeInTheDocument()
  })

  it('renders null as dash and formats numbers', () => {
    render(<TurnView turn={base} />)
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText((1234.5).toLocaleString('vi-VN', { maximumFractionDigits: 2 }))).toBeInTheDocument()
  })

  it('notes truncation', () => {
    render(<TurnView turn={{ ...base, result: { ...base.result!, truncated: true } }} />)
    expect(screen.getByText('Chỉ hiển thị 1000 dòng đầu')).toBeInTheDocument()
  })
})
