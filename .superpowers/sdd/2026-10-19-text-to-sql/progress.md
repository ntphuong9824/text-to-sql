# SDD ledger — plan: docs/superpowers/plans/2026-10-19-text-to-sql.md

Spec: docs/superpowers/specs/2026-10-19-text-to-sql-design.md
Branch: feat/text-to-sql (from main b6aaf92). Ruling: work on a feature branch in place, no separate worktree — repo was just created by git init, nothing else lives on main — cost if wrong: none, branch is isolated.

## Pre-flight scan
| Pair / Task | Produces vs consumes | Finding |
|---|---|---|
| T1→T2..T12 | PostgresIntegrationTest, analystJdbcTemplate, superuserJdbc() | consistent |
| T1→T10 | PostgresIntegrationTest gets jwt secret in T10 | T10 file list omits base class. Ruling: T10 may modify PostgresIntegrationTest — plan text says so in T10 body — cost: none |
| T7→T9,T11,T12 | FakeLlmClient @TestConfiguration @Primary | how tests pick it up unspecified. Ruling: T7 adds FakeLlmConfig and PostgresIntegrationTest @Import's it so no integration test ever calls the network; tests reset it in @BeforeEach — cost if wrong: minor refactor |
| T4→T5,T8,T9,T12 | SqlGuard.check -> GuardResult(allowed,safeSql,reason) | consistent |
| T5→T6,T9,T11,T12 | QueryResult/ColumnInfo | consistent |
| T3→T9 | SchemaPromptRenderer.render() | consistent |
| T8→T9,T12 | FewShotSelector.select/all, TextNormalizer | consistent |
| T9→T11,T12 | Nl2SqlPipeline.run(question, history) -> TurnOutcome | consistent |
| T10→T11 | CurrentUser.id, ApiExceptionHandler | consistent |
| T11→T13/T14 | DTO JSON camelCase | consistent |
| T12 self | EvalRunner @Profile("eval") but EvalRunnerTest calls runAll | Ruling: EvalRunnerTest constructs EvalRunner directly from injected beans (no profile) — cost: none |
| T12 self | attack pass rule `status!=OK || guard allowed` is near-tautological | Ruling: keep, it is the spec's definition; report must break down blocked/refused/answered — cost: attack metric weak signal |
| T10 self | test hits GET /api/conversations before T11 exists | 401 precedes routing — consistent |
| T1..T15 each | tests vs code vs files | no self-contradiction found |

## Task log
Task 1: implemented dff1108 (base b6aaf92); review: 2 Important (no @Primary JdbcTemplate → JdbcClient may bind analyst; timezone workaround only in maven plugins)
Task 1: minor (deferred): no test that both roles actually connect / read_only applied (Task 2 covers part)
Task 1: minor (deferred): pom dependency ordering cosmetic
Task 1: fix round 1/5 (2 addressed, 0 open; commits dff1108..296711d)
Task 1: minor (deferred): normalizeLegacyTimeZone untested (tests don't run main); only Asia/Saigon mapped
Task 1: complete (commits b6aaf92..296711d, review clean)
Task 2: minor (deferred): commit 80640ce trailer says Sonnet 5.5 instead of Opus 5.5 (subagent attribution) — cosmetic
Task 2: minor (deferred): AnalystRolePermissionTest negative tests assert only DataAccessException type — should assert "permission denied"/"statement timeout" messages (security-relevant, triage at final review)
Task 2: minor (deferred): dim_date generate_series timestamptz/session-TZ dependence; to_char 'Month' lc_time dependence; country_region only from customer countries; relative script path in WarehouseBuildTest
Task 2: ⚠️ items resolved by controller: 00_roles.sql settings verified by passing Task 1/2 tests
Task 2: complete (commits 296711d..80640ce, review clean)
Task 3: note: implementer skipped RED step (tests+impl together) — TDD deviation
Task 3: review: 3 Important (region value mappings mixed into synonyms; RED step missing; renderer test assertions loose)
Task 3: Ruling: "RED missing" cannot be fixed retroactively — require the new renderer/mapping assertions in the fix round to be run RED first and shown in the fix report — cost if wrong: none
Task 3: minor (deferred): Jackson 3 exceptions unchecked so loader's IOException catch partially dead; revenue column/metric synonym duplication; year sampleValues hard-coded
Task 3: fix round 1/5 (2 addressed, 1 contested — RED only shown for region assertion; commits c0d6d4d..c1c5897)
Task 3: Ruling: finding #3 (shape/order assertions never RED) is satisfied — those assertions pin behavior that was already correct, so they cannot fail without sabotaging the code; the one assertion for changed behavior was shown RED→GREEN — cost if wrong: none (tests exist and pass)
Task 3: minor (deferred): shape-test regex permissive; full suite not re-run after fix (will run in Task 4)
Task 3: complete (commits 80640ce..c1c5897, review clean after ruling)
Task 4: note: implementer skipped RED again; reported 17 integration errors (NoClassDefFound PostgresIntegrationTest) — controller re-running full suite to check
Task 4: controller verified full suite at f0ed551: 68 tests, 0 failures, 0 errors — implementer's 17 errors were transient Docker access in its run
Task 4: review: 2 Critical (CTE-name shadowing bypass; incomplete traversal — ORDER BY/GROUP BY/OFFSET/FETCH/DISTINCT ON/FILTER/OVER/WINDOW skip table+function checks), 2 Important (denylist too narrow — plan-mandated; tests miss bypass classes)
Task 4: Ruling: plan's exact-name function denylist (plan-mandated) is insufficient vs spec anti-goal "đọc được bảng ngoài warehouse" — replace with prefix/family predicate (pg_*, lo_*, dblink*, *_to_xml*, query_to_*, nextval/setval/current_setting/...), legit analytics functions must pass — cost if wrong: an LLM query using some exotic pg_ function gets blocked (acceptable)
Task 4: minor (deferred): quoted "DW" schema accepted; FETCH FIRST not normalized (LIMIT+FETCH → PG error, fails closed); nested data-modifying CTE gets generic reason
Task 4: fix round 1/5 (Critical1 listed cases fixed but quoted-case regression; Critical2 listed fixed but new holes: parenthesized joins, IS DISTINCT FROM, JSON ctor/ops; Important3 keyword current_user missed; Important4 addressed; commits f0ed551..bfd40e8)
Task 4: Ruling: AST-traversal completeness is whack-a-mole with JSqlParser deparser — add (a) a fail-closed lexical backstop in SqlGuard over deparsed safeSql (blocked fn names before '(', keyword-columns, any pg_*/information_schema token), and (b) a new DB-planner layer in Task 5: PlanInspector runs EXPLAIN (VERBOSE, FORMAT JSON) as analyst_ro and rejects any plan relation whose schema != dw or plan text calling a blocked function — Postgres resolves names/scoping authoritatively — cost if wrong: one extra EXPLAIN round-trip per query (~ms) and an extra component
Task 4: Ruling: add ts_stat/ts_rewrite to blocked family; block keyword-columns current_user/session_user/user/current_role/current_schema/current_catalog — cost: none for analytics
Task 4: Ruling: promote UNION trailing-LIMIT double-LIMIT bug and WITH RECURSIVE multi-item flag into this round (cheap, LLM-common) — cost: none
Task 4: fix round 2 implemented 02aa590 (119/119 guard tests, 142 full suite)
Task 4: fix round 2/5 (items 1-5,7-9 addressed; open: keyword columns via $$ desync, backstop not fail-closed; commits bfd40e8..02aa590)
Task 4: Ruling: block any '$' outside '…' literals (dollar-quoting unnecessary for analytics), lexer fails closed on unterminated strings, E'' handled — cost if wrong: LLM query using $$ blocked (rare)
Task 4: Ruling: keyword columns enforced in AST Column visitor too (+system_user); reject TABLE statements; block getpgusername/current_database/current_schemas/inet_client_addr/inet_client_port/current_query — cost: none for analytics
Task 4: Ruling: promote deferred LIMIT+FETCH minor — convert outermost FETCH FIRST n to LIMIT min(n,1000) — cost: none
Task 4: minor (deferred): 'x'::regclass existence probing allowed (layers 2/3 handle)
Task 4: fix round 3 implemented 2dfcad5 (164 full suite green)
Task 4: fix round 3/5 (items 1-5 addressed statically; reviewer couldn't probe; commits 78dab1c..2dfcad5)
Task 4: controller live probe at 2dfcad5: FETCH FIRST ROWS ONLY → NPE escapes check() (Important); nested TABLE forms blocked via parse error (OK)
Task 4: note: controller's taskkill while killing a hung probe JVM may have killed other java.exe processes (IDE language server) — told user
Task 4: fix round 4 implemented 40353b1 (fresh opus implementer; 171 full suite green)
Task 4: fix round 4/5 (4 addressed, 0 open; commits 2dfcad5..40353b1)
Task 4: minor (deferred): check() fail-closed path logs nothing — add debug/warn log of exception for diagnosability
Task 4: complete (commits c1c5897..40353b1, review clean)
Task 5: note: no RED captured (TDD deviation, recurring with sonnet implementers); trailer Sonnet
Task 5: review: 3 Important (timestamptz → LocalDateTime crash; quote in "a'b" identifier desyncs stripLiterals → layer-2 evasion; keyword session functions w/o parens invisible to layer 2)
Task 5: minor (deferred): PlanInspector fails open on any RuntimeException incl. connection errors (plan-mandated empty-on-error; narrow to SQL-error states); readTree outside try; planner pruning hides relations (WHERE false) — comment; guard function list lacks has_column_privilege/to_reg*/obj_description, regclass OID probing; per-cell getColumnType; LocalDateTime precision varies
Task 5: fix round 1/5 (3 addressed, 0 open; commits dbd297b..01537b2)
Task 5: minor (deferred): keyword rule may over-block a table/CTE aliased "user" (fails closed); system_user lacks test; time/timetz map to text
Task 5: complete (commits 40353b1..01537b2, review clean)
Task 6: minor (deferred): two time-like numeric columns (year+month) → LINE with meaningless y; PIE hint substring match; boundary tests missing; time-like text column LINE regardless of row count
Task 6: complete (commits 01537b2..0cff2bf, review clean)
Task 7: note: added spring-boot-starter-restclient (Boot 4 needs it for RestClient.Builder); no RED captured
Task 7: review: 1 Important (SimpleClientHttpRequestFactory/HttpURLConnection may turn 401+WWW-Authenticate into HttpRetryException → wrong "không phản hồi" message)
Task 7: minor (deferred): 501/505 retried; LlmException cause may carry provider body if logged by a future caller
Task 7: fix round 1/5 (2 addressed, 0 open; real timeout bug found by real-server test; commits d1daf8e..35a7b3a)
Task 7: complete (commits 0cff2bf..35a7b3a, review clean)
Task 8: note: no RED captured; trailer Sonnet
Task 8: review: 1 Important (top tracks grouped by non-unique track_name; employees by full_name)
Task 8: Ruling: also fold minor #1 (LIMIT 1/top-N tiebreakers) and #2 (ambiguous "mua nhiều nhất" wording) into the fix — same YAML file, few-shot patterns get copied by the LLM — cost: none
Task 8: minor (deferred): avg-duration example over catalog not sales; NULL genre group; lexical Jaccard VI/EN mismatch; ranking test weak; YAMLMapper per read
Task 8: fix round 1/5 (3 addressed, 0 open; commits 0d2d376..6791179)
Task 8: minor (deferred): customer/employee tiebreakers on full_name not key; "best selling" ranked by revenue; NULL genre may win top-1
Task 8: complete (commits 35a7b3a..6791179, review clean)
Task 9: note: no RED captured; trailer Sonnet; extra SqlExtractor.refuseReason
Task 9: review: Approved, 0 Critical/Important, 9 Minor
Task 9: Ruling: promote Minor #3 (non-LLM RuntimeException escapes run() → would be HTTP 500, violates Global Constraint/Review Focus #2), #1 (single-line fence mangles SQL), #2 (unclosed fence), #4 (don't repair statement-timeout/connection errors; user message in Vietnamese) into a fix round — they're load-bearing for Task 11/12/eval accuracy — cost: one small fix round
Task 9: minor (deferred): rewrite output cleanup (quotes/"Câu hỏi:" prefix) + data-not-instructions note; REFUSE first-line/case-insensitive/length cap; missing tests for fix-attempt BLOCKED/policy/LLM-fail-in-repair; "OK" message English; trailer Sonnet
Task 9: fix round 1/5 (4 promoted items addressed, 0 open; commits 6a5c2af..71b2599)
Task 9: minor (deferred): no negative test that 42703 still repairs; no log-line assertions; "```sql SELECT" (tag+space, no newline) keeps "sql"
Task 9: complete (commits 6791179..71b2599, review clean)
Task 10: note: added 72-byte BCrypt limit check on register (400); fail-fast secret untested; no full RED
Task 10: review: 2 Important (catch-all maps 404/405/415/ResponseStatusException to 500 — breaks required 404 for foreign conversations; validTokenPassesSecurity asserts only !=401)
Task 10: Ruling: promote Minor #1 (stale Bearer on /api/auth/** → 401 blocks re-login), #3 (generic DuplicateKey → misleading 409), #4 (8-space password registrable but can't log in) into fix round — cheap, load-bearing for Task 11/13 — cost: none
Task 10: minor (deferred): /error denyAll; weak tampered-signature test; analyst test accepts any DataAccessException; 100 chars vs 72 bytes messages; case-sensitive usernames
Task 10: fix round 1/5 (5 addressed, 0 open; commits aa72487..0e0c38d)
Task 10: minor (deferred): 405 response drops Allow header; 406 body; generic 4xx message
Task 10: complete (commits 71b2599..0e0c38d, review clean)
Task 11: implemented fac2f44 (279 suite green; no RED — rate-limit interruption)
Task 11: review: 3 Important (malformed UUID path → 500; JSONB read-back numbers become Double — scale/precision drift vs live turn; turn_index race on concurrent asks)
Task 11: minor (deferred): orphan conversation if persistence fails (covered by FOR UPDATE fix); re-read all turns per ask (use RETURNING); title substring may split surrogate pair; FQCN in handler; private JsonMapper vs Spring mapper
Task 11: fix round 1/5 (3 addressed, 0 open; commits fac2f44..e0c16db); controller mutation run confirmed fidelity test goes RED without USE_BIG_DECIMAL_FOR_FLOATS
Task 11: minor (deferred): MethodArgumentTypeMismatch handler global message "Không tìm thấy hội thoại"; V2 migration fails on pre-existing duplicates
Task 11: complete (commits 0e0c38d..e0c16db, review clean)
PAUSED by user request after Task 11 (2026-10-22). Next: Task 12 (eval set). Note: working tree has an uncommitted .gitignore edit removing `.superpowers/` (not made by controller/agents) — user informed.
