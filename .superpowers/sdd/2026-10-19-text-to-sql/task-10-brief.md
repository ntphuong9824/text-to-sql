### Task 10: Schema `app`, đăng ký, đăng nhập, JWT

**Files:**
- Create: `src/main/resources/db/migration/V1__app_schema.sql`, `config/SecurityConfig.java`, `auth/JwtService.java`, `auth/UserRepository.java`, `auth/AuthController.java`, `auth/AuthDtos.java`, `config/ApiExceptionHandler.java`
- Modify: `application.yml` (`app.jwt.secret: ${JWT_SECRET:}`, `app.jwt.ttl: 24h`, `app.cors.allowed-origins: http://localhost:5173`)
- Test: `auth/AuthFlowTest.java` (integration, MockMvc)

**Interfaces:**
- Produces:
  - Bảng `app.users(id bigserial PK, username varchar(50) unique not null, password_hash text not null, created_at timestamptz default now())`.
  - Bảng `app.conversations(id uuid PK, user_id bigint FK not null, title varchar(80) not null, created_at, updated_at)`, index `(user_id, updated_at desc)`.
  - Bảng `app.messages(id bigserial PK, conversation_id uuid FK on delete cascade, turn_index int, question text, rewritten_question text, sql text, status varchar(10), message text, chart jsonb, result jsonb, created_at)`.
  - `UserRepository` (dùng `JdbcClient`): `Optional<UserRow> findByUsername(String)`, `long create(String username, String passwordHash)`, với `record UserRow(long id, String username, String passwordHash)`.
  - `JwtService`: `String issue(long userId, String username)`. Subject = userId, claim `username`, ký HS256 bằng `NimbusJwtEncoder`. Bean `JwtDecoder` dùng cùng secret.
  - Helper `CurrentUser.id(Authentication) -> long`: static, lấy `Long.parseLong(jwt.getSubject())`.
  - DTO:
    - `RegisterRequest(@NotBlank @Size(min=3,max=50) @Pattern("[a-zA-Z0-9_.]+") username, @Size(min=8,max=100) password)`
    - `LoginRequest(username, password)`
    - `AuthResponse(String token, String username)`
    - `ErrorResponse(String message)`
  - Endpoint:
    - `POST /api/auth/register` → 201 + `AuthResponse`; trùng username → 409.
    - `POST /api/auth/login` → 200; sai thông tin → 401, message "Sai tên đăng nhập hoặc mật khẩu".
  - `SecurityConfig`: stateless, tắt CSRF (API dùng Bearer token), CORS theo config. `permitAll` cho `/api/auth/**`, mọi `/api/**` khác yêu cầu authenticated. `oauth2ResourceServer(jwt)`. `PasswordEncoder` dùng BCrypt.
  - `ApiExceptionHandler`: lỗi validation → 400 kèm `ErrorResponse` (tiếng Việt), lỗi không lường trước → 500 với message chung, không lộ stacktrace.
  - Khi khởi động: `JWT_SECRET` trống hoặc ngắn hơn 32 byte → **fail fast**, thông báo rõ. `PostgresIntegrationTest` (Task 1) thêm `app.jwt.secret` cố định, dài 32 byte trở lên, vào `@DynamicPropertySource`, để mọi integration test cũ vẫn chạy.

- [ ] **Step 1: Viết test** `AuthFlowTest`:
  - Đăng ký `alice` → 201, có token. Đăng ký lại `alice` → 409.
  - Đăng nhập đúng → 200. Sai mật khẩu → 401.
  - Mật khẩu 5 ký tự → 400.
  - `GET /api/conversations` không kèm token → 401. Token sai chữ ký → 401. Token hết hạn (tạo bằng `JwtService` với TTL âm, hoặc encoder trong test) → 401.
  - Trong DB, `password_hash` bắt đầu bằng `$2` (BCrypt), không chứa mật khẩu gốc.
  - `analystJdbcTemplate` chạy `select * from app.users` → exception (kiểm tra lại sau khi Flyway đã tạo bảng).
- [ ] **Step 2:** Chạy. Kỳ vọng: FAIL. Implement. Chạy lại. Kỳ vọng: PASS.
- [ ] **Step 3:** Commit: `feat(auth): add registration, login and JWT security`.

---

