package com.fsoft.training.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlGuardTest {

    private final SqlGuard guard = new SqlGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE dw.fact_sales",
            "DELETE FROM dw.fact_sales",
            "UPDATE dw.dim_track SET unit_price=0",
            "INSERT INTO dw.dim_date VALUES (1)",
            "TRUNCATE dw.fact_sales",
            "SELECT 1; DROP TABLE dw.fact_sales",
            "SELECT * FROM dw.fact_sales; SELECT 1",
            "SELECT * FROM chinook.invoice",
            "SELECT * FROM app.users",
            "SELECT * FROM pg_catalog.pg_user",
            "SELECT * FROM information_schema.tables",
            "SELECT * FROM fact_sales",
            "SELECT pg_sleep(100)",
            "SELECT PG_READ_FILE('/etc/passwd')",
            "WITH x AS (DELETE FROM dw.fact_sales RETURNING *) SELECT * FROM x",
            "SELECT * INTO dw.copy FROM dw.fact_sales",
            "SELECT * FROM dw.fact_sales WHERE sales_key IN (SELECT id FROM app.users)",
            "/* hi */ DROP TABLE dw.fact_sales",
            "",
            "   ",
            "not sql at all",
            // extra: functions and tables hidden in nested positions
            "SELECT * FROM dw.fact_sales WHERE sales_key = (SELECT length(pg_read_file('/etc/passwd')))",
            "SELECT * FROM dw.fact_sales f JOIN dw.dim_date d ON d.date_key = f.date_key AND pg_sleep(5) IS NULL",
            "WITH t AS (SELECT pg_sleep(5)) SELECT * FROM t",
            "SELECT coalesce(current_setting('server_version'), 'x') FROM dw.dim_date",
            "SELECT pg_catalog.pg_sleep(5)",
            "SELECT \"pg_sleep\"(5)",
            "SELECT * FROM dblink('host=x', 'select 1') AS t(a int)",
            "SELECT * FROM dw.dim_date UNION SELECT * FROM app.users",
            "WITH t AS (SELECT * FROM app.users) SELECT * FROM t",
            "SELECT * FROM (SELECT * FROM chinook.invoice) s",
            "SELECT * FROM dw.dim_date FOR UPDATE",
            "SELECT * FROM mydb.dw.dim_date"
    })
    void blocksUnsafeSql(String sql) {
        GuardResult result = guard.check(sql);
        assertThat(result.allowed()).as(sql).isFalse();
        assertThat(result.safeSql()).isNull();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void reasonNamesOffendingTableAndFunction() {
        assertThat(guard.check("SELECT * FROM app.users").reason()).contains("app.users");
        assertThat(guard.check("SELECT * FROM fact_sales").reason()).contains("fact_sales");
        assertThat(guard.check("SELECT PG_SLEEP(1)").reason()).contains("pg_sleep");
    }

    @Test
    void addsLimitWhenMissing() {
        GuardResult r = guard.check("SELECT count(*) FROM dw.fact_sales");
        assertThat(r.allowed()).isTrue();
        assertThat(r.safeSql()).contains("LIMIT 1000");
    }

    @Test
    void keepsSmallLimit() {
        GuardResult r = guard.check("SELECT * FROM dw.fact_sales LIMIT 5");
        assertThat(r.allowed()).isTrue();
        assertThat(r.safeSql()).contains("LIMIT 5").doesNotContain("LIMIT 1000");
    }

    @Test
    void capsLargeLimit() {
        GuardResult r = guard.check("SELECT * FROM dw.fact_sales LIMIT 50000");
        assertThat(r.allowed()).isTrue();
        assertThat(r.safeSql()).contains("LIMIT 1000").doesNotContain("50000");
    }

    @Test
    void capsLimitAll() {
        GuardResult r = guard.check("SELECT * FROM dw.fact_sales LIMIT ALL");
        assertThat(r.allowed()).isTrue();
        assertThat(r.safeSql()).contains("LIMIT 1000");
    }

    @Test
    void keepsOffsetWhenCapping() {
        GuardResult r = guard.check("SELECT * FROM dw.fact_sales LIMIT 5000 OFFSET 10");
        assertThat(r.allowed()).isTrue();
        assertThat(r.safeSql()).contains("LIMIT 1000").contains("OFFSET 10");
    }

    @Test
    void innerLimitDoesNotCountAsOuterLimit() {
        GuardResult r = guard.check("SELECT * FROM (SELECT * FROM dw.fact_sales LIMIT 5) s");
        assertThat(r.allowed()).isTrue();
        assertThat(r.safeSql()).endsWith("LIMIT 1000");
    }

    @Test
    void allowsCte() {
        GuardResult r = guard.check(
                "WITH t AS (SELECT * FROM dw.fact_sales) SELECT count(*) FROM t");
        assertThat(r.allowed()).as(r.reason()).isTrue();
        assertThat(r.safeSql()).contains("LIMIT 1000");
    }

    @Test
    void allowsStringLiteralThatLooksLikeCommand() {
        GuardResult r = guard.check("SELECT 'DROP TABLE x' AS s FROM dw.dim_date");
        assertThat(r.allowed()).as(r.reason()).isTrue();
    }

    @Test
    void allowsUnionOfDwSelectsAndLimitsOutermost() {
        GuardResult r = guard.check(
                "SELECT date_key FROM dw.dim_date UNION ALL SELECT date_key FROM dw.fact_sales");
        assertThat(r.allowed()).as(r.reason()).isTrue();
        assertThat(r.safeSql()).endsWith("LIMIT 1000");
    }

    @Test
    void allowsTrailingSemicolon() {
        GuardResult r = guard.check("SELECT 1 FROM dw.dim_date;");
        assertThat(r.allowed()).as(r.reason()).isTrue();
    }

    @Test
    void allowsCommonPostgresSyntax() {
        String[] queries = {
                "SELECT EXTRACT(MONTH FROM d.full_date) AS m, count(*) FROM dw.dim_date d GROUP BY 1",
                "SELECT SUM(f.total)::numeric AS t FROM dw.fact_sales f",
                "SELECT * FROM dw.dim_track WHERE name ILIKE '%rock%'",
                "SELECT track_key, RANK() OVER (PARTITION BY genre_key ORDER BY unit_price DESC) AS r "
                        + "FROM dw.dim_track",
                "SELECT count(*) FILTER (WHERE quantity > 1) AS c FROM dw.fact_sales",
                "SELECT DATE_TRUNC('month', d.full_date) AS m, count(*) FROM dw.dim_date d GROUP BY 1 ORDER BY 1"
        };
        for (String q : queries) {
            GuardResult r = guard.check(q);
            assertThat(r.allowed()).as("%s -> %s", q, r.reason()).isTrue();
            assertThat(r.safeSql()).contains("LIMIT 1000");
        }
    }
}
