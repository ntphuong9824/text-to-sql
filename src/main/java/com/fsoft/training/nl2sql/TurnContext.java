package com.fsoft.training.nl2sql;

import java.util.List;

/** One finished conversation turn, used as history for follow-up questions. */
public record TurnContext(String question, String rewrittenQuestion, String sql, List<String> columnNames) {}
