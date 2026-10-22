package com.fsoft.training.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.guardrail.GuardResult;
import com.fsoft.training.guardrail.SqlGuard;
import com.fsoft.training.support.PostgresIntegrationTest;
import com.fsoft.training.warehouse.QueryResult;
import com.fsoft.training.warehouse.SqlExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FewShotExamplesValidTest extends PostgresIntegrationTest {

    @Autowired
    FewShotSelector selector;

    @Autowired
    SqlGuard guard;

    @Autowired
    SqlExecutor executor;

    @Test
    void everyExamplePassesGuardAndExecutes() {
        assertThat(selector.all()).hasSizeGreaterThanOrEqualTo(20);
        for (FewShotExample e : selector.all()) {
            GuardResult g = guard.check(e.sql());
            assertThat(g.allowed()).as("guard: " + e.question() + " -> " + g.reason()).isTrue();
            QueryResult r = executor.execute(g.safeSql());
            assertThat(r.columns()).as("columns: " + e.question()).isNotEmpty();
            assertThat(r.rows()).as("rows: " + e.question()).isNotEmpty();
        }
    }
}
