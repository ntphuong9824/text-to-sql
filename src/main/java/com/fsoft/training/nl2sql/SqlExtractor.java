package com.fsoft.training.nl2sql;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls a single SQL statement out of free-form LLM text. */
public final class SqlExtractor {

    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z]*[ \\t]*\\R?(.*?)```", Pattern.DOTALL);
    private static final Pattern REFUSE = Pattern.compile("^\\s*--\\s*REFUSE\\s*:?\\s*(.*)$", Pattern.DOTALL);

    private SqlExtractor() {}

    /** Empty when the model refused; otherwise the SQL without code fence or trailing semicolons. */
    public static Optional<String> extract(String llmText) {
        String text = body(llmText);
        if (REFUSE.matcher(text).matches()) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    /** The reason after {@code -- REFUSE:}, or null when the text is not a refusal. */
    public static String refuseReason(String llmText) {
        Matcher m = REFUSE.matcher(body(llmText));
        if (!m.matches()) {
            return null;
        }
        String reason = m.group(1).strip();
        return reason.isEmpty() ? "Yêu cầu bị từ chối." : reason;
    }

    private static String body(String llmText) {
        String text = llmText == null ? "" : llmText.strip();
        Matcher fence = FENCE.matcher(text);
        if (fence.find()) {
            text = fence.group(1).strip();
        }
        while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).stripTrailing();
        }
        return text;
    }
}
