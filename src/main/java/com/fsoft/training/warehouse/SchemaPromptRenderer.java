package com.fsoft.training.warehouse;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Renders the semantic layer as compact text for the LLM prompt. */
@Component
public class SchemaPromptRenderer {

    private final SemanticLayerLoader loader;

    public SchemaPromptRenderer(SemanticLayerLoader loader) {
        this.loader = loader;
    }

    public String render() {
        SemanticLayer layer = loader.load();
        StringBuilder sb = new StringBuilder("Tables:\n");
        for (SemanticLayer.Table t : layer.tables()) {
            sb.append("dw.").append(t.name()).append('(')
                    .append(t.columns().stream().map(this::column).collect(Collectors.joining(", ")))
                    .append(") -- ").append(t.description()).append('\n');
        }
        sb.append("\nMetrics:\n");
        for (SemanticLayer.Metric m : layer.metrics()) {
            sb.append("- ").append(m.name()).append(" = ").append(m.sqlExpression())
                    .append(" -- ").append(m.description());
            if (!isEmpty(m.synonyms())) {
                sb.append("; đồng nghĩa: ").append(String.join(", ", m.synonyms()));
            }
            sb.append('\n');
        }
        sb.append("\nRules:\n");
        for (SemanticLayer.Rule r : layer.rules()) {
            sb.append("- ").append(r.description()).append('\n');
        }
        return sb.toString();
    }

    private String column(SemanticLayer.Column c) {
        StringBuilder sb = new StringBuilder(c.name()).append(' ').append(c.type())
                .append(" -- ").append(c.description());
        if (!isEmpty(c.synonyms())) {
            sb.append("; đồng nghĩa: ").append(String.join(" | ", c.synonyms()));
        }
        if (!isEmpty(c.sampleValues())) {
            sb.append("; ví dụ: ").append(String.join(" | ", c.sampleValues()));
        }
        return sb.toString();
    }

    private static boolean isEmpty(List<String> l) {
        return l == null || l.isEmpty();
    }
}
