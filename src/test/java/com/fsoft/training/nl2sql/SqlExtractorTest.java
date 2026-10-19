package com.fsoft.training.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SqlExtractorTest {

    @Test
    void stripsSqlFenceAndSemicolon() {
        assertThat(SqlExtractor.extract("```sql\nSELECT 1 FROM dw.dim_date;\n```"))
                .contains("SELECT 1 FROM dw.dim_date");
    }

    @Test
    void takesOnlyFencedPartFromProse() {
        assertThat(SqlExtractor.extract("Đây là câu SQL:\n```\nSELECT a FROM dw.x\n```\nGiải thích..."))
                .contains("SELECT a FROM dw.x");
    }

    @Test
    void dropsTrailingSemicolon() {
        assertThat(SqlExtractor.extract("SELECT a FROM dw.x;")).contains("SELECT a FROM dw.x");
    }

    @Test
    void refuseReturnsEmpty() {
        assertThat(SqlExtractor.extract("  -- REFUSE: không được xóa dữ liệu")).isEqualTo(Optional.empty());
        assertThat(SqlExtractor.refuseReason("  -- REFUSE: không được xóa dữ liệu"))
                .isEqualTo("không được xóa dữ liệu");
    }

    @Test
    void plainTextPassesThroughForGuardToBlock() {
        assertThat(SqlExtractor.extract("Xin lỗi")).contains("Xin lỗi");
    }

    @Test
    void cteIsKept() {
        String sql = "WITH t AS (SELECT 1 AS a) SELECT a FROM t";
        assertThat(SqlExtractor.extract(sql)).contains(sql);
    }
}
