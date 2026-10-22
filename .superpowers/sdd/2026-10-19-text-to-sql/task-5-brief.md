### Task 5: SqlExecutor

**Files:**
- Create: `warehouse/SqlExecutor.java`, `warehouse/QueryResult.java`, `warehouse/ColumnInfo.java`, `warehouse/SqlExecutionException.java`
- Test: `warehouse/SqlExecutorTest.java` (integration)

**Interfaces:**
- Consumes: `analystJdbcTemplate` (Task 1). Đầu vào phải là `GuardResult.safeSql` (Task 4).
- Produces:
  - `record ColumnInfo(String name, String type)`. `type` là một trong `"number" | "text" | "date" | "boolean"`, ánh xạ từ `java.sql.Types`.
  - `record QueryResult(List<ColumnInfo> columns, List<List<Object>> rows, boolean truncated)`. Giá trị trong `rows` đã chuẩn hóa sang dạng JSON-friendly: số → `BigDecimal`/`Long`, date/timestamp → chuỗi ISO, null giữ nguyên null.
  - `class SqlExecutionException extends RuntimeException`: message là lỗi gốc từ Postgres (dùng cho bước tự sửa lỗi).
  - `SqlExecutor`: `@Component`, `QueryResult execute(String safeSql)`. Dùng `setMaxRows(1001)` để biết có bị cắt hay không: có hơn 1000 dòng thì `truncated=true` và chỉ giữ 1000. Query timeout 10s.

- [ ] **Step 1: Viết test**:
  - `"select region, sum(revenue) as revenue from dw.fact_sales f join dw.dim_customer c on c.customer_key=f.customer_key group by region"`: có 4 dòng, cột 2 có type `number`.
  - Query trả 0 dòng (`where 1=0`): `rows` rỗng, `columns` vẫn có đủ.
  - Một cột `date` trả về chuỗi `yyyy-MM-dd`.
  - Giá trị null giữ là null.
  - `select * from dw.fact_sales` cùng LIMIT 1000 được truyền vào: `truncated` = false và có đúng 1000 dòng. Gọi trực tiếp không kèm LIMIT: `truncated` = true.
  - Cột không tồn tại → ném `SqlExecutionException`, message chứa `column`.
  - `QueryResult` serialize bằng `JsonMapper` không lỗi.
- [ ] **Step 2:** Chạy. Kỳ vọng: FAIL. Implement. Chạy lại. Kỳ vọng: PASS.
- [ ] **Step 3: Thêm lớp kiểm tra bằng planner** *(bổ sung khi thực thi plan. Lý do: duyệt AST bằng JSqlParser không bao giờ đảm bảo phủ đủ mọi nhánh. Postgres mới là bên phân giải tên, schema, phạm vi CTE và view một cách chính xác)*.
  - Tạo `warehouse/PlanInspector.java` (`@Component`), có hàm `Optional<String> violation(String safeSql)`.
  - Cách làm: dùng `analystJdbcTemplate` chạy `EXPLAIN (VERBOSE, FORMAT JSON) <safeSql>`, rồi duyệt đệ quy cây plan JSON:
    - Node nào có `"Relation Name"` thì `"Schema"` phải là `dw`. Nếu không, trả lý do tiếng Việt kèm tên `schema.bảng`. View như `pg_user` sẽ hiện ra dưới dạng các bảng `pg_catalog` bên dưới nên cũng bị bắt.
    - Node nào có `"Function Name"` (Function Scan) thì tên hàm phải không thỏa `SqlGuard.isBlockedFunction`.
    - Các trường text (`Output`, `Filter`, `Join Filter`, `Hash Cond`, `Index Cond`, `Sort Key`, `Group Key`, `One-Time Filter`...): quét các identifier đứng ngay trước `(`, rồi kiểm tra từng tên bằng `isBlockedFunction`.
    - EXPLAIN mà lỗi (ví dụ thiếu quyền) thì trả `Optional.empty()`, để lỗi thật xuất hiện lúc execute và đi vào vòng tự sửa lỗi.
  - Yêu cầu `isBlockedFunction(String)` của `SqlGuard` là `public static`.
  - `SqlExecutor.execute` gọi `PlanInspector.violation` trước khi chạy. Có vi phạm thì ném `SqlPolicyViolationException(reason)`. Lớp này **không** kế thừa `SqlExecutionException`: pipeline sẽ coi nó là BLOCKED và không gửi lại cho LLM sửa.
  - Test `PlanInspectorTest` (integration):
    - `SELECT * FROM pg_user` (truyền thẳng vào, bỏ qua `SqlGuard`) → có vi phạm, lý do chứa `pg_catalog`.
    - `WITH "PG_USER" AS (SELECT 1) SELECT * FROM pg_user` → có vi phạm.
    - `SELECT * FROM (dw.dim_date d CROSS JOIN pg_user)` → có vi phạm.
    - `SELECT 1 FROM dw.dim_date WHERE pg_sleep(0) IS NULL` → có vi phạm (bắt qua trường Filter).
    - `SELECT region, sum(revenue) FROM dw.fact_sales f JOIN dw.dim_customer c USING (customer_key) GROUP BY region` → không vi phạm.
    - Câu có CTE và window function trên `dw` → không vi phạm.
    - `SqlExecutor.execute("SELECT * FROM pg_user")` → ném `SqlPolicyViolationException`.
- [ ] **Step 4:** Commit: `feat(warehouse): add read-only SQL executor with planner-based policy check`.

---

