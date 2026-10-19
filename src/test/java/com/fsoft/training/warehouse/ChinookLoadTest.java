package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

class ChinookLoadTest extends PostgresIntegrationTest {

    @Autowired
    ApplicationContext ctx;

    @Test
    void chinookLoaded() {
        assertThat(superuserJdbc().queryForObject("select count(*) from chinook.invoice", Long.class)).isEqualTo(412L);
        assertThat(superuserJdbc().queryForObject("select count(*) from chinook.invoice_line", Long.class)).isEqualTo(2240L);
        assertThat(superuserJdbc().queryForObject("select sum(total) from chinook.invoice", BigDecimal.class))
                .isEqualByComparingTo("2328.60");
    }

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    JdbcTemplate defaultJdbcTemplate;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("analyst")
    JdbcTemplate analystJdbcTemplate;

    @Test
    void defaultBeansRunAsAppRwAndAnalystAsAnalystRo() {
        assertThat(jdbcClient.sql("select current_user").query(String.class).single()).isEqualTo("app_rw");
        assertThat(defaultJdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo("app_rw");
        assertThat(analystJdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo("analyst_ro");
    }

    @Test
    void contextLoads() {
        assertThat(ctx.getBean("analystJdbcTemplate")).isNotNull();
    }
}
