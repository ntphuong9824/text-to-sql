export function formatNumber(value: number): string {
  return value.toLocaleString('vi-VN', { maximumFractionDigits: 2 })
}

export function formatCell(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'number') return formatNumber(value)
  return String(value)
}
