import type { QueryResult } from '../types'
import { formatCell } from './format'

export default function ResultTable({ result }: { result: QueryResult }) {
  return (
    <div className="table-wrap">
      <table className="result-table">
        <thead>
          <tr>
            {result.columns.map((c) => (
              <th key={c.name} scope="col" className={c.type === 'number' ? 'num' : undefined}>
                {c.name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, i) => (
            <tr key={i}>
              {result.columns.map((c, j) => (
                <td key={c.name + j} className={c.type === 'number' ? 'num' : undefined}>
                  {formatCell(row[j])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
