### Task 6: ChartSuggester

**Files:**
- Create: `chart/ChartSuggester.java`, `chart/ChartSpec.java`, `chart/ChartType.java`
- Test: `chart/ChartSuggesterTest.java` (unit)

**Interfaces:**
- Consumes: `QueryResult` và `ColumnInfo` (Task 5).
- Produces:
  - `enum ChartType { LINE, BAR, PIE, KPI, TABLE }`
  - `record ChartSpec(ChartType type, String xColumn, String yColumn)`. Với `KPI` và `TABLE` thì `xColumn`, `yColumn` là null.
  - `ChartSuggester`: `@Component`, `ChartSpec suggest(QueryResult r)`.

Quy tắc (áp dụng theo thứ tự):
1. 0 dòng → `TABLE`.
2. Đúng 1 dòng, 1 cột số → `KPI`.
3. Đúng 2 cột, một cột date (hoặc cột text/number có tên chứa `month`, `year`, `quarter`, `date`, `thang`, `nam`, `quy`) và một cột số → `LINE`.
4. Đúng 2 cột, một text và một số:
   - 2–6 dòng, mọi giá trị số ≥ 0, và tên cột số chứa `share`, `percent`, `ty_le`, `pct` → `PIE`;
   - ngược lại, nếu ≤ 50 dòng → `BAR`.
5. Còn lại → `TABLE`.

- [ ] **Step 1: Viết test**, mỗi quy tắc một ca. Thêm hai ca: cột số toàn null → không ném lỗi; 120 dòng text+số → `TABLE`.
- [ ] **Step 2:** Chạy. Kỳ vọng: FAIL. Implement. Chạy lại. Kỳ vọng: PASS.
- [ ] **Step 3:** Commit: `feat(chart): add rule-based chart suggestion`.

---

