package com.fsoft.training.nl2sql;

import com.fsoft.training.chart.ChartSpec;
import com.fsoft.training.warehouse.QueryResult;

/** {@code result} and {@code chart} are null unless status is OK. {@code message} is Vietnamese. */
public record TurnOutcome(
        String rewrittenQuestion,
        String sql,
        TurnStatus status,
        String message,
        QueryResult result,
        ChartSpec chart) {}
