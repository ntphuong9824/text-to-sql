export type TurnStatus = 'OK' | 'BLOCKED' | 'ERROR'
export type ChartType = 'LINE' | 'BAR' | 'PIE' | 'KPI' | 'TABLE'

export interface AuthResponse {
  token: string
  username: string
}

export interface ErrorResponse {
  message: string
}

export interface ColumnInfo {
  name: string
  type: 'number' | 'text' | 'date' | 'boolean'
}

export interface QueryResult {
  columns: ColumnInfo[]
  rows: unknown[][]
  truncated: boolean
}

export interface ChartSpec {
  type: ChartType
  xColumn: string | null
  yColumn: string | null
}

export interface TurnDto {
  turnIndex: number
  question: string
  rewrittenQuestion: string | null
  sql: string | null
  status: TurnStatus
  message: string | null
  result: QueryResult | null
  chart: ChartSpec | null
  createdAt: string
}

export interface ChatResponse {
  conversationId: string
  title: string
  turn: TurnDto
}

export interface ConversationSummary {
  id: string
  title: string
  updatedAt: string
}

export interface ConversationDetail {
  id: string
  title: string
  turns: TurnDto[]
}
