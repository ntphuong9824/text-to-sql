package com.fsoft.training.guardrail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JsonAggregateFunction;
import net.sf.jsqlparser.expression.JsonExpression;
import net.sf.jsqlparser.expression.JsonFunction;
import net.sf.jsqlparser.expression.JsonFunctionExpression;
import net.sf.jsqlparser.expression.JsonKeyValuePair;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.WindowDefinition;
import net.sf.jsqlparser.expression.WindowElement;
import net.sf.jsqlparser.expression.WindowOffset;
import net.sf.jsqlparser.expression.operators.relational.IsDistinctExpression;
import net.sf.jsqlparser.expression.operators.relational.JsonOperator;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.FromItemVisitor;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.TableStatement;
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
            "to_regclass", "xmlparse", "ts_stat", "ts_rewrite",
            "getpgusername", "current_database", "current_schemas", "inet_client_addr",
            "inet_client_port", "current_query");

    /** Keyword forms that the parser reads as plain columns but that expose session information. */
    private static final Set<String> KEYWORD_COLUMNS = Set.of(
            "current_user", "session_user", "system_user", "user", "current_role", "current_schema",
            "current_catalog");

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
        if (inspector.tableStatement) {
            return GuardResult.blocked("Câu lệnh TABLE không được hỗ trợ, hãy dùng SELECT ... FROM dw.<tên bảng>.");
        }
        if (inspector.keywordColumn != null) {
            return GuardResult.blocked("Từ khóa '" + inspector.keywordColumn
                    + "' tiết lộ thông tin phiên làm việc, không được phép.");
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
        String safeSql = select.toString();
        Optional<String> backstop = lexicalBackstop(sql);
        if (backstop.isEmpty()) {
            backstop = lexicalBackstop(safeSql);
        }
        if (backstop.isPresent()) {
            return GuardResult.blocked(backstop.get());
        }
        return GuardResult.allowed(safeSql);
    }

    /** @param name unquoted, lowercased, schema-stripped function name */
    public static boolean isBlockedFunction(String name) {
        if (BLOCKED_FUNCTIONS.contains(name) || name.contains("_to_xml")) {
            return true;
        }
        return BLOCKED_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static final String DOLLAR_REASON = "Không hỗ trợ chuỗi dollar-quoted ($$), hãy dùng dấu nháy đơn '...'.";

    /**
     * Fail-closed lexical layer over SQL text, independent of the AST walk. String literals (standard
     * with '' escape, and E'...' with backslash escapes), comments and quoted identifiers are lexed
     * properly; anything it cannot lex with certainty (unterminated string/comment/identifier, any
     * '$' outside a string literal) is blocked. Outside literals it blocks blocked-function calls,
     * pg_* / information_schema names and session keyword columns.
     */
    static Optional<String> lexicalBackstop(String sql) {
        int n = sql.length();
        int i = 0;
        char prev = ' ';
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'') {
                i = skipString(sql, i, false);
                if (i < 0) {
                    return Optional.of(UNTERMINATED_REASON);
                }
                prev = '\'';
                continue;
            }
            if (c == '$') {
                return Optional.of(DOLLAR_REASON);
            }
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    return Optional.of(UNTERMINATED_REASON);
                }
                i = end + 2;
                continue;
            }
            boolean quoted = c == '"';
            if (quoted || Character.isLetter(c) || c == '_') {
                int start = quoted ? i + 1 : i;
                int j = start;
                if (quoted) {
                    while (j < n && !(sql.charAt(j) == '"'
                            && !(j + 1 < n && sql.charAt(j + 1) == '"'))) {
                        j += sql.charAt(j) == '"' ? 2 : 1;
                    }
                    if (j >= n) {
                        return Optional.of(UNTERMINATED_REASON);
                    }
                } else {
                    while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
                        j++;
                    }
                }
                String name = sql.substring(start, j).toLowerCase(Locale.ROOT);
                i = quoted ? j + 1 : j;
                if (!quoted && name.equals("e") && i < n && sql.charAt(i) == '\'') {
                    i = skipString(sql, i, true);
                    if (i < 0) {
                        return Optional.of(UNTERMINATED_REASON);
                    }
                    prev = '\'';
                    continue;
                }
                int k = i;
                while (k < n && Character.isWhitespace(sql.charAt(k))) {
                    k++;
                }
                boolean call = k < n && sql.charAt(k) == '(';
                boolean qualified = prev == '.' || k < n && sql.charAt(k) == '.';
                if (call && isBlockedFunction(name)) {
                    return Optional.of("Hàm '" + name + "' bị cấm vì lý do an toàn.");
                }
                if (name.startsWith("pg_") || name.equals("information_schema")) {
                    return Optional.of("Tên '" + name + "' thuộc catalog hệ thống, không được phép truy vấn.");
                }
                if (!quoted && !qualified && KEYWORD_COLUMNS.contains(name)) {
                    return Optional.of("Từ khóa '" + name + "' tiết lộ thông tin phiên làm việc, không được phép.");
                }
                prev = 'a';
                continue;
            }
            if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i++;
                }
                prev = '0';
                continue;
            }
            if (!Character.isWhitespace(c)) {
                prev = c;
            }
            i++;
        }
        return Optional.empty();
    }

    private static final String UNTERMINATED_REASON = "SQL không hợp lệ: chuỗi hoặc chú thích không được đóng.";

    /** @return index just after the closing quote, or -1 if the literal is unterminated */
    private static int skipString(String sql, int openQuote, boolean backslashEscapes) {
        int n = sql.length();
        int i = openQuote + 1;
        while (i < n) {
            char ch = sql.charAt(i);
            if (backslashEscapes && ch == '\\') {
                i += 2;
                continue;
            }
            if (ch == '\'') {
                if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    private static boolean isAllowedTable(Table table) {
        // Exactly schema.table; a database part (db.schema.table) or missing schema is rejected.
        if (table.getDatabase() != null && table.getDatabase().getDatabaseName() != null) {
            return false;
        }
        return table.getSchemaName() != null && ALLOWED_SCHEMA.equals(fold(table.getSchemaName()));
    }

    private static void applyLimit(Select select) {
        // JSqlParser attaches a trailing "UNION ... LIMIT n" / FETCH to the last (unparenthesized)
        // branch; in Postgres it applies to the whole set operation, so treat it as the outermost one.
        Select holder = select;
        if (select instanceof SetOperationList union && union.getLimit() == null && union.getFetch() == null
                && union.getSelects() != null && !union.getSelects().isEmpty()) {
            Select last = union.getSelects().get(union.getSelects().size() - 1);
            if (last instanceof PlainSelect && (last.getLimit() != null || last.getFetch() != null)) {
                holder = last;
            }
        }
        long cap = MAX_ROWS;
        Limit limit = holder.getLimit();
        if (limit != null && limit.getRowCount() instanceof LongValue rows) {
            cap = Math.min(cap, rows.getValue());
        }
        Fetch fetch = holder.getFetch();
        if (fetch != null) {
            cap = Math.min(cap, fetchRows(fetch));
            holder.setFetch(null);
        }
        if (limit == null) {
            limit = new Limit();
            holder.setLimit(limit);
        }
        // Over the cap, LIMIT ALL / NULL, or an expression we cannot prove is <= MAX_ROWS: use the cap.
        limit.setLimitAll(false);
        limit.setLimitNull(false);
        limit.setRowCount(new LongValue(cap));
    }

    /** Row count of FETCH FIRST n ROWS ONLY; anything else (WITH TIES, expression) counts as MAX_ROWS. */
    private static long fetchRows(Fetch fetch) {
        if (fetch.toString().toUpperCase(Locale.ROOT).contains("TIES")) {
            return MAX_ROWS;
        }
        if (fetch.getExpression() instanceof LongValue rows) {
            return rows.getValue();
        }
        return fetch.getExpression() == null && fetch.getFetchJdbcParameter() == null
                ? fetch.getRowCount() : MAX_ROWS;
    }

    /** Postgres folding: unquoted identifiers lowercase, quoted ones compare exactly. */
    static String fold(String identifier) {
        String n = identifier.strip();
        if (n.length() >= 2 && n.startsWith("\"") && n.endsWith("\"")) {
            return n.substring(1, n.length() - 1).replace("\"\"", "\"");
        }
        return n.toLowerCase(Locale.ROOT);
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
        boolean tableStatement;
        String keywordColumn;
        private final Deque<Scope> scopes = new ArrayDeque<>();

        private static final class Scope {
            final Set<String> names = new HashSet<>();
            final boolean recursive;

            Scope(boolean recursive) {
                this.recursive = recursive;
            }
        }

        /** WITH RECURSIVE written once applies to every item of that WITH clause. */
        private void pushScope(Select select) {
            boolean recursive = select.getWithItemsList() != null
                    && select.getWithItemsList().stream().anyMatch(WithItem::isRecursive);
            scopes.push(new Scope(recursive));
        }

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
            String n = fold(table.getName());
            return scopes.stream().anyMatch(frame -> frame.names.contains(n));
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

        private void walkExpression(Object node) {
            if (node instanceof Expression e) {
                e.accept(expressions, null);
            }
        }

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
            public <S> StringBuilder visit(Column column, S context) {
                String name = column.getColumnName();
                if (column.getTable() == null && name != null && !name.startsWith("\"")
                        && KEYWORD_COLUMNS.contains(name.toLowerCase(Locale.ROOT))) {
                    keywordColumn = name.toLowerCase(Locale.ROOT);
                }
                return super.visit(column, context);
            }

            @Override
            public <S> StringBuilder visit(IsDistinctExpression expr, S context) {
                walkExpression(expr.getLeftExpression());
                walkExpression(expr.getRightExpression());
                return super.visit(expr, context);
            }

            @Override
            public <S> StringBuilder visit(JsonOperator expr, S context) {
                walkExpression(expr.getLeftExpression());
                walkExpression(expr.getRightExpression());
                return super.visit(expr, context);
            }

            @Override
            public <S> StringBuilder visit(JsonExpression expr, S context) {
                walkExpression(expr.getExpression());
                if (expr.getIdentList() != null) {
                    for (java.util.Map.Entry<Expression, String> ident : expr.getIdentList()) {
                        walkExpression(ident.getKey());
                    }
                }
                return super.visit(expr, context);
            }

            @Override
            public <S> StringBuilder visit(JsonFunction expr, S context) {
                if (expr.getKeyValuePairs() != null) {
                    for (JsonKeyValuePair pair : expr.getKeyValuePairs()) {
                        walkExpression(pair.getKey());
                        walkExpression(pair.getValue());
                    }
                }
                if (expr.getExpressions() != null) {
                    for (JsonFunctionExpression e : expr.getExpressions()) {
                        walkExpression(e.getExpression());
                    }
                }
                return super.visit(expr, context);
            }

            @Override
            public <S> StringBuilder visit(JsonAggregateFunction expr, S context) {
                walkExpression(expr.getExpression());
                walkExpression(expr.getKey());
                walkExpression(expr.getValue());
                walkExpression(expr.getFilterExpression());
                if (expr.getExpressionOrderByElements() != null) {
                    for (OrderByElement order : expr.getExpressionOrderByElements()) {
                        walkExpression(order.getExpression());
                    }
                }
                return super.visit(expr, context);
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
            public <S> StringBuilder visit(TableStatement table, S context) {
                tableStatement = true;
                return super.visit(table, context);
            }

            @Override
            public <S> StringBuilder visit(ParenthesedFromItem item, S context) {
                // The deparser prints the joins inside the parentheses as text; walk them here.
                if (item.getJoins() != null) {
                    for (Join join : item.getJoins()) {
                        join.getRightItem().accept((FromItemVisitor<StringBuilder>) this, null);
                        if (join.getOnExpressions() != null) {
                            for (Expression on : join.getOnExpressions()) {
                                on.accept(expressions, null);
                            }
                        }
                    }
                }
                return super.visit(item, context);
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
                String alias = withItem.getAliasName() == null ? null : fold(withItem.getAliasName());
                Scope frame = scopes.peek();
                if (frame != null && frame.recursive && alias != null) {
                    frame.names.add(alias);
                }
                StringBuilder result = super.visit(withItem, context);
                if (alias != null && frame != null) {
                    frame.names.add(alias);
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
                pushScope(plainSelect);
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
                pushScope(list);
                try {
                    return super.visit(list, context);
                } finally {
                    scopes.pop();
                }
            }

            @Override
            public <S> StringBuilder visit(ParenthesedSelect select, S context) {
                pushScope(select);
                try {
                    return super.visit(select, context);
                } finally {
                    scopes.pop();
                }
            }
        }
    }
}
