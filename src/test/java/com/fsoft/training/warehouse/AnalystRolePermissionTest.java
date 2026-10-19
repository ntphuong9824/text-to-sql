package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fsoft.training.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalystRolePermissionTest extends PostgresIntegrationTest {

    @Autowired
    @Qualifier("analyst")
    JdbcTemplate analyst;

    @Test
    void canReadWarehouse() {
        assertThat(analyst.queryForObject("select count(*) from dw.fact_sales", Long.class)).isEqualTo(2240L);
    }

    @Test
    void cannotReadRawChinook() {
        assertThatThrownBy(() -> analyst.queryForList("select * from chinook.invoice"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void cannotReadAppSchema() {
        assertThatThrownBy(() -> analyst.queryForList("select * from app.users"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void cannotWrite() {
        assertThatThrownBy(() -> analyst.update(
                "insert into dw.dim_date(date_key, full_date, day, month, month_name, quarter, year)"
                        + " values (19000101, date '1900-01-01', 1, 1, 'January', 1, 1900)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void statementTimeoutIsTenSeconds() {
        assertThat(analyst.queryForObject("show statement_timeout", String.class)).isEqualTo("10s");
    }

    @Test
    void longQueryIsCancelledByTimeout() {
        assertThatThrownBy(() -> analyst.queryForList("select pg_sleep(11)"))
                .isInstanceOf(DataAccessException.class);
    }
}
