package com.fsoft.training.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FewShotSelectorTest {

    private final FewShotSelector selector = new FewShotSelector();

    @Test
    void regionQuestionPicksRegionExample() {
        List<FewShotExample> top = selector.select("doanh thu theo khu vực", 3);
        assertThat(top).hasSize(3);
        assertThat(top).anyMatch(e -> e.sql().contains("c.region AS region"));
    }

    @Test
    void accentAndCaseInsensitive() {
        assertThat(selector.select("DOANH THU THEO KHU VUC", 3))
                .isEqualTo(selector.select("doanh thu theo khu vực", 3));
    }

    @Test
    void emptyQuestionReturnsKExamples() {
        assertThat(selector.select("", 3)).hasSize(3);
    }

    @Test
    void kIsCappedAndDeterministic() {
        int n = selector.all().size();
        assertThat(n).isGreaterThanOrEqualTo(20);
        assertThat(selector.select("top nghệ sĩ", n + 10)).hasSize(n);
        assertThat(selector.select("top nghệ sĩ", 5)).isEqualTo(selector.select("top nghệ sĩ", 5));
    }

    @Test
    void normalizeStripsDiacritics() {
        assertThat(TextNormalizer.normalize("Doanh thu Đông Nam Á")).isEqualTo("doanh thu dong nam a");
        assertThat(TextNormalizer.normalize("  Hello,   World! ")).isEqualTo("hello world");
    }

    @Test
    void tokensDropStopwordsAndAddBigrams() {
        assertThat(TextNormalizer.tokens("doanh thu của khu vực"))
                .contains("doanh", "thu", "khu", "vuc", "_doanh thu")
                .doesNotContain("cua");
    }
}
