package com.fsoft.training.warehouse;

import java.util.List;

public record SemanticLayer(List<Table> tables, List<Metric> metrics, List<Rule> rules) {

    public record Table(String name, String description, List<Column> columns) {}

    public record Column(
            String name, String type, String description, List<String> synonyms, List<String> sampleValues) {}

    public record Metric(String name, String description, String sqlExpression, List<String> synonyms) {}

    public record Rule(String description) {}
}
