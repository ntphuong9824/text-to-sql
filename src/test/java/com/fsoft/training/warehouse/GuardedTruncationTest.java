package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.guardrail.GuardResult;
import com.fsoft.training.guardrail.SqlGuard;
import com.fsoft.training.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Guard and executor together: the truncation flag must be reachable in the real pipeline. */
class GuardedTruncationTest extends PostgresIntegrationTest {

    @Autowired
    SqlGuard guard;

    @Autowired
    SqlExecutor executor;

    private QueryResult run(String sql) {
        GuardResult g = guard.check(sql);
        assertThat(g.allowed()).as(g.reason()).isTrue();
        return executor.execute(g.safeSql());
    }

    @Test
    void unlimitedQueryIsTruncatedTo1000Rows() {
        QueryResult r = run("SELECT * FROM dw.fact_sales");
        assertThat(r.rows()).hasSize(1000);
        assertThat(r.truncated()).isTrue();
    }

    @Test
    void explicitSmallLimitIsNotTruncated() {
        QueryResult r = run("SELECT * FROM dw.fact_sales LIMIT 5");
        assertThat(r.rows()).hasSize(5);
        assertThat(r.truncated()).isFalse();
    }
}
