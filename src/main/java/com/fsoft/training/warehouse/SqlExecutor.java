package com.fsoft.training.warehouse;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Runs guard-approved SQL through the read-only analyst connection. */
@Component
public class SqlExecutor {

    static final int MAX_ROWS = 1000;
    static final int TIMEOUT_SECONDS = 10;

    private final JdbcTemplate jdbc;
    private final PlanInspector planInspector;

    public SqlExecutor(@Qualifier("analyst") JdbcTemplate analystJdbcTemplate, PlanInspector planInspector) {
        this.jdbc = analystJdbcTemplate;
        this.planInspector = planInspector;
    }

    public QueryResult execute(String safeSql) {
        Optional<String> violation = planInspector.violation(safeSql);
        if (violation.isPresent()) {
            throw new SqlPolicyViolationException(violation.get());
        }
        try {
            return jdbc.execute((Connection con) -> {
                try (PreparedStatement ps = con.prepareStatement(safeSql)) {
                    ps.setMaxRows(MAX_ROWS + 1);
                    ps.setQueryTimeout(TIMEOUT_SECONDS);
                    try (ResultSet rs = ps.executeQuery()) {
                        return read(rs);
                    }
                }
            });
        } catch (DataAccessException e) {
            throw new SqlExecutionException(e.getMostSpecificCause().getMessage(), e);
        }
    }

    private static QueryResult read(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<ColumnInfo> columns = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            columns.add(new ColumnInfo(md.getColumnLabel(i), typeOf(md.getColumnType(i))));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() == MAX_ROWS) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) {
                row.add(value(rs, i, md.getColumnType(i), md.getColumnTypeName(i)));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, truncated);
    }

    static String typeOf(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL,
                 Types.FLOAT, Types.REAL, Types.DOUBLE -> "number";
            case Types.DATE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "date";
            case Types.BOOLEAN, Types.BIT -> "boolean";
            default -> "text";
        };
    }

    private static Object value(ResultSet rs, int i, int sqlType, String typeName) throws SQLException {
        Object raw = switch (sqlType) {
            case Types.DATE -> rs.getObject(i, LocalDate.class);
            // pgjdbc reports timestamptz as Types.TIMESTAMP, so the type name decides.
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "timestamptz".equalsIgnoreCase(typeName)
                    ? rs.getObject(i, OffsetDateTime.class)
                    : rs.getObject(i, LocalDateTime.class);
            default -> rs.getObject(i);
        };
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case Short v -> v.longValue();
            case Integer v -> v.longValue();
            case Long v -> v;
            case BigDecimal v -> v;
            case Float v -> BigDecimal.valueOf(v.doubleValue());
            case Double v -> v.isNaN() || v.isInfinite() ? v.toString() : BigDecimal.valueOf(v);
            case Boolean v -> v;
            default -> raw.toString();
        };
    }
}
