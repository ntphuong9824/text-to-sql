import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChatResponse, ConversationDetail, TurnDto } from '../types'
import ChatPage from './ChatPage'

const chatMock = vi.fn()
const getConversationMock = vi.fn()
const listMock = vi.fn()

vi.mock('../api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api')>()),
  chat: (...a: unknown[]) => chatMock(...a),
  getConversation: (...a: unknown[]) => getConversationMock(...a),
  listConversations: () => listMock(),
  deleteConversation: vi.fn(),
}))
vi.mock('../auth', () => ({ useAuth: () => ({ username: 'alice', signOut: vi.fn() }) }))

const turn = (question: string, extra: Partial<TurnDto> = {}): TurnDto => ({
  turnIndex: 0,
  question,
  rewrittenQuestion: null,
  sql: null,
  status: 'ERROR',
  message: 'ANSWER-' + question,
  result: null,
  chart: null,
  createdAt: '2026-01-01T00:00:00Z',
  ...extra,
})

function deferred<T>() {
  let resolve!: (v: T) => void
  const promise = new Promise<T>((r) => (resolve = r))
  return { promise, resolve }
}

function Probe() {
  return <div data-testid="path">{useLocation().pathname}</div>
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Probe />
      <Routes>
        <Route path="/" element={<ChatPage />} />
        <Route path="/c/:id" element={<ChatPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  chatMock.mockReset()
  getConversationMock.mockReset()
  listMock.mockReset()
  listMock.mockResolvedValue([
    { id: 'A', title: 'Conv A', updatedAt: '2026-01-01T00:00:00Z' },
    { id: 'B', title: 'Conv B', updatedAt: '2026-01-01T00:00:00Z' },
  ])
  getConversationMock.mockImplementation(async (id: string): Promise<ConversationDetail> => ({
    id,
    title: id,
    turns: [turn('old-' + id, { message: 'old answer ' + id })],
  }))
})

describe('ChatPage pending request isolation', () => {
  it('does not leak a pending answer into another conversation', async () => {
    const d = deferred<ChatResponse>()
    chatMock.mockReturnValue(d.promise)
    renderAt('/c/A')
    await screen.findByText('old answer A')
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'qA{Enter}')
    expect(await screen.findByText('Đang phân tích…')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: 'Conv B' }))
    await screen.findByText('old answer B')
    expect(screen.queryByText('Đang phân tích…')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Câu hỏi')).toBeEnabled()

    d.resolve({ conversationId: 'A', title: 'A', turn: turn('qA') })
    await waitFor(() => expect(listMock.mock.calls.length).toBeGreaterThan(1))
    expect(screen.queryByText('ANSWER-qA')).not.toBeInTheDocument()
    expect(screen.getByTestId('path')).toHaveTextContent('/c/B')
  })

  it('does not yank the user away when a first answer lands after they opened another conversation', async () => {
    const d = deferred<ChatResponse>()
    chatMock.mockReturnValue(d.promise)
    renderAt('/')
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'qNew{Enter}')
    await screen.findByText('Đang phân tích…')

    await userEvent.click(screen.getByRole('link', { name: 'Conv B' }))
    await screen.findByText('old answer B')

    d.resolve({ conversationId: 'NEW', title: 'New', turn: turn('qNew') })
    await waitFor(() => expect(listMock.mock.calls.length).toBeGreaterThan(1))
    expect(screen.getByTestId('path')).toHaveTextContent('/c/B')
    expect(screen.queryByText('ANSWER-qNew')).not.toBeInTheDocument()
  })

  it('navigates to the new conversation when still on /', async () => {
    chatMock.mockResolvedValue({ conversationId: 'NEW', title: 'New', turn: turn('qNew') })
    getConversationMock.mockImplementation(async (id: string) => ({
      id,
      title: id,
      turns: [turn('qNew')],
    }))
    renderAt('/')
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'qNew{Enter}')
    await waitFor(() => expect(screen.getByTestId('path')).toHaveTextContent('/c/NEW'))
    expect(await screen.findByText('ANSWER-qNew')).toBeInTheDocument()
  })

  it('ignores Enter while an IME composition is finishing (keyCode 229)', async () => {
    renderAt('/')
    const box = screen.getByLabelText('Câu hỏi')
    await userEvent.type(box, 'xin chào')
    fireEvent.keyDown(box, { key: 'Enter', keyCode: 229 })
    expect(chatMock).not.toHaveBeenCalled()
  })
})
