### Task 3: Semantic layer

**Files:**
- Create: `src/main/resources/semantic/semantic-layer.yaml`, `warehouse/SemanticLayer.java`, `warehouse/SemanticLayerLoader.java`, `warehouse/SchemaPromptRenderer.java`
- Test: `warehouse/SemanticLayerConsistencyTest.java` (integration), `warehouse/SchemaPromptRendererTest.java` (unit)

**Interfaces:**
- Produces:
  - `record SemanticLayer(List<Table> tables, List<Metric> metrics, List<Rule> rules)`, trong đó:
    - `record Table(String name, String description, List<Column> columns)`
    - `record Column(String name, String type, String description, List<String> synonyms, List<String> sampleValues)`
    - `record Metric(String name, String description, String sqlExpression, List<String> synonyms)`
    - `record Rule(String description)`
  - `SemanticLayerLoader`: `@Component`, `SemanticLayer load()`. Đọc classpath `semantic/semantic-layer.yaml` bằng Jackson YAML, cache sau lần đọc đầu.
  - `SchemaPromptRenderer`: `@Component`, `String render()`. Trả text gọn: mỗi bảng ghi `dw.<table>(col type -- mô tả; đồng nghĩa: ...; ví dụ: ...)`, sau đó là phần Metrics và Rules.

- [ ] **Step 1:** Viết YAML cho đủ 6 bảng `dw`. Phải có:
  - metric `revenue` = `SUM(f.revenue)`, đồng nghĩa "doanh thu", "doanh số", "sales";
  - metric `order_count` = `COUNT(DISTINCT f.invoice_id)`, đồng nghĩa "số đơn", "số hóa đơn";
  - metric `customer_count`;
  - đồng nghĩa cho region ("khu vực", "vùng", "Bắc Mỹ" = North America, "Châu Âu" = Europe, "Nam Mỹ", "Châu Á - Thái Bình Dương");
  - rule: "Câu hỏi không nêu năm thì gộp mọi năm";
  - rule: "Tháng/quý/năm lấy từ dw.dim_date qua fact_sales.date_key";
  - rule: "Chỉ dùng schema dw".
- [ ] **Step 2: Viết test** `SemanticLayerConsistencyTest`: với mỗi bảng và cột trong YAML, kiểm tra tồn tại trong `information_schema.columns` của schema `dw`. Ngược lại, mọi cột của `dw` đều phải có mô tả trong YAML (để không có cột nào LLM không biết).
- [ ] **Step 3: Viết test** `SchemaPromptRendererTest`: output chứa `dw.fact_sales`, `revenue`, `SUM(f.revenue)`, `doanh thu`, `North America`, và rule gộp năm.
- [ ] **Step 4:** Chạy test. Kỳ vọng: FAIL. Implement. Chạy lại. Kỳ vọng: PASS.
- [ ] **Step 5:** Commit: `feat(semantic): add semantic layer and schema prompt renderer`.

---

