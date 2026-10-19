package com.fsoft.training.warehouse;

import com.fsoft.training.guardrail.SqlGuard;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Second guardrail layer: asks the Postgres planner (EXPLAIN) what the query really touches, so name,
 * schema, CTE and view resolution is done by Postgres rather than by walking the SQL AST.
 */
@Component
public class PlanInspector {

    private static final String ALLOWED_SCHEMA = "dw";
    private static final Pattern CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_$]*)\\s*\\(");

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public PlanInspector(@Qualifier("analyst") JdbcTemplate analystJdbcTemplate) {
        this.jdbc = analystJdbcTemplate;
    }

    /** @return a Vietnamese reason if the plan breaks policy; empty if fine or if EXPLAIN itself fails. */
    public Optional<String> violation(String safeSql) {
        String json;
        try {
            json = jdbc.queryForObject("EXPLAIN (VERBOSE, FORMAT JSON) " + safeSql, String.class);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(walk(mapper.readTree(json)));
    }

    private String walk(JsonNode node) {
        if (node.isObject()) {
            JsonNode relation = node.get("Relation Name");
            if (relation != null && relation.isString()) {
                JsonNode schema = node.get("Schema");
                String schemaName = schema != null && schema.isString() ? schema.asString() : "?";
                if (!ALLOWED_SCHEMA.equals(schemaName)) {
                    return "Truy vấn đọc bảng '" + schemaName + "." + relation.asString()
                            + "' ngoài schema được phép (" + ALLOWED_SCHEMA + ").";
                }
            }
            JsonNode function = node.get("Function Name");
            if (function != null && function.isString()) {
                String name = function.asString().toLowerCase(Locale.ROOT);
                if (SqlGuard.isBlockedFunction(name)) {
                    return blocked(name);
                }
            }
            for (var entry : node.properties()) {
                String reason = walk(entry.getValue());
                if (reason != null) {
                    return reason;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String reason = walk(child);
                if (reason != null) {
                    return reason;
                }
            }
        } else if (node.isString()) {
            return scanText(node.asString());
        }
        return null;
    }

    private static String scanText(String text) {
        Matcher m = CALL.matcher(stripLiterals(text));
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            if (SqlGuard.isBlockedFunction(name)) {
                return blocked(name);
            }
        }
        return null;
    }

    /** Removes single-quoted literals ('' is an escaped quote) so strings never look like calls. */
    private static String stripLiterals(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (in && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    in = !in;
                    sb.append(' ');
                }
            } else if (!in) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String blocked(String name) {
        return "Hàm '" + name + "' bị cấm vì lý do an toàn.";
    }
}
