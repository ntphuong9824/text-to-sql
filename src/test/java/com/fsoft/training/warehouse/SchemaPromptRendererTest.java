package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchemaPromptRendererTest {

    private final String out = new SchemaPromptRenderer(new SemanticLayerLoader()).render();

    @Test
    void containsTablesColumnsAndMetrics() {
        assertThat(out).contains("dw.fact_sales", "revenue", "SUM(f.revenue)", "COUNT(DISTINCT f.invoice_id)");
    }

    @Test
    void containsSynonymsAndSampleValues() {
        assertThat(out).contains("doanh thu", "North America", "Bắc Mỹ");
    }

    @Test
    void containsRules() {
        assertThat(out).contains("Câu hỏi không nêu năm thì gộp mọi năm", "Chỉ dùng schema dw");
    }
}
