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

    @ParameterizedTest
    @ValueSource(strings = {
            // CTE scoping (Critical 1)
            "WITH pg_user AS (SELECT * FROM pg_user) SELECT * FROM pg_user",
            "WITH users AS (SELECT * FROM users) SELECT * FROM users",
            "WITH a AS (SELECT * FROM b), b AS (SELECT 1 FROM dw.dim_date) SELECT * FROM a",
            "SELECT * FROM (WITH pg_user AS (SELECT 1) SELECT * FROM pg_user) s CROSS JOIN pg_user",
            "SELECT * FROM dw.dim_date WHERE date_key IN "
                    + "(WITH pg_user AS (SELECT * FROM pg_user) SELECT usename FROM pg_user)",
            // clauses skipped by the old traversal, table rule (Critical 2)
            "SELECT * FROM dw.dim_date ORDER BY (SELECT password FROM app.users LIMIT 1)",
            "SELECT * FROM dw.dim_date OFFSET (SELECT password::int FROM app.users LIMIT 1)",
            "SELECT * FROM dw.dim_date LIMIT 10 OFFSET (SELECT count(*) FROM pg_shadow)",
            "SELECT count(*) FROM dw.dim_date GROUP BY (SELECT 1 FROM app.users LIMIT 1)",
            "SELECT count(*) FILTER (WHERE EXISTS (SELECT 1 FROM app.users)) FROM dw.dim_date",
            "SELECT rank() OVER (ORDER BY (SELECT 1 FROM pg_shadow)) FROM dw.dim_date",
            "SELECT DISTINCT ON ((SELECT usename FROM pg_user LIMIT 1)) date_key FROM dw.dim_date",
            "SELECT * FROM dw.dim_date FETCH FIRST (SELECT 1 FROM app.users) ROWS ONLY",
            // clauses skipped by the old traversal, function rule (Critical 2)
            "SELECT * FROM dw.dim_date ORDER BY pg_sleep(5)",
            "SELECT * FROM dw.dim_date ORDER BY current_setting('x')",
            "SELECT * FROM dw.dim_date OFFSET pg_sleep(5)::text::int",
            "SELECT count(*) FILTER (WHERE pg_sleep(1) IS NULL) FROM dw.dim_date",
            "SELECT rank() OVER (PARTITION BY pg_sleep(1)) FROM dw.dim_date",
            "SELECT rank() OVER w FROM dw.dim_date WINDOW w AS (ORDER BY pg_sleep(1))",
            "SELECT string_agg(name, ',' ORDER BY pg_sleep(1)) FROM dw.dim_track",
            "SELECT * FROM (SELECT * FROM dw.dim_date ORDER BY pg_sleep(1)) s",
            "SELECT * FROM dw.dim_date GROUP BY date_key HAVING pg_sleep(1) IS NULL",
            // function rule families (Important 3)
            "SELECT query_to_xml('select * from app.users', true, true, '')",
            "SELECT table_to_xml('app.users', true, true, '')",
            "SELECT cursor_to_xml('c', 1, true, true, '')",
            "SELECT schema_to_xml('app', true, true, '')",
            "SELECT database_to_xml(true, true, '')",
            "SELECT query_to_xml_and_xmlschema('select 1', true, true, '')",
            "SELECT lo_get(1)",
            "SELECT pg_advisory_lock(1)",
            "SELECT pg_stat_file('x')",
            "SELECT pg_reload_conf()",
            "SELECT dblink_exec('x', 'y')",
            "SELECT nextval('dw.seq')",
            "SELECT setval('dw.seq', 1)",
            "SELECT version()",
            "SELECT has_table_privilege('app.users', 'select')",
            "SELECT to_regclass('app.users')"
    })
    void blocksBypassAttempts(String sql) {
        GuardResult r = guard.check(sql);
        assertThat(r.allowed()).as(sql).isFalse();
        assertThat(r.reason()).isNotBlank();
    }

    @Test
    void allowsAnalyticsFunctionsAndScopedCtes() {
        String[] queries = {
                "SELECT sum(total), count(*), avg(total), round(avg(total), 2), coalesce(max(total), 0) "
                        + "FROM dw.fact_sales",
                "SELECT to_char(d.full_date, 'YYYY-MM'), lower(d.month_name), upper(d.month_name), "
                        + "concat(d.month_name, '-', d.year_num), CAST(d.year_num AS text), "
                        + "row_number() OVER (ORDER BY d.full_date), rank() OVER (ORDER BY d.full_date) "
                        + "FROM dw.dim_date d",
                "SELECT string_agg(name, ',' ORDER BY name) FROM dw.dim_track",
                "WITH a AS (SELECT 1 AS x FROM dw.dim_date), b AS (SELECT * FROM a) SELECT * FROM b",
                "WITH RECURSIVE r AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM r WHERE n < 5) SELECT * FROM r",
                "SELECT * FROM dw.dim_date ORDER BY (SELECT max(date_key) FROM dw.fact_sales)"
        };
        for (String q : queries) {
            GuardResult r = guard.check(q);
            assertThat(r.allowed()).as("%s -> %s", q, r.reason()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 1. quoted identifiers are case-sensitive in Postgres
            "WITH \"PG_USER\" AS (SELECT 1) SELECT * FROM pg_user",
            "WITH \"Users\" AS (SELECT 1) SELECT * FROM users",
            "SELECT * FROM \"DW\".dim_date",
            // 2. joins inside a parenthesized FROM item
            "SELECT * FROM (dw.dim_date d JOIN pg_shadow s ON true)",
            "SELECT * FROM (dw.dim_date b CROSS JOIN pg_user)",
            "SELECT * FROM dw.dim_date a JOIN (dw.dim_date b JOIN dw.dim_date c "
                    + "ON current_setting('x')='1') ON true",
            "SELECT * FROM (dw.dim_date d JOIN dw.dim_date e ON pg_sleep(1) IS NULL)",
            "SELECT * FROM (dw.dim_date d JOIN app.users u ON true)",
            // 3. IS [NOT] DISTINCT FROM
            "SELECT * FROM dw.dim_date x WHERE x.date_key IS DISTINCT FROM (SELECT 1 FROM app.users)",
            "SELECT * FROM dw.dim_date x WHERE x.date_key IS NOT DISTINCT FROM (SELECT 1 FROM app.users)",
            "SELECT * FROM dw.dim_date x WHERE (SELECT 1 FROM app.users) IS DISTINCT FROM x.date_key",
            "SELECT * FROM dw.dim_date x WHERE x.date_key IS DISTINCT FROM pg_sleep(1)",
            // 4. SQL/JSON constructors
            "SELECT JSON_ARRAYAGG((SELECT username FROM app.users LIMIT 1)) FROM dw.dim_date",
            "SELECT JSON_ARRAY((SELECT username FROM app.users LIMIT 1)) FROM dw.dim_date",
            "SELECT JSON_OBJECT('k' : (SELECT username FROM app.users LIMIT 1)) FROM dw.dim_date",
            "SELECT JSON_OBJECTAGG(date_key VALUE (SELECT username FROM app.users LIMIT 1)) FROM dw.dim_date",
            "SELECT JSON_OBJECT('k' VALUE pg_sleep(1)) FROM dw.dim_date",
            // 5. JSON operators
            "SELECT d.j -> (SELECT username FROM app.users LIMIT 1) FROM dw.dim_date d",
            "SELECT d.j ->> pg_sleep(1)::text FROM dw.dim_date d",
            "SELECT (SELECT '{}'::jsonb FROM app.users LIMIT 1) ->> 'a' FROM dw.dim_date d",
            "SELECT d.j #>> (SELECT '{a}' FROM app.users LIMIT 1) FROM dw.dim_date d",
            // 6. keyword forms parsed as columns
            "SELECT current_user",
            "SELECT session_user",
            "SELECT user",
            "SELECT current_role",
            "SELECT current_schema",
            "SELECT current_catalog",
            "SELECT date_key, current_user FROM dw.dim_date",
            // 7. functions running SQL text
            "SELECT * FROM ts_stat('SELECT 1')",
            "SELECT ts_rewrite('a'::tsquery, 'SELECT 1')"
    })
    void blocksRound2Bypasses(String sql) {
        GuardResult r = guard.check(sql);
        assertThat(r.allowed()).as(sql).isFalse();
        assertThat(r.reason()).isNotBlank();
    }

    @Test
    void allowsRecursiveAppliedToEveryWithItem() {
        GuardResult r = guard.check("WITH RECURSIVE r AS (SELECT 1 n), "
                + "s AS (SELECT n+1 FROM s WHERE n<3 UNION ALL SELECT n FROM r) SELECT * FROM s");
        assertThat(r.allowed()).as(r.reason()).isTrue();
    }

    @Test
    void quotedCteMatchesQuotedReferenceExactly() {
        GuardResult r = guard.check("WITH \"Mixed\" AS (SELECT 1 AS n FROM dw.dim_date) SELECT * FROM \"Mixed\"");
        assertThat(r.allowed()).as(r.reason()).isTrue();
    }

    @Test
    void unionTrailingLimitIsKeptOrCappedAndSingle() {
        GuardResult small = guard.check("SELECT date_key FROM dw.dim_date UNION SELECT date_key FROM dw.fact_sales LIMIT 10");
        assertThat(small.allowed()).as(small.reason()).isTrue();
        assertThat(small.safeSql()).containsOnlyOnce("LIMIT").contains("LIMIT 10");

        GuardResult big = guard.check(
                "SELECT date_key FROM dw.dim_date UNION SELECT date_key FROM dw.fact_sales LIMIT 99999 OFFSET 5");
        assertThat(big.allowed()).as(big.reason()).isTrue();
        assertThat(big.safeSql()).containsOnlyOnce("LIMIT").contains("LIMIT 1000").contains("OFFSET 5");

        GuardResult none = guard.check("SELECT date_key FROM dw.dim_date UNION SELECT date_key FROM dw.fact_sales");
        assertThat(none.safeSql()).containsOnlyOnce("LIMIT").endsWith("LIMIT 1000");
    }

    @Test
    void lexicalBackstopCatchesWhatTheAstWalkCouldMiss() {
        assertThat(SqlGuard.lexicalBackstop("SELECT pg_sleep (1)")).isPresent();
        assertThat(SqlGuard.lexicalBackstop("SELECT \"query_to_xml\"('x')")).isPresent();
        assertThat(SqlGuard.lexicalBackstop("SELECT * FROM pg_user")).isPresent();
        assertThat(SqlGuard.lexicalBackstop("SELECT * FROM information_schema.tables")).isPresent();
        assertThat(SqlGuard.lexicalBackstop("SELECT current_user")).isPresent();
        assertThat(SqlGuard.lexicalBackstop("SELECT CURRENT_SCHEMA")).isPresent();
        // literals and unrelated identifiers are fine
        assertThat(SqlGuard.lexicalBackstop("SELECT 'pg_sleep(1) current_user' FROM dw.dim_date")).isEmpty();
        assertThat(SqlGuard.lexicalBackstop("SELECT d.user_name, sum(x) FROM dw.dim_date d")).isEmpty();
        assertThat(SqlGuard.lexicalBackstop("SELECT 'it''s pg_sleep(1)', px FROM dw.dim_date")).isEmpty();
    }

    @Test
    void blockedFunctionPredicateIsPublicAndCoversFamilies() {
        assertThat(SqlGuard.isBlockedFunction("pg_sleep")).isTrue();
        assertThat(SqlGuard.isBlockedFunction("ts_stat")).isTrue();
        assertThat(SqlGuard.isBlockedFunction("sum")).isFalse();
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
