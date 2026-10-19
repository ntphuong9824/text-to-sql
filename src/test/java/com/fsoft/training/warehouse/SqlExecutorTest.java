package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fsoft.training.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class SqlExecutorTest extends PostgresIntegrationTest {

    @Autowired
    SqlExecutor executor;

    private static final String BY_REGION = "select region, sum(revenue) as revenue from dw.fact_sales f "
            + "join dw.dim_customer c on c.customer_key=f.customer_key group by region";

    @Test
    void groupByRegionHasFourRowsAndNumericSecondColumn() {
        QueryResult r = executor.execute(BY_REGION);
        assertThat(r.rows()).hasSize(4);
        assertThat(r.columns().get(1).type()).isEqualTo("number");
        assertThat(r.columns().get(0).type()).isEqualTo("text");
    }

    @Test
    void zeroRowsKeepsColumns() {
        QueryResult r = executor.execute("select * from dw.dim_date where 1=0");
        assertThat(r.rows()).isEmpty();
        assertThat(r.columns()).hasSize(7);
        assertThat(r.truncated()).isFalse();
    }

    @Test
    void dateColumnIsIsoString() {
        QueryResult r = executor.execute("select full_date from dw.dim_date order by full_date limit 1");
        assertThat(r.columns().get(0).type()).isEqualTo("date");
        assertThat((String) r.rows().get(0).get(0)).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void nullStaysNull() {
        QueryResult r = executor.execute("select null::text as a, 1 as b");
        assertThat(r.rows().get(0).get(0)).isNull();
        assertThat(r.rows().get(0).get(1)).isEqualTo(1L);
    }

    @Test
    void truncationDependsOnLimit() {
        QueryResult limited = executor.execute("select * from dw.fact_sales limit 1000");
        assertThat(limited.rows()).hasSize(1000);
        assertThat(limited.truncated()).isFalse();
        QueryResult unlimited = executor.execute("select * from dw.fact_sales");
        assertThat(unlimited.rows()).hasSize(1000);
        assertThat(unlimited.truncated()).isTrue();
    }

    @Test
    void unknownColumnThrowsWithPostgresMessage() {
        assertThatThrownBy(() -> executor.execute("select nope from dw.dim_date"))
                .isInstanceOf(SqlExecutionException.class)
                .hasMessageContaining("column");
    }

    @Test
    void policyViolationIsNotAnExecutionException() {
        assertThatThrownBy(() -> executor.execute("SELECT * FROM pg_user"))
                .isInstanceOf(SqlPolicyViolationException.class)
                .isNotInstanceOf(SqlExecutionException.class);
    }

    @Test
    void resultSerializesToJson() {
        String json = JsonMapper.builder().build().writeValueAsString(executor.execute(BY_REGION));
        assertThat(json).contains("\"columns\"").contains("\"truncated\":false");
    }
}
