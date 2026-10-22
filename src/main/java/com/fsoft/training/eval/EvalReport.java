package com.fsoft.training.eval;

import com.fsoft.training.nl2sql.TurnStatus;
import java.util.List;

public record EvalReport(List<CaseResult> cases) {

    public static final String SINGLE = "single";
    public static final String CHAIN = "chain";
    public static final String ATTACK = "attack";

    /**
     * One scored case. Chain turns have id {@code <chainId>/t<index>}; {@code difficulty} and {@code lang}
     * are null for chain turns and attacks.
     *
     * @param kind single | chain | attack
     */
    public record CaseResult(String id, String kind, String difficulty, String lang, String question,
                             String rewrittenQuestion, String goldSql, String generatedSql, TurnStatus status,
                             boolean passed, String note) {

        /** A chain turn after the first one (index ≥ 1), i.e. one that needs conversation context. */
        public boolean isFollowUp() {
            return CHAIN.equals(kind) && !id.endsWith("/t0");
        }
    }
}
