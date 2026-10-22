# Task 9 report
Implemented SqlExtractor (+refuseReason), PromptBuilder, QuestionRewriter, Nl2SqlPipeline, TurnContext/TurnOutcome/TurnStatus in com.fsoft.training.nl2sql, with SqlExtractorTest (6), PromptBuilderTest (4), Nl2SqlPipelineTest (7).
Handles SqlPolicyViolationException as BLOCKED (no repair); guard-blocked fix output also stops. INFO log line per turn.
Tests: `./mvnw test` -> 250 run, 0 failures, BUILD SUCCESS.
TDD note: tests and implementation were written in the same step; a separate RED run was not captured (first run was GREEN).
Commit message trailer uses Claude Sonnet 5.5 per the harness attribution reminder (contract text says Opus 5.5).

## Fix round
Changes: (1) catch RuntimeException in Nl2SqlPipeline.run -> ERROR generic Vietnamese message, WARN with exception, INFO line still written. (2) SqlExtractor fence regex only treats a tag as language tag when a newline follows (single-line and `sql-postgres` cases). (3) Unclosed/truncated fence stripped. (4) Timeout (SQLState 57014 or Spring QueryTimeoutException) and connection failures (SQLState class 08, CannotGetJdbcConnectionException, TransientDataAccessResourceException) found by walking the SqlExecutionException cause chain (cause already kept; no warehouse change) -> ERROR with Vietnamese message, no repair.
Tests: SqlExtractorTest +3; new Nl2SqlPipelineRobustnessTest (4, Mockito collaborators, simulated causes: chosen for speed/reliability over a real 10s query).
RED: `./mvnw -q test -Dtest='SqlExtractorTest,Nl2SqlPipelineRobustnessTest'` -> 4 failures + 3 errors (extractor cases initially failed/uncompilable due to a quoting slip in my test edit, later fixed). GREEN: `./mvnw test` -> 257 run, 0 failures, BUILD SUCCESS.
