package com.fsoft.training.nl2sql;

import com.fsoft.training.chart.ChartSuggester;
import com.fsoft.training.guardrail.GuardResult;
import com.fsoft.training.guardrail.SqlGuard;
import com.fsoft.training.llm.ChatMessage;
import com.fsoft.training.llm.LlmClient;
import com.fsoft.training.llm.LlmException;
import com.fsoft.training.warehouse.QueryResult;
import com.fsoft.training.warehouse.SqlExecutionException;
import com.fsoft.training.warehouse.SqlExecutor;
import com.fsoft.training.warehouse.SqlPolicyViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Nl2SqlPipeline {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlPipeline.class);
    static final int MAX_REPAIRS = 2;
    static final int SHOTS = 4;

    private final QuestionRewriter rewriter;
    private final FewShotSelector shotSelector;
    private final PromptBuilder prompts;
    private final LlmClient llm;
    private final SqlGuard guard;
    private final SqlExecutor executor;
    private final ChartSuggester chartSuggester;

    public Nl2SqlPipeline(QuestionRewriter rewriter, FewShotSelector shotSelector, PromptBuilder prompts,
                          LlmClient llm, SqlGuard guard, SqlExecutor executor, ChartSuggester chartSuggester) {
        this.rewriter = rewriter;
        this.shotSelector = shotSelector;
        this.prompts = prompts;
        this.llm = llm;
        this.guard = guard;
        this.executor = executor;
        this.chartSuggester = chartSuggester;
    }

    /** {@code history}: up to the 3 most recent turns, oldest first. */
    public TurnOutcome run(String question, List<TurnContext> history) {
        long start = System.nanoTime();
        String rewritten = question;
        String sql = null;
        String guardDecision = "n/a";
        int repairs = 0;
        TurnOutcome outcome;
        try {
            rewritten = rewriter.rewrite(question, history);
            TurnContext previous = history == null || history.isEmpty() ? null : history.get(history.size() - 1);
            List<ChatMessage> messages =
                    prompts.buildGenerate(rewritten, shotSelector.select(rewritten, SHOTS), previous);
            String raw = llm.complete(messages);
            while (true) {
                String refusal = SqlExtractor.refuseReason(raw);
                if (refusal != null) {
                    guardDecision = "refused";
                    outcome = blocked(rewritten, null, refusal);
                    break;
                }
                String extracted = SqlExtractor.extract(raw).orElse("");
                sql = extracted;
                GuardResult g = guard.check(extracted);
                if (!g.allowed()) {
                    guardDecision = "blocked";
                    outcome = blocked(rewritten, extracted, g.reason());
                    break;
                }
                guardDecision = "allowed";
                sql = g.safeSql();
                try {
                    QueryResult result = executor.execute(sql);
                    outcome = new TurnOutcome(rewritten, sql, TurnStatus.OK, "OK", result,
                            chartSuggester.suggest(result));
                    break;
                } catch (SqlPolicyViolationException e) {
                    guardDecision = "policy-blocked";
                    outcome = blocked(rewritten, sql, e.getMessage());
                    break;
                } catch (SqlExecutionException e) {
                    if (repairs >= MAX_REPAIRS) {
                        outcome = new TurnOutcome(rewritten, sql, TurnStatus.ERROR,
                                "Không chạy được câu SQL sau " + MAX_REPAIRS + " lần sửa: " + e.getMessage(),
                                null, null);
                        break;
                    }
                    repairs++;
                    messages = prompts.buildFix(messages, sql, e.getMessage());
                    raw = llm.complete(messages);
                }
            }
        } catch (LlmException e) {
            outcome = new TurnOutcome(rewritten, sql, TurnStatus.ERROR, e.userMessage(), null, null);
        }
        log.info("nl2sql turn: question=\"{}\" rewritten=\"{}\" sql=\"{}\" guard={} status={} repairs={} elapsedMs={}",
                oneLine(question), oneLine(rewritten), oneLine(outcome.sql()), guardDecision, outcome.status(),
                repairs, (System.nanoTime() - start) / 1_000_000);
        return outcome;
    }

    private static TurnOutcome blocked(String rewritten, String sql, String reason) {
        return new TurnOutcome(rewritten, sql, TurnStatus.BLOCKED, reason, null, null);
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ");
    }
}
