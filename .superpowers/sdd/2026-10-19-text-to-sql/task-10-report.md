# Task 10 report
Implemented V1__app_schema.sql (users/conversations/messages in schema app), JwtProperties (fail-fast: secret null/<32 bytes), JwtService, CurrentUser, UserRepository, AuthDtos, AuthController, SecurityConfig (stateless, CSRF off, CORS, JSON 401 "Bạn cần đăng nhập", HS256 Nimbus encoder/decoder), ApiExceptionHandler. application.yml: app.jwt, app.cors, spring.flyway.create-schemas=false. PostgresIntegrationTest sets app.jwt.secret.
Tests: AuthFlowTest 9 tests (register/409, login, wrong pw/unknown user same message, 400s, 401 missing/tampered/expired, valid token passes security, BCrypt hash, analyst cannot read app.users, 72-byte BCrypt limit).
TDD: RED = initial compile failure/ missing beans before implementation (AuthFlowTest first failing run found a real bug: 100-char password -> 500 because Spring BCrypt rejects >72 bytes; fixed with @AssertTrue 72-byte validation on register and treat as mismatch on login).
GREEN: `./mvnw test` -> Tests run: 266, Failures: 0, Errors: 0, BUILD SUCCESS.
Concerns: brief's password max=100 kept, plus extra 72-byte limit. AutoConfigureMockMvc package is org.springframework.boot.webmvc.test.autoconfigure. Fail-fast not covered by an automated test.

## Fix round 1
Changes: catch-all handler now preserves status of Spring MVC ErrorResponse exceptions (Vietnamese messages for 400/404/405/415, DEBUG log; org.springframework.web.ErrorResponse is an interface so it is handled via instanceof inside the Exception handler); test-only TestMeController (GET /api/test/me) replaces the weak status!=401 test; BearerTokenResolver ignores Authorization on /api/auth/**; UserRepository.create translates DuplicateKeyException to UsernameTakenException, only that maps to 409; register password is @NotBlank.
RED: `./mvnw test -Dtest=AuthFlowTest` -> 12 run, 3 failures (frameworkErrorsKeepTheirStatus, staleBearerTokenDoesNotBlockAuthEndpoints, blankPasswordIsRejectedOnRegister).
GREEN: `./mvnw test` -> 269 run, 0 failures, BUILD SUCCESS.
Not tested: non-username DuplicateKeyException falling through to 500.
