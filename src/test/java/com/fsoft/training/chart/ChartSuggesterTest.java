package com.fsoft.training.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.warehouse.ColumnInfo;
import com.fsoft.training.warehouse.QueryResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChartSuggesterTest {

    private final ChartSuggester suggester = new ChartSuggester();

    private static ColumnInfo col(String n, String t) {
        return new ColumnInfo(n, t);
    }

    private static List<Object> row(Object... v) {
        return Arrays.asList(v);
    }

    private static QueryResult qr(List<ColumnInfo> cols, List<List<Object>> rows) {
        return new QueryResult(cols, rows, false);
    }

    @Test
    void zeroRowsIsTable() {
        var r = qr(List.of(col("a", "text"), col("b", "number")), List.of());
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.TABLE, null, null));
    }

    @Test
    void oneRowOneNumberColumnIsKpi() {
        var r = qr(List.of(col("total", "number")), List.of(row(new BigDecimal("12.5"))));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.KPI, null, null));
    }

    @Test
    void dateAndNumberIsLine() {
        var r = qr(List.of(col("day", "date"), col("revenue", "number")),
                List.of(row("2024-01-01", 1L), row("2024-01-02", 2L)));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.LINE, "day", "revenue"));
    }

    @Test
    void numberFirstThenDateIsLineRegardlessOfOrder() {
        var r = qr(List.of(col("revenue", "number"), col("day", "date")),
                List.of(row(1L, "2024-01-01"), row(2L, "2024-01-02")));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.LINE, "day", "revenue"));
    }

    @Test
    void textMonthHintAndNumberIsLine() {
        var r = qr(List.of(col("year_month", "text"), col("revenue", "number")),
                List.of(row("2024-01", 1L), row("2024-02", 2L)));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.LINE, "year_month", "revenue"));
    }

    @Test
    void numericYearWithNumberIsLine() {
        var r = qr(List.of(col("year", "number"), col("revenue", "number")),
                List.of(row(2021, 5.0), row(2022, 6.0)));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.LINE, "year", "revenue"));
    }

    @Test
    void nameColumnIsNotTimeLike() {
        var r = qr(List.of(col("artist_name", "text"), col("sales", "number")),
                List.of(row("A", 1L), row("B", 2L)));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.BAR, "artist_name", "sales"));
    }

    @Test
    void namTokenIsTimeLike() {
        var r = qr(List.of(col("doanh_thu_nam", "text"), col("doanh_thu", "number")),
                List.of(row("2020", 1L), row("2021", 2L)));
        assertThat(suggester.suggest(r).type()).isEqualTo(ChartType.LINE);
    }

    @Test
    void shareColumnWithFewRowsIsPie() {
        var r = qr(List.of(col("country", "text"), col("share_pct", "number")),
                List.of(row("VN", 60L), row("US", 40L)));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.PIE, "country", "share_pct"));
    }

    @Test
    void shareColumnWithNegativeValueIsBar() {
        var r = qr(List.of(col("country", "text"), col("share", "number")),
                List.of(row("VN", 60L), row("US", -4L)));
        assertThat(suggester.suggest(r).type()).isEqualTo(ChartType.BAR);
    }

    @Test
    void shareColumnWithSevenRowsIsBar() {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rows.add(row("c" + i, 1L));
        }
        var r = qr(List.of(col("country", "text"), col("share", "number")), rows);
        assertThat(suggester.suggest(r).type()).isEqualTo(ChartType.BAR);
    }

    @Test
    void textAndNumberIsBar() {
        var r = qr(List.of(col("genre", "text"), col("total", "number")),
                List.of(row("rock", 10L), row("pop", 5L), row("jazz", 3L)));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.BAR, "genre", "total"));
    }

    @Test
    void numberFirstThenTextIsBar() {
        var r = qr(List.of(col("total", "number"), col("genre", "text")),
                List.of(row(10L, "rock"), row(5L, "pop")));
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.BAR, "genre", "total"));
    }

    @Test
    void allNullNumericColumnDoesNotThrow() {
        var r = qr(List.of(col("genre", "text"), col("share", "number")),
                List.of(row("rock", null), row("pop", null)));
        assertThat(suggester.suggest(r).type()).isEqualTo(ChartType.BAR);
    }

    @Test
    void oneRowOneNullKpiDoesNotThrow() {
        var r = qr(List.of(col("total", "number")), List.of(row((Object) null)));
        assertThat(suggester.suggest(r).type()).isEqualTo(ChartType.KPI);
    }

    @Test
    void manyRowsTextAndNumberIsTable() {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            rows.add(row("c" + i, (long) i));
        }
        var r = qr(List.of(col("genre", "text"), col("total", "number")), rows);
        assertThat(suggester.suggest(r)).isEqualTo(new ChartSpec(ChartType.TABLE, null, null));
    }

    @Test
    void threeColumnsIsTable() {
        var r = qr(List.of(col("a", "text"), col("b", "number"), col("c", "number")),
                List.of(row("x", 1L, 2L)));
        assertThat(suggester.suggest(r).type()).isEqualTo(ChartType.TABLE);
    }
}
