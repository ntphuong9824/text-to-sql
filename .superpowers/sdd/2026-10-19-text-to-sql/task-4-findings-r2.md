# Task 4 — fix round 2 findings (from scoped re-review of bfd40e8, verbatim summary)

Reviewer probe sources/outputs: C:\Users\nguye\AppData\Local\Temp\claude\d--Project-FSoft-text-to-sql\2cd17b80-9f4b-4962-a978-45372df40180\scratchpad\p3\ (Probe3.java, bypass.txt, more.txt, m3.txt, old.txt, old2.txt, more.out)

## Open — fix all of these this round
1. (Critical 1, NEW regression) Quoted-identifier case folding. SqlGuard.java:278-279 lowercases getUnquotedAliasName() even for quoted aliases; SqlGuard.java:181 lowercases table names. In Postgres "PG_USER" ≠ pg_user. ALLOWED now:
   - WITH "PG_USER" AS (SELECT 1) SELECT * FROM pg_user
   - WITH "Users" AS (SELECT 1) SELECT * FROM users
   Rule: fold to lowercase only unquoted identifiers; quoted names compare exactly — on both CTE-alias and table-reference sides. (This also covers deferred minor: quoted "DW" schema must not equal dw.)
2. (Critical 2, NEW regression) Joins inside a parenthesized FROM item not walked. ALLOWED now:
   - SELECT * FROM (dw.dim_date d JOIN pg_shadow s ON true)
   - SELECT * FROM (dw.dim_date b CROSS JOIN pg_user)
   - ... JOIN (dw.dim_date b JOIN dw.dim_date c ON current_setting('x')='1') ON true
   - (dw.dim_date d JOIN dw.dim_date e ON pg_sleep(1) IS NULL)
3. (Critical 2, NEW regression) IS [NOT] DISTINCT FROM operands not walked:
   - WHERE x IS DISTINCT FROM (SELECT 1 FROM pg_shadow); IS NOT DISTINCT FROM (SELECT ...); (SELECT 1 FROM pg_shadow) IS DISTINCT FROM x; x IS DISTINCT FROM pg_sleep(1)
4. (Critical 2) SQL/JSON constructors not walked: JSON_ARRAYAGG((SELECT usename FROM pg_user LIMIT 1)), JSON_ARRAY((SELECT ...)), JSON_OBJECT('k' : (SELECT ...)), JSON_OBJECTAGG(k VALUE (SELECT ...)), JSON_OBJECT('k' VALUE pg_sleep(1))
5. (Critical 2) JSON operators ->, ->>, #>> operands not walked: d.j -> (SELECT usename FROM pg_user LIMIT 1); d.j ->> pg_sleep(1)::text; (SELECT '{}'::jsonb FROM pg_shadow LIMIT 1) ->> 'a'
6. (Important 3) Keyword forms current_user / session_user (parsed as Column, not Function) are allowed. Block these keyword-columns when unqualified: current_user, session_user, user, current_role, current_schema, current_catalog. Test with the real syntax `SELECT current_user` (not `current_user()`).
7. (Controller ruling, extends Important 3) Add ts_stat and ts_rewrite (run SQL text) to the blocked family; also block any function whose name is in {query_to_xml…} already covered. Test: SELECT * FROM ts_stat('SELECT 1') blocked.
8. (Minor, regression in safe direction — fix since you're here) WITH RECURSIVE applies to every WITH item, not only the first: `WITH RECURSIVE r AS (SELECT 1 n), s AS (SELECT n+1 FROM s WHERE n<3 UNION ALL SELECT n FROM r) SELECT * FROM s` must be ALLOWED.
9. (Old bug, now promoted — LLMs emit this constantly) UNION with trailing LIMIT: `SELECT a FROM dw.x UNION SELECT a FROM dw.y LIMIT 10` currently yields `... LIMIT 10 LIMIT 1000` (Postgres error). JSqlParser attaches the LIMIT to the last branch; the outermost-LIMIT logic must recognise/cap it (LIMIT 10 stays 10, LIMIT 99999 → 1000, OFFSET preserved), producing exactly one LIMIT.

## Fail-closed requirement (structural)
The deparser-hook traversal keeps having holes wherever JSqlParser prints a node as text without visiting children. Add a FAIL-CLOSED backstop inside SqlGuard: after the AST walk, lexically scan the deparsed safeSql (skip string literals and quoted identifiers' contents appropriately) for:
  (a) any identifier immediately followed by `(` whose name satisfies isBlockedFunction → block;
  (b) the keyword-columns from item 6 as standalone tokens → block;
  (c) any `pg_` / `information_schema` identifier token at all (pg_catalog relations, pg_user, pg_shadow…) → block.
This is a deliberate belt-and-braces layer; keep it as a small, separately tested method (e.g. `lexicalBackstop(String sql) -> Optional<String reason>`).
Note: an additional DB-planner layer (EXPLAIN-based relation check) will be added in Task 5, so table-rule completeness in the AST is not the last line — but this task must still close every item above.

## TDD
Add every query above as a test FIRST, run RED on the current code (capture), then fix, GREEN, full suite once.
