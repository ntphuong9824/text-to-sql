const SUGGESTIONS = [
  'Doanh thu tháng 9 theo khu vực',
  'Top 5 sản phẩm bán chạy nhất',
  'Số đơn hàng theo từng tháng',
  'Tổng doanh thu năm 2025',
]

export default function SuggestedQuestions({
  onPick,
  disabled,
}: {
  onPick: (q: string) => void
  disabled?: boolean
}) {
  return (
    <div className="suggestions">
      <p>Thử hỏi một câu như:</p>
      <div className="suggestion-list">
        {SUGGESTIONS.map((q) => (
          <button key={q} type="button" className="chip" disabled={disabled} onClick={() => onPick(q)}>
            {q}
          </button>
        ))}
      </div>
    </div>
  )
}
