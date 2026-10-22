# Task 4 — fix round 3 findings (from scoped re-review of 02aa590)

Reviewer probes: C:\Users\nguye\AppData\Local\Temp\claude\d--Project-FSoft-text-to-sql\2cd17b80-9f4b-4962-a978-45372df40180\scratchpad\p4\ (holes.txt, lex.txt, desync.txt, desync2.txt, Probe4.java, Probe5.java + .out). Note: probe JVMs need System.exit (JSqlParser leaves a non-daemon thread).

## Open — fix all (binding controller rulings)
1. Backstop is not fail-closed: lexicalBackstop (SqlGuard.java:144-205) only lexes '…' strings; JSqlParser keeps $$…$$ verbatim in safeSql, so a `'` inside a dollar string desyncs the lexer and disables rules (a)(b)(c). Proof: lexicalBackstop("SELECT $$'$$, pg_sleep(1), $$'$$") returns EMPTY.
   RULING: dollar-quoting is never needed for analytics → BLOCK any statement containing `$` outside a '…' string literal (reason: "Không hỗ trợ chuỗi dollar-quoted ($$)"), in both the raw input and safeSql. Also lex standard strings correctly ('' escape) and treat E'…' as a string with backslash escapes. If the lexer ever reaches an unterminated string → BLOCK (fail closed), never "skip to end".
2. Keyword columns bypass via the desync: ALLOWED today —
   SELECT $$'$$, session_user, $$'$$ ; SELECT $$'$$, user, current_role, current_schema, current_catalog, $$'$$ ; SELECT $$'$$ || current_user || $$'$$ AS who FROM dw.dim_date ; … WHERE $$'$$ <> current_user OR $$'$$ = ''
   RULING: also enforce keyword columns in the AST walk (Column visitor: unqualified, unquoted name in the keyword set → block), not only lexically. Add `system_user` to the keyword set.
3. `TABLE x` statement: AST walk skips it and the deparser drops the schema (`TABLE app.users` → `TABLE users LIMIT 1000`, ALLOWED). RULING: reject TableStatement (and any non-PlainSelect/SetOperationList/ParenthesedSelect top-level or nested form you can't walk) outright.
4. Session-info functions: add getpgusername, current_database, current_schemas, inet_client_addr, inet_client_port, current_query to the blocked exact names.
5. LIMIT + FETCH (previously deferred minor, now promoted because LLMs emit FETCH FIRST): `… FETCH FIRST 10 ROWS ONLY` → currently `LIMIT 1000 FETCH FIRST 10 ROWS ONLY` (PG error); FETCH FIRST 5000 not capped. RULING: convert an outermost FETCH FIRST n ROWS ONLY into LIMIT min(n,1000) (remove the Fetch); FETCH with a non-literal or WITH TIES → LIMIT 1000 replacing it. Exactly one row-limiting clause in safeSql. Same for UNION + FETCH.

## TDD
Add every probe above as a test first (blocked: items 1-4; correct rewrite: item 5), run RED on current code (capture), fix, GREEN, full suite once (Docker may need to be started — if `docker info` fails, say so in the report rather than skipping silently).
Keep all existing tests green; legit queries with string literals containing 'pg_sleep', 'DROP TABLE', 'current_user', apostrophes ('O''Reilly') must still pass.
