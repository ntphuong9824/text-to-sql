package com.fsoft.training.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.warehouse.ColumnInfo;
import com.fsoft.training.warehouse.QueryResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultComparatorTest {

    private final ResultComparator comparator = new ResultComparator();

    /** Builds a result; {@code rows} are given row by row. */
    private static QueryResult result(List<String> columns, List<List<Object>> rows) {
        List<ColumnInfo> cols = columns.stream().map(c -> new ColumnInfo(c, "text")).toList();
        return new QueryResult(cols, rows, false);
    }

    private static List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static final QueryResult GOLD = result(List.of("region", "revenue"), List.of(
            row("Europe", new BigDecimal("79.23")),
            row("North America", new BigDecimal("87.27")),
            row("Asia-Pacific", new BigDecimal("13.86"))));

    @Test
    void identicalResultsMatch() {
        assertThat(comparator.matches(GOLD, GOLD, false)).isTrue();
        assertThat(comparator.matches(GOLD, GOLD, true)).isTrue();
    }

    @Test
    void rowOrderMattersOnlyWhenOrdered() {
        QueryResult reordered = result(List.of("region", "revenue"), List.of(
                row("North America", new BigDecimal("87.27")),
                row("Europe", new BigDecimal("79.23")),
                row("Asia-Pacific", new BigDecimal("13.86"))));
        assertThat(comparator.matches(GOLD, reordered, false)).isTrue();
        assertThat(comparator.matches(GOLD, reordered, true)).isFalse();
    }

    @Test
    void extraActualColumnIsIgnored() {
        QueryResult extra = result(List.of("region", "revenue", "order_count"), List.of(
                row("Europe", new BigDecimal("79.23"), 10L),
                row("North America", new BigDecimal("87.27"), 12L),
                row("Asia-Pacific", new BigDecimal("13.86"), 3L)));
        assertThat(comparator.matches(GOLD, extra, false)).isTrue();
    }

    @Test
    void columnOrderDoesNotMatter() {
        QueryResult swapped = result(List.of("revenue", "region"), List.of(
                row(new BigDecimal("79.23"), "Europe"),
                row(new BigDecimal("87.27"), "North America"),
                row(new BigDecimal("13.86"), "Asia-Pacific")));
        assertThat(comparator.matches(GOLD, swapped, true)).isTrue();
    }

    @Test
    void columnNamesDoNotMatter() {
        QueryResult renamed = result(List.of("khu_vuc", "doanh_thu"), GOLD.rows());
        assertThat(comparator.matches(GOLD, renamed, false)).isTrue();
    }

    @Test
    void numbersAreComparedRoundedToTwoDecimals() {
        QueryResult a = result(List.of("v"), List.of(row(new BigDecimal("2328.6"))));
        QueryResult b = result(List.of("v"), List.of(row(new BigDecimal("2328.600"))));
        assertThat(comparator.matches(a, b, false)).isTrue();

        QueryResult c = result(List.of("v"), List.of(row(new BigDecimal("1.004"))));
        QueryResult d = result(List.of("v"), List.of(row(new BigDecimal("1.0"))));
        assertThat(comparator.matches(c, d, false)).isTrue();
    }

    @Test
    void integerTypesAndDecimalsCompareByValue() {
        QueryResult a = result(List.of("n"), List.of(row(412L)));
        QueryResult b = result(List.of("n"), List.of(row(new BigDecimal("412.00"))));
        assertThat(comparator.matches(a, b, false)).isTrue();
    }

    @Test
    void missingRowDoesNotMatch() {
        QueryResult fewer = result(List.of("region", "revenue"), GOLD.rows().subList(0, 2));
        assertThat(comparator.matches(GOLD, fewer, false)).isFalse();
    }

    @Test
    void sameColumnMultisetsButMisalignedRowsDoNotMatch() {
        QueryResult gold = result(List.of("a", "b"), List.of(row(1L, 2L), row(2L, 1L)));
        QueryResult actual = result(List.of("x", "y"), List.of(row(1L, 1L), row(2L, 2L)));
        assertThat(comparator.matches(gold, actual, false)).isFalse();
    }

    @Test
    void backtracksWhenFirstColumnPairingLeadsToWrongRows() {
        // Both actual columns have gold column "a"'s multiset; only pairing a->y lines the rows up.
        QueryResult gold = result(List.of("a", "b"), List.of(row(1L, "p"), row(2L, "q")));
        QueryResult actual = result(List.of("x", "y", "z"),
                List.of(row(2L, 1L, "p"), row(1L, 2L, "q")));
        assertThat(comparator.matches(gold, actual, false)).isTrue();
    }

    @Test
    void stringsAreTrimmed() {
        QueryResult a = result(List.of("s"), List.of(row("Rock")));
        QueryResult b = result(List.of("s"), List.of(row("  Rock ")));
        assertThat(comparator.matches(a, b, false)).isTrue();
    }

    @Test
    void nullMatchesNull() {
        QueryResult a = result(List.of("s", "v"), List.of(row(null, 1L)));
        QueryResult b = result(List.of("s", "v"), List.of(row(null, 1L)));
        assertThat(comparator.matches(a, b, false)).isTrue();
        QueryResult c = result(List.of("s", "v"), List.of(row("x", 1L)));
        assertThat(comparator.matches(a, c, false)).isFalse();
    }

    @Test
    void missingActualColumnDoesNotMatch() {
        QueryResult onlyRegion = result(List.of("region"),
                List.of(row("Europe"), row("North America"), row("Asia-Pacific")));
        assertThat(comparator.matches(GOLD, onlyRegion, false)).isFalse();
    }

    @Test
    void detectsTopLevelOrderByOnly() {
        assertThat(ResultComparator.hasTopLevelOrderBy(
                "SELECT c.region FROM dw.dim_customer c ORDER BY c.region")).isTrue();
        assertThat(ResultComparator.hasTopLevelOrderBy(
                "SELECT d.month, SUM(SUM(f.revenue)) OVER (ORDER BY d.month) FROM dw.fact_sales f "
                        + "JOIN dw.dim_date d ON d.date_key = f.date_key GROUP BY d.month")).isFalse();
        assertThat(ResultComparator.hasTopLevelOrderBy(
                "SELECT x.a FROM (SELECT c.region AS a FROM dw.dim_customer c ORDER BY 1 LIMIT 2) x")).isFalse();
        assertThat(ResultComparator.hasTopLevelOrderBy(
                "WITH y AS (SELECT 1 AS a) SELECT y.a FROM y ORDER BY y.a")).isTrue();
        assertThat(ResultComparator.hasTopLevelOrderBy(
                "SELECT 1 AS a UNION ALL SELECT 2 ORDER BY a")).isTrue();
        assertThat(ResultComparator.hasTopLevelOrderBy("not sql at all")).isFalse();
    }
}
