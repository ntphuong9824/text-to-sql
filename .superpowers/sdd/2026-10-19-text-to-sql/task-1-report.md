# Task 1 report

Status: DONE_WITH_CONCERNS

## Implemented
- pom.xml: added all brief dependencies (all versions managed by Boot 4.1.1 BOM except jsqlparser 5.3, explicit); testcontainers artifacts `testcontainers-postgresql` / `testcontainers-junit-jupiter` resolved fine; removed spring-boot-docker-compose; name=text-to-sql.
- compose.yaml (postgres:17.6, textsql, init volume, pgdata), .env.example, .gitignore/.gitattributes edits.
- db/init/00_roles.sql, db/init/01_chinook.sql (v1.4.5, header with source+MIT, DROP/CREATE DATABASE and `\c` removed, BOM absent, `SET search_path TO chinook;`).
- application.yml (replaces application.properties; flyway schemas/default-schema=app, `fail-on-missing-locations: false`).
- DataSourceConfig (primary appDataSource; qualifier "analyst" DataSource + JdbcTemplate `analystJdbcTemplate`), using `org.springframework.boot.jdbc.autoconfigure.DataSourceProperties`.
- PostgresIntegrationTest (static shared container, started in static block; superuserJdbc()), ChinookLoadTest; removed TrainingApplicationTests.

## Tests / TDD
- RED: first run failed (container exit: `\c chinook` left in script; then context failure with analystJdbcTemplate missing/ TimeZone). GREEN after fixes.
- `./mvnw test`: 2 tests, 0 failures. Docker: `select count(*) from chinook.track` = 3503. invoice=412, invoice_line=2240, sum(total)=2328.60 verified.

## Chinook tables/columns (schema chinook, snake_case)
- album(album_id, title, artist_id); artist(artist_id, name)
- customer(customer_id, first_name, last_name, company, address, city, state, country, postal_code, phone, fax, email, support_rep_id)
- employee(employee_id, last_name, first_name, title, reports_to, birth_date, hire_date, address, city, state, country, postal_code, phone, fax, email)
- genre(genre_id, name); media_type(media_type_id, name); playlist(playlist_id, name); playlist_track(playlist_id, track_id)
- invoice(invoice_id, customer_id, invoice_date TIMESTAMP, billing_address, billing_city, billing_state, billing_country, billing_postal_code, total NUMERIC(10,2))
- invoice_line(invoice_line_id, invoice_id, track_id, unit_price NUMERIC(10,2), quantity)
- track(track_id, name, album_id, media_type_id, genre_id, composer, milliseconds, bytes, unit_price NUMERIC(10,2))
- All tables owned by postgres; analyst_ro has NO grants yet (Task 2 must grant/ build dw). search_path for analyst_ro is `dw` (schema not created yet).

## Concerns
- Windows JDK reports timezone Asia/Saigon, which Postgres 17 rejects (FATAL invalid TimeZone). Fixed by `-Duser.timezone=Asia/Ho_Chi_Minh` in surefire argLine and spring-boot-maven-plugin jvmArguments; running the jar directly on such a machine needs the same flag.
- Test output warnings: Flyway "No migrations found" (expected until Task 10) and Mockito/ByteBuddy dynamic agent warning (JDK 21 noise, not ours).
- `.gitattributes`/`.gitignore` appended; git warns about LF/CRLF only.

## Fix round 1
Changes:
- DataSourceConfig: added `@Primary JdbcTemplate appJdbcTemplate(appDataSource)`, so Boot's NamedParameterJdbcTemplate/JdbcClient build on the app datasource.
- TrainingApplication.main: `normalizeLegacyTimeZone()` maps JVM default `Asia/Saigon` to `Asia/Ho_Chi_Minh` before the context starts (covers jar and IDE launches). Pom flags (surefire argLine, boot plugin jvmArguments) kept, since tests do not run main.
- ChinookLoadTest: new test `defaultBeansRunAsAppRwAndAnalystAsAnalystRo` (`select current_user` via JdbcClient and default JdbcTemplate = app_rw, analyst template = analyst_ro).
Command: `./mvnw -q test` (full suite). Output: surefire report tests=3 errors=0 skipped=0 failures=0; no ERROR lines.
