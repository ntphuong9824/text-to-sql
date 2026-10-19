package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class WarehouseBuildTest extends PostgresIntegrationTest {

    private Long count(String sql) {
        return superuserJdbc().queryForObject(sql, Long.class);
    }

    @Test
    void factSalesHasAllInvoiceLines() {
        assertThat(count("select count(*) from dw.fact_sales")).isEqualTo(2240L);
    }

    @Test
    void revenueMatchesInvoiceTotals() {
        assertThat(superuserJdbc().queryForObject("select sum(revenue) from dw.fact_sales", BigDecimal.class))
                .isEqualByComparingTo("2328.60");
    }

    @Test
    void everyCustomerHasRegionAndThereAreFourRegions() {
        assertThat(count("select count(*) from dw.dim_customer where region is null")).isZero();
        assertThat(count("select count(distinct region) from dw.dim_customer")).isEqualTo(4L);
    }

    @Test
    void everyFactDateExistsInDimDate() {
        assertThat(count("select count(*) from dw.fact_sales f left join dw.dim_date d on d.date_key = f.date_key"
                + " where d.date_key is null")).isZero();
    }

    @Test
    void scriptCanBeRerunBySuperuser() throws Exception {
        try (Connection c = superuserJdbc().getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource(Path.of("db/init/02_build_dw.sql")));
        }
        assertThat(count("select count(*) from dw.fact_sales")).isEqualTo(2240L);
        assertThat(count("select count(*) from dw.dim_customer")).isEqualTo(59L);
        // grants are re-applied after DROP SCHEMA CASCADE
        assertThat(count("select count(*) from information_schema.table_privileges"
                + " where grantee = 'analyst_ro' and table_schema = 'dw' and privilege_type = 'SELECT'"))
                .isGreaterThanOrEqualTo(6L);
    }
}
