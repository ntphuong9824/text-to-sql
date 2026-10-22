# Task 5 report
Implemented warehouse/{ColumnInfo,QueryResult,SqlExecutionException,SqlPolicyViolationException,SqlExecutor,PlanInspector} + SqlExecutorTest (8) and PlanInspectorTest (8).
Tests: focused 16/16 pass; full `./mvnw test` 187 tests, 0 failures, BUILD SUCCESS.
TDD note: tests and implementation were written in the same pass, so no separate RED run was captured.
Design: executor runs PlanInspector first (violation -> SqlPolicyViolationException, not a subclass of SqlExecutionException); PlanInspector walks every JSON node, checks Relation Name/Schema == dw, Function Name via SqlGuard.isBlockedFunction, and regex-scans all string values (single-quoted literals stripped) for ident( calls. EXPLAIN failure -> Optional.empty().
Extra tests: literal 'pg_sleep(1)' not a false positive; EXPLAIN failure (chinook.album, bad column) -> empty.

## Fix round 1
1. timestamptz: SqlExecutor now branches on getColumnTypeName ("timestamptz" -> OffsetDateTime, else LocalDateTime). Tests: date_trunc month, timestamp cast, now().
2. PlanInspector.stripLiterals now skips both '...' literals and "..." identifiers (doubled-quote escapes), so a quote inside one cannot flip state. Regression test with the "a'b" query.
3. PlanInspector flags bare current_user/session_user/current_role/current_schema/current_catalog/system_user/user (case-insensitive, whole word, outside literals/identifiers). Test loops over all keywords.
RED (before fix): `./mvnw test -Dtest='SqlExecutorTest,PlanInspectorTest'` -> 4 new tests failed: quoteInsideQuotedIdentifierDoesNotHideCall, keywordSessionFunctionsAreCaught (current_user), timestamptzColumnIsDateWithIsoString and plainTimestampAndNowWork ("Cannot convert the column of type TIMESTAMPTZ to requested type java.time.LocalDateTime").
GREEN: full `./mvnw test` -> 191 tests, 0 failures, BUILD SUCCESS (PlanInspectorTest 10, SqlExecutorTest 10).
