package com.fsoft.training.eval;

import com.fsoft.training.warehouse.QueryResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Compares a generated result with the gold result by values only: column names and column order are
 * ignored, extra generated columns are ignored, numbers are compared rounded to 2 decimals.
 */
public class ResultComparator {

    static final String NULL_TOKEN = "∅";

    /** Upper bound on complete column pairings tried, so wide results with many look-alike columns stay cheap. */
    static final int MAX_PAIRINGS = 10_000;

    public boolean matches(QueryResult gold, QueryResult actual, boolean ordered) {
        if (gold == null || actual == null) {
            return false;
        }
        List<List<String>> goldRows = normalize(gold.rows());
        List<List<String>> actualRows = normalize(actual.rows());
        if (goldRows.size() != actualRows.size()) {
            return false;
        }
        int goldCols = gold.columns().size();
        int actualCols = actual.columns().size();
        if (actualCols < goldCols) {
            return false;
        }
        List<Map<String, Integer>> goldBags = columnBags(goldRows, goldCols);
        List<Map<String, Integer>> actualBags = columnBags(actualRows, actualCols);
        Search search = new Search(goldRows, actualRows, goldBags, actualBags, ordered);
        return search.assign(0, new int[goldCols], new boolean[actualCols]);
    }

    /** True when the statement itself (not a subquery, CTE body or window) has an ORDER BY. */
    public static boolean hasTopLevelOrderBy(String sql) {
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException | RuntimeException e) {
            return false;
        }
        Select select = statement instanceof Select s ? s : null;
        while (select != null) {
            if (select.getOrderByElements() != null && !select.getOrderByElements().isEmpty()) {
                return true;
            }
            select = select instanceof ParenthesedSelect p ? p.getSelect() : null;
        }
        return false;
    }

    static String normalizeValue(Object value) {
        return switch (value) {
            case null -> NULL_TOKEN;
            case BigDecimal d -> number(d);
            case Long l -> number(BigDecimal.valueOf(l));
            case Integer i -> number(BigDecimal.valueOf(i));
            case Short s -> number(BigDecimal.valueOf(s));
            case Double d when !d.isNaN() && !d.isInfinite() -> number(BigDecimal.valueOf(d));
            case Float f when !f.isNaN() && !f.isInfinite() -> number(BigDecimal.valueOf(f.doubleValue()));
            case String s -> s.trim();
            default -> value.toString().trim();
        };
    }

    private static String number(BigDecimal d) {
        return d.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static List<List<String>> normalize(List<List<Object>> rows) {
        List<List<String>> out = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            List<String> r = new ArrayList<>(row.size());
            for (Object v : row) {
                r.add(normalizeValue(v));
            }
            out.add(r);
        }
        return out;
    }

    private static List<Map<String, Integer>> columnBags(List<List<String>> rows, int columns) {
        List<Map<String, Integer>> bags = new ArrayList<>(columns);
        for (int c = 0; c < columns; c++) {
            Map<String, Integer> bag = new HashMap<>();
            for (List<String> row : rows) {
                bag.merge(row.get(c), 1, Integer::sum);
            }
            bags.add(bag);
        }
        return bags;
    }

    private static Map<List<String>, Integer> rowBag(List<List<String>> rows) {
        Map<List<String>, Integer> bag = new HashMap<>();
        for (List<String> row : rows) {
            bag.merge(row, 1, Integer::sum);
        }
        return bag;
    }

    /** Backtracking pairing of gold columns to distinct actual columns with equal value multisets. */
    private static final class Search {
        private final List<List<String>> goldRows;
        private final List<List<String>> actualRows;
        private final List<Map<String, Integer>> goldBags;
        private final List<Map<String, Integer>> actualBags;
        private final boolean ordered;
        private Map<List<String>, Integer> goldRowBag;
        private int pairingsTried;

        Search(List<List<String>> goldRows, List<List<String>> actualRows, List<Map<String, Integer>> goldBags,
               List<Map<String, Integer>> actualBags, boolean ordered) {
            this.goldRows = goldRows;
            this.actualRows = actualRows;
            this.goldBags = goldBags;
            this.actualBags = actualBags;
            this.ordered = ordered;
        }

        boolean assign(int goldCol, int[] pairing, boolean[] used) {
            if (goldCol == pairing.length) {
                pairingsTried++;
                return rowsMatch(pairing);
            }
            for (int a = 0; a < used.length; a++) {
                if (pairingsTried >= MAX_PAIRINGS) {
                    return false;
                }
                if (used[a] || !goldBags.get(goldCol).equals(actualBags.get(a))) {
                    continue;
                }
                used[a] = true;
                pairing[goldCol] = a;
                if (assign(goldCol + 1, pairing, used)) {
                    return true;
                }
                used[a] = false;
            }
            return false;
        }

        private boolean rowsMatch(int[] pairing) {
            List<List<String>> projected = new ArrayList<>(actualRows.size());
            for (List<String> row : actualRows) {
                List<String> p = new ArrayList<>(pairing.length);
                for (int a : pairing) {
                    p.add(row.get(a));
                }
                projected.add(p);
            }
            if (ordered) {
                return projected.equals(goldRows);
            }
            if (goldRowBag == null) {
                goldRowBag = rowBag(goldRows);
            }
            return rowBag(projected).equals(goldRowBag);
        }
    }
}
