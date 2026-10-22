# Implementer contract (applies to every task)

- Repo: D:\Project FSoft\text-to-sql, branch feat/text-to-sql. Windows + Git Bash, Docker Desktop running, Java 21 (./mvnw), Node 24.
- Read your brief and D:\Project FSoft\text-to-sql\.superpowers\sdd\2026-10-19-text-to-sql\global-constraints.md first. The brief is your requirements; use its exact values verbatim.
- If anything is unclear, ask before starting (report NEEDS_CONTEXT). Don't guess on things that matter.
- Implement exactly what the brief specifies (YAGNI), TDD where it says so: write the test, run it and see it fail (RED), implement, see it pass (GREEN).
- While iterating, run the focused test; run the full `./mvnw test` once before committing (frontend tasks: `npm --prefix frontend test -- --run` and build).
- Commit your work; end every commit message with the line: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never commit `.superpowers/`, `.env`, or secrets.
- Known environment quirks: the JDK reports timezone Asia/Saigon; tests rely on the surefire `-Duser.timezone=Asia/Ho_Chi_Minh` flag already in pom.xml. Flyway "No migrations found" warning is expected until Task 10.
- You do not dispatch subagents — not helpers, not reviewers. Review is done by the controller afterwards.
- Follow the plan's file structure; one responsibility per file; don't restructure beyond your task. If a file grows beyond intent, report DONE_WITH_CONCERNS.
- If you're in over your head (architectural choice, can't find clarity), stop and report BLOCKED / NEEDS_CONTEXT with specifics.
- Self-review your diff before reporting: completeness, quality, naming, YAGNI, tests verify real behavior, pristine output.
- Write the full report to your report file: what you implemented, tests + results, TDD evidence (RED command + failing output, GREEN command + passing output), files changed, self-review findings, concerns.
- If resumed with review findings: fix, re-run the covering tests, append a fix report (changes, tests, command, output) to the same report file, commit, reply with the short contract.
- Final reply (under 15 lines): Status (DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT), commits (short SHA + subject), one-line test summary, concerns, report path.
