### Task 1: Khởi tạo repo, build và Postgres với Chinook gốc

**Files:**
- Modify: `pom.xml`, `compose.yaml`, `.gitignore`, `.gitattributes`
- Create: `.env.example`, `db/init/00_roles.sql`, `db/init/01_chinook.sql`, `src/main/resources/application.yml` (xóa `application.properties`)
- Create: `src/main/java/com/fsoft/training/config/DataSourceConfig.java`
- Test: `src/test/java/com/fsoft/training/support/PostgresIntegrationTest.java`, `src/test/java/com/fsoft/training/warehouse/ChinookLoadTest.java`; xóa `TrainingApplicationTests.java` (thay bằng test có container)

**Interfaces:**
- Produces:
  - `DataSourceConfig`: bean `@Primary DataSource appDataSource` (prefix `app.datasource.app`), bean `DataSource analystDataSource` và `JdbcTemplate analystJdbcTemplate`, cả hai có `@Qualifier("analyst")` (prefix `app.datasource.analyst`). `JdbcClient` mặc định dùng primary.
  - `PostgresIntegrationTest`: abstract class, `@SpringBootTest`, có static container `postgres:17.6` copy `db/init/` vào `/docker-entrypoint-initdb.d/`. `@DynamicPropertySource` set URL cho cả hai datasource. Có field `protected JdbcTemplate superuserJdbc()` (kết nối bằng user `postgres`) để test kiểm tra quyền.

- [ ] **Step 1:** `git init`, rồi thêm vào `.gitignore`: `.env`, `frontend/node_modules/`, `frontend/dist/`. Thêm vào `.gitattributes`: `*.sql text eol=lf`.
- [ ] **Step 2:** Cập nhật `pom.xml`.
  - Thêm: `spring-boot-starter-webmvc`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, `spring-boot-starter-flyway` + `flyway-database-postgresql`, `spring-boot-starter-oauth2-resource-server`, `com.github.jsqlparser:jsqlparser` (5.3 hoặc mới nhất 5.x), `tools.jackson.dataformat:jackson-dataformat-yaml`.
  - Test: `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:testcontainers-junit-jupiter`.
  - Xóa `spring-boot-docker-compose`.
  - Đổi `<name>` thành `text-to-sql`.
- [ ] **Step 3:** `compose.yaml`: service `postgres`, image `postgres:17.6`, env `POSTGRES_DB=textsql`, `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=postgres`, port `5432:5432`, volume `./db/init:/docker-entrypoint-initdb.d:ro` và named volume cho data.
- [ ] **Step 4:** `00_roles.sql`:
  - Tạo role `app_rw` (LOGIN, password `app_rw_dev`) và `analyst_ro` (LOGIN, password `analyst_ro_dev`).
  - Tạo schema `chinook` (owner postgres) và `app` (owner `app_rw`). `REVOKE ALL ON SCHEMA public FROM PUBLIC`.
  - `ALTER ROLE analyst_ro SET default_transaction_read_only = on`, `SET statement_timeout = '10s'`, `SET search_path = dw`.
  - `ALTER ROLE app_rw SET search_path = app`.
- [ ] **Step 5:** Tạo `01_chinook.sql`.
  - Tải `https://raw.githubusercontent.com/lerocha/chinook-database/v1.4.5/ChinookDatabase/DataSources/Chinook_PostgreSql.sql`.
  - Bỏ các dòng `DROP DATABASE` / `CREATE DATABASE` / `\c`, và BOM nếu có. Thêm `SET search_path TO chinook;` vào đầu file.
  - Ghi nguồn và license (MIT) trong comment đầu file.
  - Mở file ra ghi lại tên bảng và cột thực tế (bản 1.4.5 dùng snake_case: `invoice`, `invoice_line`, `customer`, `track`...), vì Task 2 cần.
- [ ] **Step 6:** `application.yml`:
  - `app.datasource.app` (url `jdbc:postgresql://localhost:5432/textsql`, user `${APP_DB_USER:app_rw}`, password `${APP_DB_PASSWORD:app_rw_dev}`) và `app.datasource.analyst` (tương tự với `analyst_ro`).
  - `spring.flyway.schemas=app`, `default-schema=app`.
  - `.env.example` liệt kê `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`, `JWT_SECRET`.
- [ ] **Step 7: Viết test** `ChinookLoadTest extends PostgresIntegrationTest`:
  ```java
  @Test void chinookLoaded() {
    assertThat(superuserJdbc().queryForObject("select count(*) from chinook.invoice", Long.class)).isEqualTo(412L);
    assertThat(superuserJdbc().queryForObject("select count(*) from chinook.invoice_line", Long.class)).isEqualTo(2240L);
    assertThat(superuserJdbc().queryForObject("select sum(total) from chinook.invoice", BigDecimal.class)).isEqualByComparingTo("2328.60");
  }
  @Test void contextLoads(ApplicationContext ctx) { assertThat(ctx.getBean("analystJdbcTemplate")).isNotNull(); }
  ```
- [ ] **Step 8:** Chạy `./mvnw test -Dtest=ChinookLoadTest`. Kỳ vọng: PASS. Nếu tên bảng hoặc cột khác, sửa query theo đúng file ở Step 5. Nếu tổng tiền khác `2328.60` thì dừng lại, báo, và kiểm tra xem có tải nhầm phiên bản không.
- [ ] **Step 9:** `docker compose up -d`, rồi chạy `docker compose exec postgres psql -U postgres -d textsql -c "select count(*) from chinook.track"`. Kỳ vọng: `3503`.
- [ ] **Step 10:** Commit: `chore: set up build, postgres with Chinook raw data`. Commit luôn cả spec và plan.

---

