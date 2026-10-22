import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { ChartSpec, QueryResult } from '../types'
import { formatNumber } from './format'

interface Props {
  chart: ChartSpec | null
  result: QueryResult
  /** Fixed size, used by tests where ResponsiveContainer has no layout. */
  size?: { width: number; height: number }
}

interface Point {
  x: string
  y: number
}

const SERIES = [1, 2, 3, 4, 5, 6, 7, 8].map((n) => `var(--series-${n})`)

function toPoints(chart: ChartSpec, result: QueryResult): Point[] {
  const xi = result.columns.findIndex((c) => c.name === chart.xColumn)
  const yi = result.columns.findIndex((c) => c.name === chart.yColumn)
  if (xi < 0 || yi < 0) return []
  const points: Point[] = []
  for (const row of result.rows) {
    const raw = row[yi]
    if (raw === null || raw === undefined) continue
    const y = Number(raw)
    if (Number.isNaN(y)) continue
    points.push({ x: String(row[xi] ?? '—'), y })
  }
  return points
}

function pieData(points: Point[]): Point[] {
  if (points.length <= SERIES.length) return points
  const head = points.slice(0, SERIES.length - 1)
  const rest = points.slice(SERIES.length - 1).reduce((s, p) => s + p.y, 0)
  return [...head, { x: 'Khác', y: rest }]
}

export default function ResultChart({ chart, result, size }: Props) {
  if (!chart || chart.type === 'TABLE' || result.rows.length === 0) return null

  if (chart.type === 'KPI') {
    const yi = result.columns.findIndex((c) => c.name === chart.yColumn)
    const raw = result.rows[0]?.[yi >= 0 ? yi : 0]
    const num = raw === null || raw === undefined ? NaN : Number(raw)
    const text = Number.isNaN(num) ? '—' : formatNumber(num)
    return (
      <div className="kpi" role="img" aria-label={`${chart.yColumn ?? 'Giá trị'}: ${text}`}>
        <div className="kpi-value">{text}</div>
        {chart.yColumn && <div className="kpi-label">{chart.yColumn}</div>}
      </div>
    )
  }

  const points = toPoints(chart, result)
  if (points.length === 0) return null
  const label = `Biểu đồ ${chart.yColumn ?? ''} theo ${chart.xColumn ?? ''}`.trim()
  const axisProps = { tick: { fill: 'var(--text-secondary)', fontSize: 12 }, stroke: 'var(--grid)' }
  const tip = (v: unknown) => (typeof v === 'number' ? formatNumber(v) : String(v))
  const tooltipStyle = {
    background: 'var(--surface-1)',
    border: '1px solid var(--grid)',
    color: 'var(--text-primary)',
  }

  let body
  if (chart.type === 'LINE') {
    body = (
      <LineChart data={points} width={size?.width} height={size?.height}>
        <CartesianGrid stroke="var(--grid)" vertical={false} />
        <XAxis dataKey="x" {...axisProps} />
        <YAxis {...axisProps} tickFormatter={tip} width={70} />
        <Tooltip formatter={tip} contentStyle={tooltipStyle} />
        <Line
          type="monotone"
          dataKey="y"
          name={chart.yColumn ?? 'Giá trị'}
          stroke="var(--series-1)"
          strokeWidth={2}
          dot={{ r: 4, stroke: 'var(--surface-1)', strokeWidth: 2 }}
        />
      </LineChart>
    )
  } else if (chart.type === 'BAR') {
    body = (
      <BarChart data={points} width={size?.width} height={size?.height}>
        <CartesianGrid stroke="var(--grid)" vertical={false} />
        <XAxis dataKey="x" {...axisProps} />
        <YAxis {...axisProps} tickFormatter={tip} width={70} />
        <Tooltip formatter={tip} contentStyle={tooltipStyle} cursor={{ fill: 'var(--grid)', opacity: 0.4 }} />
        <Bar dataKey="y" name={chart.yColumn ?? 'Giá trị'} fill="var(--series-1)" radius={[4, 4, 0, 0]} maxBarSize={48} />
      </BarChart>
    )
  } else {
    const data = pieData(points.filter((p) => p.y > 0))
    body = (
      <PieChart width={size?.width} height={size?.height}>
        <Tooltip formatter={tip} contentStyle={tooltipStyle} />
        <Legend wrapperStyle={{ color: 'var(--text-secondary)' }} />
        <Pie data={data} dataKey="y" nameKey="x" innerRadius="45%" outerRadius="80%" stroke="var(--surface-1)" strokeWidth={2}>
          {data.map((_, i) => (
            <Cell key={i} fill={SERIES[i % SERIES.length]} />
          ))}
        </Pie>
      </PieChart>
    )
  }

  return (
    <div className="chart" role="img" aria-label={label}>
      {size ? body : <ResponsiveContainer width="100%" height={300}>{body}</ResponsiveContainer>}
    </div>
  )
}
