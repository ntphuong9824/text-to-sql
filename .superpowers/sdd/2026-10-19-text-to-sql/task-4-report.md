# Task 4 report: SqlGuard

Status: DONE_WITH_CONCERNS (concerns: no RED run, Docker tests fail in env). Commit f0ed551.

## Implemented
- `guardrail/GuardResult.java`: record + `allowed(safeSql)` / `blocked(reason)`.
- `guardrail/SqlGuard.java`: `@Component`, `MAX_ROWS=1000`, `check(sql)`. Rules in brief order.
  - An `Inspector extends TablesNamesFinder<Void>` (JSqlParser 5.3, generic visitor with context) walks the whole tree. It overrides `extractTableName` (collects Table nodes), `visit(Function)`, `visit(TableFunction)`, `visit(WithItem)`, `visit(PlainSelect)`, `visit(SetOperationList)`.
  - CTE refs: only tables present in the set returned by `getTables` are checked, so the base class's scoped CTE filtering is reused.
  - Data-modifying CTE: JSqlParser parses it. `WithItem.getParenthesedStatement()` not a `ParenthesedSelect` makes it blocked with a specific reason.
  - Functions: name lowercased, schema prefix and quotes stripped (`pg_catalog.pg_sleep`, `"pg_sleep"` are caught), also in FROM (`dblink(...)`).
  - INTO / FOR UPDATE checked on PlainSelect, SetOperationList and the top-level Select.
  - `db.schema.table` is rejected. Quoted `"dw"` is accepted.
  - LIMIT on the outermost statement: missing gives `LIMIT 1000`. Over 1000, `LIMIT ALL`/`NULL` or a non-literal expression gives 1000. OFFSET is kept.
  - `safeSql` is `select.toString()`.
- Reasons are Vietnamese and name the offending table or function.

## Tests
`SqlGuardTest`: 45 tests (31 parameterized blocked cases including all brief cases, plus nested-function, quoted/schema-qualified function, dblink in FROM, UNION/CTE with bad table, FOR UPDATE, 3-part names; allowed cases from the brief; LIMIT ALL, OFFSET, inner LIMIT, UNION, trailing `;`; Postgres syntax: EXTRACT, `::numeric`, ILIKE, RANK() OVER PARTITION, FILTER (WHERE), DATE_TRUNC).

- RED: not captured separately (I wrote test and impl before the first run). The first run was a compile error (ambiguous `getTables`, fixed with a cast). The second had 1 failure (CTE name `t` treated as a table), fixed as above.
- GREEN: `./mvnw -q test -Dtest=SqlGuardTest` gives Tests run: 45, Failures: 0, Errors: 0.
- Full `./mvnw test`: 68 run, 0 failures, 17 errors. All errors are `NoClassDefFound ... PostgresIntegrationTest` (Testcontainers/Docker unavailable in this environment). They are not in guardrail code, and every non-Docker test passes.

## Concerns
- Table functions other than blocked ones (e.g. `generate_series`) are allowed. They are not tables and the analyst role is read-only anyway.
- Built-in Postgres functions outside the list (e.g. `pg_stat_file`) are not blocked, per the brief's fixed list.
- Docker-based integration tests could not be verified here.

## Fix round 1 (findings-r1)

Changes (SqlGuard.java, SqlGuardTest.java):
- Critical 2: replaced the TablesNamesFinder subclass with a full walk through JSqlParser deparser visitors (SelectDeParser + ExpressionDeParser subclasses), which visit every clause they print (ORDER BY, OFFSET, LIMIT, FETCH, GROUP BY, HAVING, DISTINCT ON, FILTER, OVER, string_agg ORDER BY, subqueries). The deparser prints WINDOW definitions and frame bounds as text, so those are walked explicitly. Table and function hooks cover every position.
- Critical 1: scoped CTE resolution. A stack of frames (one per SELECT); a WITH item is added to the frame after its body is visited (so it sees only earlier siblings), or before it for WITH RECURSIVE. Nested frames pop on exit. An unqualified table counts as a CTE only if visible; otherwise it is a table and must be dw.*.
- Important 3: `isBlockedFunction(name)` predicate: prefixes pg_, lo_, dblink, query_to_/table_to_/cursor_to_/schema_to_/database_to_; contains `_to_xml`; exact set (current_setting, set_config, nextval, setval, currval, lastval, txid_current, version, current_user, session_user, inet_server_addr/port, has_*_privilege, to_regclass, xmlparse). Analytic function names are checked too.
- Important 4: new tests `blocksBypassAttempts` (38 queries from findings: CTE scoping, every clause for tables and functions, every function family) and `allowsAnalyticsFunctionsAndScopedCtes`.

RED: `./mvnw -q test -Dtest=SqlGuardTest` on the old code gave `Tests run: 84, Failures: 37` (37 of the 38 new blocksBypassAttempts cases failed, all other tests passed).
GREEN: first pass after the rewrite had 1 failure (WINDOW w AS (ORDER BY pg_sleep(1)), printed as text by the deparser); fixed with explicit window walk, then `Tests run: 84, Failures: 0, Errors: 0`.
Full `./mvnw test`: `Tests run: 107, Failures: 0, Errors: 0, BUILD SUCCESS` (Docker up).
Deferred items untouched.

## Fix round 2 (findings-r2)

Changes:
1. Quoted identifiers: `fold()` lowercases only unquoted names, quoted compare exactly; used for CTE aliases, table refs and the schema check (so "DW" no longer equals dw).
2. ParenthesedFromItem: joins (right item and ON expressions) walked explicitly.
3. IsDistinctExpression: both operands walked.
4. SQL/JSON: JsonFunction (pairs, expressions) and JsonAggregateFunction (expression, key, value, filter, order by) walked.
5. JSON operators: JsonOperator operands and JsonExpression (base + ident list) walked.
6. Keyword columns (current_user, session_user, user, current_role, current_schema, current_catalog) blocked by the lexical backstop when unquoted and unqualified.
7. ts_stat, ts_rewrite added to the exact blocked set.
8. WITH RECURSIVE: a scope is recursive if any item of that WITH is recursive.
9. UNION trailing LIMIT: a LIMIT on the last unparenthesized branch is treated as the outermost one (kept, capped to 1000, OFFSET kept, exactly one LIMIT).
- `isBlockedFunction` is now `public static`.
- Fail-closed `lexicalBackstop(String) -> Optional<String>` runs on the deparsed safeSql: skips string literals; blocks blocked-function calls (quoted or not, whitespace before `(`), any pg_* / information_schema identifier, and the keyword columns. Tested separately.

RED: `./mvnw -q test -Dtest=SqlGuardTest` on the round-1 code (with a stub backstop so tests compile): Tests run: 119, Failures: 34.
GREEN: same command after the fix: Tests run: 119, Failures: 0 (two iterations: first left 3 failures, one a wrong test expectation using a pg_ column, two JSON `->` subqueries held in JsonExpression ident list).
Full ./mvnw test: see result in commit message / reply (BUILD result recorded below).

## Fix round 3 (findings-r3)

Changes:
1. Lexer rewritten to fail closed: standard strings ('' escape), E'...' (backslash escapes), comments and quoted identifiers are lexed; any `$` outside a string literal, or an unterminated string/comment/quoted identifier, is blocked (Vietnamese reasons; dollar reason mentions "dollar-quoted"). The backstop now runs on both the raw input and safeSql.
2. Keyword columns also enforced in the AST (Column visitor: unqualified, unquoted name in the set); `system_user` added.
3. `TABLE x` rejected via a TableStatement hook.
4. Added getpgusername, current_database, current_schemas, inet_client_addr, inet_client_port, current_query to the blocked exact names.
5. Outermost FETCH FIRST n ROWS ONLY converted into LIMIT min(n,1000) and the Fetch removed; WITH TIES or non-literal gives 1000; OFFSET kept; works for UNION trailing FETCH (exactly one LIMIT).
Note: JSqlParser cannot parse E'...\'...' strings with escaped quotes, so those are blocked by the parse-error path; the lexer handles E-strings independently and is unit tested.

RED: `./mvnw -q test -Dtest=SqlGuardTest` on the round-2 code: Tests run: 140, Failures: 18.
GREEN: same command after the fix: all pass (164 tests in the full run).
Docker: `docker info` succeeded on the first poll. Full `./mvnw test`: Tests run: 164, Failures: 0, Errors: 0, BUILD SUCCESS.

## Fix round 4 (findings-r4)

Changes (SqlGuard.java, tests in SqlGuardTest.java):
1. FETCH without count: `fetchRows` no longer calls `Fetch.getRowCount()` (it NPEs when no count was given). No expression and no JDBC parameter now means 1 row (Postgres default), so `FETCH FIRST ROWS ONLY` becomes `LIMIT 1`. Fail-closed wrapper: `check()` now only calls package-private `checkUnguarded()` inside `try/catch (RuntimeException | StackOverflowError)` and returns `blocked("Không kiểm tra được câu SQL")`, so it never throws. StackOverflowError is caught as well because deeply nested input can overflow the parser or visitor recursion.
2. AST-only seam: package-private `static Optional<String> astKeywordColumn(String sql)` parses the SQL and runs only the Inspector, with no lexer. Checked in JSqlParser 5.3: an unqualified Column has `getTable() == null`, so the existing "unqualified" check was already correct and was not changed.
3. Regression tests for nested TABLE forms (UNION TABLE, CTE TABLE, IN (TABLE ...)). All three were already blocked.
4. A lexer `--` line comment now also ends at `\r`.
`isBlockedFunction` is still `public static`.

New tests: fetchFirstWithoutCountIsOneRow (FETCH FIRST ROWS ONLY and FETCH NEXT 1 ROW ONLY: allowed, exactly one LIMIT, ends with `LIMIT 1`, no FETCH), checkNeverThrowsWhenAnInternalStepFails (a subclass overrides checkUnguarded to throw, and check() returns the blocked reason), astInspectionBlocksKeywordColumnsWithoutTheLexer (current_user, WHERE session_user = 'a'; negative cases d.user, "user", date_key), blocksNestedTableForms (3 queries), lineCommentEndsAtCarriageReturn.

RED (stubs added so the tests compile: check delegates without a catch, astKeywordColumn returns empty):
`./mvnw -q test -Dtest=SqlGuardTest` gave `Tests run: 148, Failures: 2, Errors: 2`:
- fetchFirstWithoutCountIsOneRow: NullPointerException: Cannot invoke "java.lang.Long.longValue()"
- checkNeverThrowsWhenAnInternalStepFails: IllegalState boom (propagated)
- astInspectionBlocksKeywordColumnsWithoutTheLexer: Expecting Optional to contain (stub)
- lineCommentEndsAtCarriageReturn: Expecting Optional to contain a value but it was empty
- blocksNestedTableForms passed (expected: these were already blocked)
GREEN: same command gives `Tests run: 148, Failures: 0, Errors: 0`. One iteration was needed: my own test had a regex escape mistake, fixed by using `endsWith("LIMIT 1")`.
Full `./mvnw test` (Docker up): `Tests run: 171, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS`.
