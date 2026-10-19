package com.fsoft.training.chart;

import com.fsoft.training.warehouse.ColumnInfo;
import com.fsoft.training.warehouse.QueryResult;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ChartSuggester {

    private static final Set<String> TIME_HINTS =
            Set.of("month", "year", "quarter", "date", "thang", "nam", "quy");
    private static final List<String> PIE_HINTS = List.of("share", "percent", "ty_le", "pct");
    private static final int PIE_MIN_ROWS = 2;
    private static final int PIE_MAX_ROWS = 6;
    private static final int BAR_MAX_ROWS = 50;

    private static final ChartSpec TABLE = new ChartSpec(ChartType.TABLE, null, null);

    public ChartSpec suggest(QueryResult r) {
        if (r == null || r.rows() == null || r.rows().isEmpty()) {
            return TABLE;
        }
        List<ColumnInfo> cols = r.columns();
        int rowCount = r.rows().size();

        if (rowCount == 1 && cols.size() == 1 && isNumber(cols.get(0))) {
            return new ChartSpec(ChartType.KPI, null, null);
        }
        if (cols.size() != 2) {
            return TABLE;
        }

        // Rule 3: one time-like column, the other numeric.
        for (int i = 0; i < 2; i++) {
            ColumnInfo dim = cols.get(i);
            ColumnInfo val = cols.get(1 - i);
            if (isTimeLike(dim) && isNumber(val)) {
                return new ChartSpec(ChartType.LINE, dim.name(), val.name());
            }
        }

        // Rule 4: one text, one numeric.
        for (int i = 0; i < 2; i++) {
            ColumnInfo dim = cols.get(i);
            ColumnInfo val = cols.get(1 - i);
            if (!"text".equals(dim.type()) || !isNumber(val)) {
                continue;
            }
            if (isPie(r, 1 - i, val.name(), rowCount)) {
                return new ChartSpec(ChartType.PIE, dim.name(), val.name());
            }
            if (rowCount <= BAR_MAX_ROWS) {
                return new ChartSpec(ChartType.BAR, dim.name(), val.name());
            }
        }
        return TABLE;
    }

    private static boolean isPie(QueryResult r, int valIdx, String valName, int rowCount) {
        if (rowCount < PIE_MIN_ROWS || rowCount > PIE_MAX_ROWS) {
            return false;
        }
        String lower = valName.toLowerCase(Locale.ROOT);
        if (PIE_HINTS.stream().noneMatch(lower::contains)) {
            return false;
        }
        for (List<Object> row : r.rows()) {
            Object v = row.get(valIdx);
            if (!(v instanceof Number n) || n.doubleValue() < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumber(ColumnInfo c) {
        return "number".equals(c.type());
    }

    private static boolean isTimeLike(ColumnInfo c) {
        if ("date".equals(c.type())) {
            return true;
        }
        if (!"text".equals(c.type()) && !"number".equals(c.type())) {
            return false;
        }
        for (String token : c.name().toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (TIME_HINTS.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
