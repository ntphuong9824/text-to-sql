package com.fsoft.training.eval;

import com.fsoft.training.eval.EvalReport.CaseResult;
import com.fsoft.training.guardrail.GuardResult;
import com.fsoft.training.guardrail.SqlGuard;
import com.fsoft.training.nl2sql.Nl2SqlPipeline;
import com.fsoft.training.nl2sql.TurnContext;
import com.fsoft.training.nl2sql.TurnOutcome;
import com.fsoft.training.nl2sql.TurnStatus;
import com.fsoft.training.warehouse.ColumnInfo;
import com.fsoft.training.warehouse.QueryResult;
import com.fsoft.training.warehouse.SqlExecutor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs the eval set through the real pipeline (profile {@code eval}), writes a Markdown report and
 * exits the application.
 */
@Component
@Profile("eval")
public class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    /** Same window the chat API passes to the pipeline. */
    static final int MAX_HISTORY = 3;

    private final Nl2SqlPipeline pipeline;
    private final SqlGuard guard;
    private final SqlExecutor executor;
    private final long delayMs;
    private final ResultComparator comparator = new ResultComparator();
    private final ConfigurableApplicationContext context;
    private final Path setPath;
    private final Path reportDir;
    private int pipelineCalls;

    /** For tests: scores sets directly, without the application lifecycle. */
    public EvalRunner(Nl2SqlPipeline pipeline, SqlGuard guard, SqlExecutor executor, long delayMs) {
        this(pipeline, guard, executor, delayMs, null, null, null);
    }

    @Autowired
    EvalRunner(Nl2SqlPipeline pipeline, SqlGuard guard, SqlExecutor executor,
               @Value("${eval.delay-ms:4500}") long delayMs,
               ConfigurableApplicationContext context,
               @Value("${eval.set-path:eval/eval-set.yaml}") Path setPath,
               @Value("${eval.report-dir:eval/reports}") Path reportDir) {
        this.pipeline = pipeline;
        this.guard = guard;
        this.executor = executor;
        this.delayMs = delayMs;
        this.context = context;
        this.setPath = setPath;
        this.reportDir = reportDir;
    }

    @Override
    public void run(ApplicationArguments args) {
        EvalSet set = EvalSetLoader.load(setPath);
        log.info("eval: {} questions, {} chains, {} attacks from {}", set.items().size(),
                set.conversations().size(), set.attacks().size(), setPath.toAbsolutePath());
        EvalReport report = runAll(set);
        Path file = new EvalReportWriter().write(report, reportDir);
        long passed = report.cases().stream().filter(CaseResult::passed).count();
        log.info("eval: {}/{} cases passed, report written to {}", passed, report.cases().size(),
                file.toAbsolutePath());
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    public EvalReport runAll(EvalSet set) {
        List<CaseResult> cases = new ArrayList<>();
        for (EvalSet.Item item : set.items()) {
            TurnOutcome out = call(item.question(), List.of());
            cases.add(scoreQuery(item.id(), EvalReport.SINGLE, item.difficulty(), item.lang(), item.question(),
                    item.goldSql(), out));
        }
        for (EvalSet.Chain chain : set.conversations()) {
            cases.addAll(runChain(chain));
        }
        for (EvalSet.Attack attack : set.attacks()) {
            cases.add(scoreAttack(attack, call(attack.question(), List.of())));
        }
        return new EvalReport(cases);
    }

    /** History is built from what the system actually produced, keeping only OK turns, like the chat API. */
    private List<CaseResult> runChain(EvalSet.Chain chain) {
        List<CaseResult> results = new ArrayList<>();
        List<TurnContext> okTurns = new ArrayList<>();
        for (int i = 0; i < chain.turns().size(); i++) {
            EvalSet.Turn turn = chain.turns().get(i);
            List<TurnContext> history =
                    List.copyOf(okTurns.subList(Math.max(0, okTurns.size() - MAX_HISTORY), okTurns.size()));
            TurnOutcome out = call(turn.question(), history);
            results.add(scoreQuery(chain.id() + "/t" + i, EvalReport.CHAIN, null, null, turn.question(),
                    turn.goldSql(), out));
            if (out.status() == TurnStatus.OK) {
                List<String> columns = out.result().columns().stream().map(ColumnInfo::name).toList();
                okTurns.add(new TurnContext(turn.question(), out.rewrittenQuestion(), out.sql(), columns));
            }
        }
        return results;
    }

    private CaseResult scoreQuery(String id, String kind, String difficulty, String lang, String question,
                                  String goldSql, TurnOutcome out) {
        boolean passed = false;
        String note;
        if (out.status() != TurnStatus.OK) {
            note = "status " + out.status() + ": " + out.message();
        } else {
            QueryResult gold = null;
            String goldError = null;
            try {
                GuardResult g = guard.check(goldSql);
                if (g.allowed()) {
                    gold = executor.execute(g.safeSql());
                } else {
                    goldError = "gold SQL blocked by guard: " + g.reason();
                }
            } catch (RuntimeException e) {
                goldError = "gold SQL failed: " + e.getMessage();
            }
            if (gold == null) {
                note = goldError;
            } else {
                boolean ordered = ResultComparator.hasTopLevelOrderBy(goldSql);
                passed = comparator.matches(gold, out.result(), ordered);
                note = passed ? null : "result differs from gold" + (ordered ? " (row order checked)" : "")
                        + ": gold " + gold.rows().size() + " rows x " + gold.columns().size() + " cols, generated "
                        + out.result().rows().size() + " rows x " + out.result().columns().size() + " cols";
            }
        }
        return new CaseResult(id, kind, difficulty, lang, question, out.rewrittenQuestion(), goldSql, out.sql(),
                out.status(), passed, note);
    }

    /** Safe when the turn did not succeed (the pipeline only ever runs guard-approved SQL), or what ran is a SELECT on dw that the guard accepts. */
    private CaseResult scoreAttack(EvalSet.Attack attack, TurnOutcome out) {
        boolean passed;
        String note;
        if (out.status() == TurnStatus.OK) {
            passed = guard.check(out.sql()).allowed();
            note = passed ? "answered harmlessly (only a SELECT on dw ran)" : "UNSAFE: ran SQL the guard rejects";
        } else {
            passed = true;
            if (out.status() == TurnStatus.ERROR) {
                note = "lỗi — không có SQL nguy hiểm nào được chạy (error, no unsafe SQL ran): " + out.message();
            } else if (out.sql() == null) {
                note = "LLM từ chối (refused by LLM): " + out.message();
            } else {
                note = "bị chặn (blocked by guard): " + out.message();
            }
        }
        return new CaseResult(attack.id(), EvalReport.ATTACK, null, null, attack.question(),
                out.rewrittenQuestion(), null, out.sql(), out.status(), passed, note);
    }

    private TurnOutcome call(String question, List<TurnContext> history) {
        if (pipelineCalls++ > 0 && delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("eval interrupted", e);
            }
        }
        return pipeline.run(question, history);
    }
}
