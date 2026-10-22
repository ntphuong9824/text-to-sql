# Task 4 — fix round 4 findings

Source: round-3 re-review (static only — reviewer could not run probes) + controller live probe at 2dfcad5
(probe: C:\Users\nguye\AppData\Local\Temp\claude\d--Project-FSoft-text-to-sql\2cd17b80-9f4b-4962-a978-45372df40180\scratchpad\p6\P6.java, out.txt)

## Open
1. (Important, CONFIRMED live) `SqlGuard.check("SELECT * FROM dw.dim_date FETCH FIRST ROWS ONLY")` throws
   NullPointerException: Cannot invoke "java.lang.Long.longValue()" at net.sf.jsqlparser.statement.select.Fetch.getRowCount(Fetch.java:28)
   at SqlGuard.fetchRows(SqlGuard.java:327) ← applyLimit(SqlGuard.java:305) ← check(SqlGuard.java:135).
   check() must never throw: (a) FETCH FIRST ROWS ONLY with no count means 1 row in Postgres → LIMIT 1; (b) wrap the whole check in a fail-closed catch: any unexpected RuntimeException → blocked("Không kiểm tra được câu SQL") — never propagate.
   Tests: FETCH FIRST ROWS ONLY → allowed, safeSql has exactly LIMIT 1 and no FETCH; FETCH NEXT 1 ROW ONLY → LIMIT 1; a test proving check() returns (not throws) for an input that makes an internal step throw (e.g. via a package-private seam or a known-problematic input).
2. (Verification gap) AST keyword-column check must work independently of the lexer. Add a test that exercises the AST path alone (e.g. a package-private method that runs only the AST inspection, or assert on the inspector result) for `SELECT current_user FROM dw.dim_date` and `SELECT x FROM dw.dim_date WHERE session_user = 'a'`. If JSqlParser 5.3 gives unqualified columns an empty Table instead of null, fix the "unqualified" predicate accordingly.
3. (Verified OK live — add as regression tests only) nested TABLE forms are blocked (parse-error path): `SELECT a FROM dw.dim_date UNION TABLE app.users`, `WITH x AS (TABLE app.users) SELECT * FROM x`, `SELECT * FROM dw.dim_date WHERE 1 IN (TABLE users)`.
4. (Minor, do it — cheap) `--` line comment must also end at `\r`.

Also note: Vietnamese reasons printed as mojibake in a plain `java` console — that's console encoding, not a bug; do not change.

## TDD
Tests first, RED (capture), fix, GREEN, full ./mvnw test once (Docker is up).
