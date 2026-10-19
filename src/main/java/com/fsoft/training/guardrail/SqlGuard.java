package com.fsoft.training.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

import org.springframework.stereotype.Component;

/**
 * AST-based guardrail: only a single SELECT over schema {@code dw} may pass.
 * SQL produced by an LLM must go through {@link #check(String)} before execution.
 */
@Component
public class SqlGuard {

    public static final int MAX_ROWS = 1000;

    private static final String ALLOWED_SCHEMA = "dw";

    private static final Set<String> BLOCKED_FUNCTIONS = Set.of(
            "pg_sleep", "pg_read_file", "pg_read_binary_file", "pg_ls_dir",
            "lo_import", "lo_export", "dblink", "current_setting", "set_config",
            "pg_terminate_backend", "pg_cancel_backend");

    public GuardResult check(String sql) {
        if (sql == null || sql.isBlank()) {
            return GuardResult.blocked("SQL rỗng, không có gì để chạy.");
        }
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException | RuntimeException e) {
            return GuardResult.blocked("SQL không hợp lệ: không phân tích được cú pháp.");
        }
        if (statements == null || statements.size() != 1) {
            return GuardResult.blocked("Chỉ cho phép đúng một câu lệnh SQL mỗi lần.");
        }
        Statement statement = statements.get(0);
        if (!(statement instanceof Select select)) {
            return GuardResult.blocked("Chỉ cho phép câu lệnh SELECT (đọc dữ liệu), không cho phép "
                    + statement.getClass().getSimpleName().toUpperCase(Locale.ROOT) + ".");
        }

        Inspector inspector = new Inspector();
        Set<String> realTables;
        try {
            // The returned set already excludes CTE names; use it to filter recorded Table nodes.
            realTables = inspector.getTables((Statement) select);
        } catch (RuntimeException e) {
            return GuardResult.blocked("SQL không hợp lệ: không kiểm tra được cấu trúc câu lệnh.");
        }
        if (inspector.nonSelectCte != null) {
            return GuardResult.blocked("CTE '" + inspector.nonSelectCte
                    + "' không phải câu lệnh SELECT, chỉ cho phép SELECT.");
        }
        for (Table table : inspector.tables) {
            if (!realTables.contains(table.getFullyQualifiedName())) {
                continue; // reference to a CTE, not a table
            }
            if (!isAllowedTable(table)) {
                return GuardResult.blocked("Bảng '" + table.getFullyQualifiedName()
                        + "' không được phép: chỉ được truy vấn bảng thuộc schema dw (ví dụ dw.fact_sales).");
            }
        }
        for (String function : inspector.functions) {
            if (BLOCKED_FUNCTIONS.contains(function)) {
                return GuardResult.blocked("Hàm '" + function + "' bị cấm vì lý do an toàn.");
            }
        }
        if (inspector.hasInto) {
            return GuardResult.blocked("SELECT ... INTO không được phép (sẽ tạo bảng mới).");
        }
        if (inspector.hasLocking || select.getForMode() != null || select.getForUpdateTable() != null) {
            return GuardResult.blocked("FOR UPDATE/SHARE không được phép (khóa dữ liệu).");
        }

        applyLimit(select);
        return GuardResult.allowed(select.toString());
    }

    private static boolean isAllowedTable(Table table) {
        // Exactly schema.table; a database part (db.schema.table) or missing schema is rejected.
        if (table.getDatabase() != null && table.getDatabase().getDatabaseName() != null) {
            return false;
        }
        String schema = unquote(table.getSchemaName());
        return schema != null && ALLOWED_SCHEMA.equalsIgnoreCase(schema);
    }

    private static void applyLimit(Select select) {
        Limit limit = select.getLimit();
        if (limit == null) {
            limit = new Limit();
            limit.setRowCount(new LongValue(MAX_ROWS));
            select.setLimit(limit);
            return;
        }
        if (limit.getRowCount() instanceof LongValue rows && rows.getValue() <= MAX_ROWS) {
            return;
        }
        // Too large, LIMIT ALL / NULL, or an expression we cannot prove is <= MAX_ROWS.
        limit.setLimitAll(false);
        limit.setLimitNull(false);
        limit.setRowCount(new LongValue(MAX_ROWS));
    }

    private static String unquote(String name) {
        if (name == null) {
            return null;
        }
        String n = name.strip();
        if (n.length() >= 2 && (n.startsWith("\"") && n.endsWith("\"") || n.startsWith("`") && n.endsWith("`"))) {
            n = n.substring(1, n.length() - 1);
        }
        return n;
    }

    /** Walks the whole statement tree (select list, WHERE, JOIN, subqueries, CTEs, function args). */
    private static final class Inspector extends TablesNamesFinder<Void> {
        final List<Table> tables = new ArrayList<>();
        final List<String> functions = new ArrayList<>();
        String nonSelectCte;
        boolean hasInto;
        boolean hasLocking;

        @Override
        protected String extractTableName(Table table) {
            // Only called for real tables; CTE names are filtered by the base class.
            tables.add(table);
            return super.extractTableName(table);
        }

        @Override
        public <S> Void visit(Function function, S context) {
            record(function.getName());
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(TableFunction tableFunction, S context) {
            if (tableFunction.getFunction() != null) {
                record(tableFunction.getFunction().getName());
            }
            return super.visit(tableFunction, context);
        }

        @Override
        public <S> Void visit(WithItem<?> withItem, S context) {
            if (!(withItem.getParenthesedStatement() instanceof ParenthesedSelect)) {
                nonSelectCte = withItem.getUnquotedAliasName();
            }
            return super.visit(withItem, context);
        }

        @Override
        public <S> Void visit(PlainSelect plainSelect, S context) {
            if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()
                    || plainSelect.getIntoTempTable() != null) {
                hasInto = true;
            }
            if (plainSelect.getForMode() != null || plainSelect.getForUpdateTable() != null) {
                hasLocking = true;
            }
            return super.visit(plainSelect, context);
        }

        @Override
        public <S> Void visit(SetOperationList list, S context) {
            if (list.getForMode() != null || list.getForUpdateTable() != null) {
                hasLocking = true;
            }
            return super.visit(list, context);
        }

        private void record(String name) {
            if (name == null) {
                return;
            }
            String n = name;
            int dot = n.lastIndexOf('.');
            if (dot >= 0) {
                n = n.substring(dot + 1);
            }
            functions.add(unquote(n).toLowerCase(Locale.ROOT));
        }
    }
}
