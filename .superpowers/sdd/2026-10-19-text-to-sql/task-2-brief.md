### Task 2: Star schema `dw` và quyền truy cập

**Files:**
- Create: `db/init/02_build_dw.sql`
- Test: `src/test/java/com/fsoft/training/warehouse/WarehouseBuildTest.java`, `src/test/java/com/fsoft/training/warehouse/AnalystRolePermissionTest.java`

**Interfaces:**
- Produces (các bảng mà semantic layer, few-shot và eval sẽ dựa vào):
  - `dw.dim_date(date_key int PK yyyymmdd, full_date date, day int, month int, month_name text, quarter int, year int)`: mỗi ngày trong khoảng từ ngày invoice nhỏ nhất đến lớn nhất.
  - `dw.dim_customer(customer_key int PK, full_name, company, city, state, country, region)`.
  - `dw.dim_employee(employee_key int PK, full_name, title, city, country)`.
  - `dw.dim_track(track_key int PK, track_name, album_title, artist_name, genre_name, media_type_name, composer, milliseconds int, unit_price numeric(10,2))`.
  - `dw.fact_sales(sales_key int PK = invoice_line_id, invoice_id int, date_key int FK, customer_key FK, employee_key FK nullable (support rep của khách), track_key FK, quantity int, unit_price numeric(10,2), revenue numeric(10,2))`.
  - Giá trị `region`: `North America` (USA, Canada), `South America` (Brazil, Argentina, Chile), `Asia-Pacific` (Australia, India). Mọi quốc gia còn lại trong Chinook là `Europe`. Ánh xạ đặt trong bảng `dw.country_region(country PK, region)` để dễ xem lại.

- [ ] **Step 1: Viết test** `WarehouseBuildTest`:
  - `fact_sales` có đúng 2240 dòng.
  - `sum(revenue)` bằng `2328.60`.
  - Không customer nào có `region` null.
  - `count(distinct region) = 4`.
  - Mọi `fact_sales.date_key` đều có trong `dim_date`.
  - Chạy lại script lần hai (đọc file và thực thi bằng `superuserJdbc`) không lỗi, và số dòng vẫn là 2240.
- [ ] **Step 2: Viết test** `AnalystRolePermissionTest`, dùng `analystJdbcTemplate`:
  - `select count(*) from dw.fact_sales` thành công.
  - `select * from chinook.invoice` ném `DataAccessException` (permission denied).
  - `select * from app.users` ném exception (bảng chưa tồn tại thì lỗi cũng là permission denied trên schema, vẫn tính là đạt).
  - `insert into dw.dim_date ...` ném exception.
  - `show statement_timeout` trả `10s`.
  - `select pg_sleep(11)` ném exception do timeout.
- [ ] **Step 3:** Chạy cả hai test. Kỳ vọng: FAIL vì chưa có `dw`.
- [ ] **Step 4:** Viết `02_build_dw.sql`.
  - Có thể chạy lại: `DROP SCHEMA IF EXISTS dw CASCADE; CREATE SCHEMA dw;`. Các bảng được dựng bằng `CREATE TABLE ... AS SELECT` từ `chinook`, sau đó thêm PK/FK.
  - Cấp quyền: `GRANT USAGE ON SCHEMA dw TO analyst_ro; GRANT SELECT ON ALL TABLES IN SCHEMA dw TO analyst_ro;`.
  - Thêm `COMMENT ON TABLE/COLUMN` cho từng bảng (phần mô tả này sẽ được đối chiếu với semantic layer).
- [ ] **Step 5:** Chạy lại test. Kỳ vọng: PASS.
- [ ] **Step 6:** Commit: `feat(dw): build star schema warehouse and read-only analyst role`.

---

