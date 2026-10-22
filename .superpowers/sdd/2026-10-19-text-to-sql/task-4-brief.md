### Task 4: SqlGuard

**Files:**
- Create: `guardrail/SqlGuard.java`, `guardrail/GuardResult.java`
- Test: `guardrail/SqlGuardTest.java` (unit, không cần DB)

**Interfaces:**
- Produces:
  - `record GuardResult(boolean allowed, String safeSql, String reason)`, với hai factory `allowed(String safeSql)` và `blocked(String reason)`. `reason` viết bằng tiếng Việt.
  - `SqlGuard`: `@Component`, `GuardResult check(String sql)`, hằng `MAX_ROWS = 1000`.

Quy tắc, theo thứ tự:
1. Rỗng → blocked.
2. Parse bằng `CCJSqlParserUtil.parseStatements`. Lỗi parse → blocked ("SQL không hợp lệ").
3. Số câu lệnh khác 1 → blocked.
4. Câu lệnh không phải `Select` (kể cả `WITH ... SELECT`) → blocked.
5. Duyệt mọi bảng bằng `TablesNamesFinder` (tên CTE không tính là bảng). Bảng nào không có schema hoặc schema khác `dw` → blocked, kèm tên bảng.
6. Có lời gọi hàm trong danh sách cấm (không phân biệt hoa thường): `pg_sleep`, `pg_read_file`, `pg_read_binary_file`, `pg_ls_dir`, `lo_import`, `lo_export`, `dblink`, `current_setting`, `set_config`, `pg_terminate_backend`, `pg_cancel_backend` → blocked. Duyệt bằng `ExpressionVisitorAdapter` hoặc deparser visitor.
7. `SELECT ... INTO` hoặc `FOR UPDATE` → blocked.
8. LIMIT ở ngoài cùng: không có thì thêm `LIMIT 1000`, lớn hơn 1000 thì hạ xuống 1000. `safeSql` là câu đã deparse.

*(Bảng không có schema bị chặn, vì thế prompt sẽ yêu cầu LLM luôn ghi `dw.`. Đây là quyết định khi lập plan: chặt hơn nhưng dễ kiểm soát.)*

- [ ] **Step 1: Viết test** `SqlGuardTest`. Dùng `@ParameterizedTest` cho các ca bị chặn:
  - `"DROP TABLE dw.fact_sales"`, `"DELETE FROM dw.fact_sales"`, `"UPDATE dw.dim_track SET unit_price=0"`, `"INSERT INTO dw.dim_date VALUES (1)"`, `"TRUNCATE dw.fact_sales"`;
  - `"SELECT 1; DROP TABLE dw.fact_sales"`, `"SELECT * FROM dw.fact_sales; SELECT 1"`;
  - `"SELECT * FROM chinook.invoice"`, `"SELECT * FROM app.users"`, `"SELECT * FROM pg_catalog.pg_user"`, `"SELECT * FROM information_schema.tables"`, `"SELECT * FROM fact_sales"`;
  - `"SELECT pg_sleep(100)"`, `"SELECT PG_READ_FILE('/etc/passwd')"`;
  - `"WITH x AS (DELETE FROM dw.fact_sales RETURNING *) SELECT * FROM x"`;
  - `"SELECT * INTO dw.copy FROM dw.fact_sales"`;
  - `"SELECT * FROM dw.fact_sales WHERE sales_key IN (SELECT id FROM app.users)"`;
  - `"/* hi */ DROP TABLE dw.fact_sales"`, `""`, `"not sql at all"`.

  Các ca được phép:
  - `"SELECT count(*) FROM dw.fact_sales"`: `safeSql` chứa `LIMIT 1000`;
  - `"SELECT * FROM dw.fact_sales LIMIT 5"`: giữ `LIMIT 5`;
  - `"... LIMIT 50000"`: thành `LIMIT 1000`;
  - CTE `"WITH t AS (SELECT * FROM dw.fact_sales) SELECT count(*) FROM t"`: được phép;
  - `"SELECT 'DROP TABLE x' AS s FROM dw.dim_date"`: được phép (chuỗi không phải lệnh).
- [ ] **Step 2:** Chạy `./mvnw test -Dtest=SqlGuardTest`. Kỳ vọng: FAIL (chưa có lớp).
- [ ] **Step 3:** Implement theo các quy tắc trên.
- [ ] **Step 4:** Chạy lại. Kỳ vọng: tất cả PASS.
- [ ] **Step 5:** Commit: `feat(guardrail): add AST-based SELECT-only SQL guard`.

---

