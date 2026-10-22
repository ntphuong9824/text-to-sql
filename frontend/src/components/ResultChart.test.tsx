import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { QueryResult } from '../types'
import ResultChart from './ResultChart'

const result: QueryResult = {
  columns: [
    { name: 'month', type: 'text' },
    { name: 'total', type: 'number' },
  ],
  rows: [
    ['T1', 1234567],
    ['T2', null],
  ],
  truncated: false,
}

describe('ResultChart', () => {
  it('renders KPI with formatted number', () => {
    render(
      <ResultChart
        chart={{ type: 'KPI', xColumn: null, yColumn: 'total' }}
        result={{ ...result, rows: [['T1', 1234567]] }}
      />,
    )
    expect(screen.getByText((1234567).toLocaleString('vi-VN'))).toBeInTheDocument()
  })

  it('renders nothing for TABLE', () => {
    const { container } = render(<ResultChart chart={{ type: 'TABLE', xColumn: null, yColumn: null }} result={result} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders a labelled chart wrapper for BAR', () => {
    render(
      <ResultChart
        chart={{ type: 'BAR', xColumn: 'month', yColumn: 'total' }}
        result={result}
        size={{ width: 400, height: 250 }}
      />,
    )
    expect(screen.getByRole('img', { name: /total theo month/ })).toBeInTheDocument()
  })

  it('renders nothing for a PIE with no positive values', () => {
    const { container } = render(
      <ResultChart
        chart={{ type: 'PIE', xColumn: 'month', yColumn: 'total' }}
        result={{ ...result, rows: [['T1', 0], ['T2', -3]] }}
        size={{ width: 400, height: 250 }}
      />,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
