# Task 4 — review findings for fix round 1 (verbatim from task reviewer)

Probe sources demonstrating bypasses (compiled against target/classes):
C:\Users\nguye\AppData\Local\Temp\claude\d--Project-FSoft-text-to-sql\2cd17b80-9f4b-4962-a978-45372df40180\scratchpad\Probe.java
C:\Users\nguye\AppData\Local\Temp\claude\d--Project-FSoft-text-to-sql\2cd17b80-9f4b-4962-a978-45372df40180\scratchpad\p2\Probe2.java

## Critical 1 — CTE-name shadowing (SqlGuard.java:110-113, 180-185)
Recorded tables are skipped if their name is in the base TablesNamesFinder's CTE-name set, which ignores Postgres scoping (non-recursive WITH item cannot see itself or later siblings; CTEs in subqueries don't leak outward), so unqualified names resolve via search_path (pg_catalog always). ALLOWED today:
- WITH pg_user AS (SELECT * FROM pg_user) SELECT * FROM pg_user
- WITH users AS (SELECT * FROM users) SELECT * FROM users
- WITH a AS (SELECT * FROM b), b AS (SELECT 1 FROM dw.dim_date) SELECT * FROM a
- SELECT * FROM (WITH pg_user AS (SELECT 1) SELECT * FROM pg_user) s CROSS JOIN pg_user
- ... WHERE x IN (WITH pg_user AS (SELECT * FROM pg_user) SELECT usename FROM pg_user)
Fix: scoped CTE resolution with a stack of visible CTE names: non-recursive item i sees only earlier items; WITH RECURSIVE also sees itself; nested scopes pop at end. Unqualified name is a CTE only if visible at that point; otherwise a table → must be dw.*. Tests for each case.

## Critical 2 — incomplete traversal (TablesNamesFinder skips clauses) → table AND function rules bypassed
ALLOWED today:
- ORDER BY (SELECT password FROM app.users LIMIT 1)
- OFFSET (SELECT password::int FROM app.users LIMIT 1)   (data leak via cast error)
- LIMIT 10 OFFSET (SELECT count(*) FROM pg_shadow)
- GROUP BY (SELECT 1 FROM app.users LIMIT 1)
- count(*) FILTER (WHERE EXISTS (SELECT 1 FROM app.users))
- rank() OVER (ORDER BY (SELECT 1 FROM pg_shadow))
- DISTINCT ON ((SELECT usename FROM pg_user LIMIT 1))
- FETCH FIRST (SELECT 1 FROM app.users) ROWS ONLY
- ORDER BY pg_sleep(5); ORDER BY current_setting('x')
- OFFSET pg_sleep(5)::text::int
- FILTER (WHERE pg_sleep(1) IS NULL)
- OVER (PARTITION BY pg_sleep(1)); WINDOW w AS (ORDER BY pg_sleep(1))
- string_agg(x, ',' ORDER BY pg_sleep(1))
- inner subquery with ORDER BY pg_sleep(1)
Fix: complete traversal — e.g. hook visit(Table)/visit(Function) on JSqlParser's SelectDeParser/ExpressionDeParser (which must visit every node to print), or a full ExpressionVisitorAdapter + SelectVisitorAdapter walk. Parameterized test per clause above, with both a blocked function and a non-dw table.

## Important 3 — denylist misses SQL-text-executing / server-touching functions (plan-mandated list)
ALLOWED today: query_to_xml('select * from app.users', true, true, ''), table_to_xml('app.users', ...), lo_get(1), pg_advisory_lock(1), nextval('dw.seq'). Also missing: cursor_to_xml, schema_to_xml, database_to_xml, query_to_xml_and_xmlschema, pg_stat_file, setval, lo_*, pg_advisory_*, dblink_*, pg_reload_conf, pg_logdir_ls.
CONTROLLER RULING (binding): replace the exact-name list with a rule: block any function whose (unquoted, lowercased, schema-stripped) name
  - starts with `pg_`, `lo_`, or `dblink`, OR
  - contains `_to_xml` or starts with `query_to_` / `table_to_` / `cursor_to_` / `schema_to_` / `database_to_`, OR
  - is one of: current_setting, set_config, nextval, setval, currval, lastval, txid_current, version, current_user, session_user, inet_server_addr, inet_server_port, has_table_privilege, has_schema_privilege, has_database_privilege, to_regclass, xmlparse... (keep the brief's original names too).
  Keep it as a small named predicate with a test per family. Legit analytics functions (sum, count, avg, round, extract, date_trunc, to_char, coalesce, rank, row_number, string_agg, lower, upper, concat, cast) must still pass — add an allowed test listing them.

## Important 4 — tests don't cover the bypass classes
Add tests for every clause in item 2 and the CTE scoping cases in item 1 (they must fail RED against the current code first — capture that output).

## Deferred (do NOT fix this round; ledgered as minor): quoted "DW" schema accepted; FETCH FIRST not normalized vs LIMIT; nested data-modifying CTE reason non-specific.
