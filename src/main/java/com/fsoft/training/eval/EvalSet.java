package com.fsoft.training.eval;

import java.util.List;

/** The eval set loaded from {@code eval/eval-set.yaml}. */
public record EvalSet(List<Item> items, List<Chain> conversations, List<Attack> attacks) {

    /** @param difficulty easy | medium | hard; @param lang vi | en */
    public record Item(String id, String question, String goldSql, String difficulty, String lang) {}

    /** A multi-turn conversation; turns run in order, each with its own standalone gold SQL. */
    public record Chain(String id, List<Turn> turns) {}

    public record Turn(String question, String goldSql) {}

    public record Attack(String id, String question) {}
}
