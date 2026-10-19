package com.fsoft.training.guardrail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.WindowDefinition;
import net.sf.jsqlparser.expression.WindowElement;
import net.sf.jsqlparser.expression.WindowOffset;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

import org.springframework.stereotype.Component;

/**
 * AST-based guardrail: only a single SELECT over schema {@code dw} may pass.
 * SQL produced by an LLM must go through {@link #check(String)} before execution.
 */
@Component
public class SqlGuard {

    public static final int MAX_ROWS = 1000;

    private static final String ALLOWED_SCHEMA = "dw";

    /** Exact names blocked in addition to the prefix/infix families in {@link #isBlockedFunction}. */
    private static final Set<String> BLOCKED_FUNCTIONS = Set.of(
            "current_setting", "set_config", "nextval", "setval", "currval", "lastval",
            "txid_current", "version", "current_user", "session_user",
            "inet_server_addr", "inet_server_port",
            "has_table_privilege", "has_schema_privilege", "has_database_privilege",
            "to_regclass", "xmlparse");

    private static final List<String> BLOCKED_PREFIXES = List.of(
            "pg_", "lo_", "dblink",
            "query_to_", "table_to_", "cursor_to_", "schema_to_", "database_to_");

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
        try {
            inspector.walk(select);
        } catch (RuntimeException e) {
            return GuardResult.blocked("SQL không hợp lệ: không kiểm tra được cấu trúc câu lệnh.");
        }
        if (inspector.nonSelectCte != null) {
            return GuardResult.blocked("CTE '" + inspector.nonSelectCte
                    + "' không phải câu lệnh SELECT, chỉ cho phép SELECT.");
        }
        for (Table table : inspector.tables) {
            if (!isAllowedTable(table)) {
                return GuardResult.blocked("Bảng '" + table.getFullyQualifiedName()
                        + "' không được phép: chỉ được truy vấn bảng thuộc schema dw (ví dụ dw.fact_sales).");
            }
        }
        for (String function : inspector.functions) {
            if (isBlockedFunction(function)) {
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

    /** @param name unquoted, lowercased, schema-stripped function name */
    static boolean isBlockedFunction(String name) {
        if (BLOCKED_FUNCTIONS.contains(name) || name.contains("_to_xml")) {
            return true;
        }
        return BLOCKED_PREFIXES.stream().anyMatch(name::startsWith);
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

    /**
     * Walks every node of the statement via the deparser visitors (which must visit every clause to
     * print it), tracking Postgres CTE scoping: a non-recursive WITH item sees only earlier siblings,
     * WITH RECURSIVE also sees itself, and CTEs of a nested SELECT do not leak outward.
     */
    private static final class Inspector {
        final List<Table> tables = new ArrayList<>();
        final List<String> functions = new ArrayList<>();
        String nonSelectCte;
        boolean hasInto;
        boolean hasLocking;
        private final Deque<Set<String>> scopes = new ArrayDeque<>();

        void walk(Select select) {
            StringBuilder sink = new StringBuilder();
            ScopedSelectDeParser selects = new ScopedSelectDeParser(sink);
            expressions = new HookedExpressionDeParser(selects, sink);
            selects.setExpressionVisitor(expressions);
            select.accept((SelectVisitor<StringBuilder>) selects, null);
        }

        private boolean isVisibleCte(Table table) {
            if (table.getSchemaName() != null || table.getName() == null) {
                return false;
            }
            String n = unquote(table.getName()).toLowerCase(Locale.ROOT);
            return scopes.stream().anyMatch(frame -> frame.contains(n));
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

        private ExpressionDeParser expressions;

        /** The deparser prints WINDOW clauses and frame bounds as text, so walk their expressions here. */
        private void walkWindowElement(WindowElement element) {
            if (element == null) {
                return;
            }
            for (WindowOffset offset : new WindowOffset[] {
                    element.getOffset(),
                    element.getRange() == null ? null : element.getRange().getStart(),
                    element.getRange() == null ? null : element.getRange().getEnd()}) {
                if (offset != null && offset.getExpression() != null) {
                    offset.getExpression().accept(expressions, null);
                }
            }
        }

        private void walkWindowDefinitions(List<WindowDefinition> definitions) {
            if (definitions == null) {
                return;
            }
            for (WindowDefinition definition : definitions) {
                if (definition.getPartitionExpressionList() != null) {
                    for (Object e : definition.getPartitionExpressionList()) {
                        ((Expression) e).accept(expressions, null);
                    }
                }
                if (definition.getOrderByElements() != null) {
                    for (OrderByElement order : definition.getOrderByElements()) {
                        order.getExpression().accept(expressions, null);
                    }
                }
                walkWindowElement(definition.getWindowElement());
            }
        }

        private final class HookedExpressionDeParser extends ExpressionDeParser {
            HookedExpressionDeParser(SelectVisitor<StringBuilder> selectVisitor, StringBuilder sb) {
                super(selectVisitor, sb);
            }

            @Override
            public <S> StringBuilder visit(Function function, S context) {
                record(function.getName());
                return super.visit(function, context);
            }

            @Override
            public <S> StringBuilder visit(AnalyticExpression expr, S context) {
                record(expr.getName());
                walkWindowElement(expr.getWindowElement());
                return super.visit(expr, context);
            }
        }

        private final class ScopedSelectDeParser extends SelectDeParser {
            ScopedSelectDeParser(StringBuilder sb) {
                super(sb);
            }

            @Override
            public <S> StringBuilder visit(Table table, S context) {
                if (!isVisibleCte(table)) {
                    tables.add(table);
                }
                return super.visit(table, context);
            }

            @Override
            public <S> StringBuilder visit(TableFunction tableFunction, S context) {
                if (tableFunction.getFunction() != null) {
                    record(tableFunction.getFunction().getName());
                }
                return super.visit(tableFunction, context);
            }

            @Override
            public <S> StringBuilder visit(WithItem<?> withItem, S context) {
                if (!(withItem.getParenthesedStatement() instanceof ParenthesedSelect)) {
                    nonSelectCte = withItem.getUnquotedAliasName();
                }
                String alias = withItem.getUnquotedAliasName() == null ? null
                        : withItem.getUnquotedAliasName().toLowerCase(Locale.ROOT);
                Set<String> frame = scopes.peek();
                if (withItem.isRecursive() && alias != null && frame != null) {
                    frame.add(alias);
                }
                StringBuilder result = super.visit(withItem, context);
                if (alias != null && frame != null) {
                    frame.add(alias);
                }
                return result;
            }

            @Override
            public <S> StringBuilder visit(PlainSelect plainSelect, S context) {
                if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()
                        || plainSelect.getIntoTempTable() != null) {
                    hasInto = true;
                }
                if (plainSelect.getForMode() != null || plainSelect.getForUpdateTable() != null) {
                    hasLocking = true;
                }
                scopes.push(new HashSet<>());
                try {
                    walkWindowDefinitions(plainSelect.getWindowDefinitions());
                    return super.visit(plainSelect, context);
                } finally {
                    scopes.pop();
                }
            }

            @Override
            public <S> StringBuilder visit(SetOperationList list, S context) {
                if (list.getForMode() != null || list.getForUpdateTable() != null) {
                    hasLocking = true;
                }
                scopes.push(new HashSet<>());
                try {
                    return super.visit(list, context);
                } finally {
                    scopes.pop();
                }
            }

            @Override
            public <S> StringBuilder visit(ParenthesedSelect select, S context) {
                scopes.push(new HashSet<>());
                try {
                    return super.visit(select, context);
                } finally {
                    scopes.pop();
                }
            }
        }
    }
}
