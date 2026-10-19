package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PlanInspectorTest extends PostgresIntegrationTest {

    @Autowired
    PlanInspector inspector;

    @Test
    void pgUserViewIsCaughtAsPgCatalog() {
        assertThat(inspector.violation("SELECT * FROM pg_user"))
                .hasValueSatisfying(r -> assertThat(r).contains("pg_catalog"));
    }

    @Test
    void cteShadowingDoesNotHidePgUser() {
        assertThat(inspector.violation("WITH \"PG_USER\" AS (SELECT 1) SELECT * FROM pg_user")).isPresent();
    }

    @Test
    void crossJoinInParensIsCaught() {
        assertThat(inspector.violation("SELECT * FROM (dw.dim_date d CROSS JOIN pg_user)")).isPresent();
    }

    @Test
    void blockedFunctionInFilterIsCaught() {
        assertThat(inspector.violation("SELECT 1 FROM dw.dim_date WHERE pg_sleep(0) IS NULL")).isPresent();
    }

    @Test
    void literalNamedLikeBlockedFunctionIsNotAFalsePositive() {
        assertThat(inspector.violation("SELECT 1 FROM dw.dim_date WHERE month_name = 'pg_sleep(1)'")).isEmpty();
    }

    @Test
    void normalAggregateIsAllowed() {
        assertThat(inspector.violation("SELECT region, sum(revenue) FROM dw.fact_sales f "
                + "JOIN dw.dim_customer c USING (customer_key) GROUP BY region")).isEmpty();
    }

    @Test
    void cteAndWindowFunctionAreAllowed() {
        assertThat(inspector.violation("WITH t AS (SELECT date_key, sum(revenue) r FROM dw.fact_sales GROUP BY date_key) "
                + "SELECT date_key, r, rank() OVER (ORDER BY r DESC) FROM t")).isEmpty();
    }

    @Test
    void explainFailureReturnsEmpty() {
        assertThat(inspector.violation("SELECT * FROM chinook.album")).isEmpty();
        assertThat(inspector.violation("SELECT nope FROM dw.dim_date")).isEmpty();
    }
}
