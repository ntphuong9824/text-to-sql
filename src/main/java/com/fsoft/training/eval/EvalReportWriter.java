package com.fsoft.training.eval;

import com.fsoft.training.eval.EvalReport.CaseResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

/** Writes an {@link EvalReport} as Markdown: summary table, attack outcomes, then every failing case. */
public class EvalReportWriter {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final DateTimeFormatter HEADER_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Path write(EvalReport report, Path dir) {
        LocalDateTime now = LocalDateTime.now();
        Path file = dir.resolve("report-" + FILE_STAMP.format(now) + ".md");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, render(report, now));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write eval report to " + file.toAbsolutePath(), e);
        }
        return file;
    }

    String render(EvalReport report, LocalDateTime when) {
        List<CaseResult> cases = report.cases();
        StringBuilder md = new StringBuilder();
        md.append("# Text-to-SQL eval report\n\n");
        md.append("Generated ").append(HEADER_STAMP.format(when)).append(". ")
                .append(cases.size()).append(" cases.\n\n");

        md.append("## Summary\n\n");
        md.append("| Group | Passed | Total | Rate |\n");
        md.append("|---|---:|---:|---:|\n");
        Predicate<CaseResult> single = c -> EvalReport.SINGLE.equals(c.kind());
        summaryRow(md, "Single questions", cases, single);
        for (String difficulty : List.of("easy", "medium", "hard")) {
            summaryRow(md, "Single, " + difficulty, cases, single.and(c -> difficulty.equals(c.difficulty())));
        }
        for (String lang : List.of("vi", "en")) {
            summaryRow(md, "Single, " + lang, cases, single.and(c -> lang.equals(c.lang())));
        }
        summaryRow(md, "Follow-up turns (index ≥ 1)", cases, CaseResult::isFollowUp);
        summaryRow(md, "Attacks", cases, c -> EvalReport.ATTACK.equals(c.kind()));
        md.append('\n');

        List<CaseResult> attacks = cases.stream().filter(c -> EvalReport.ATTACK.equals(c.kind())).toList();
        if (!attacks.isEmpty()) {
            md.append("## Attacks\n\n");
            md.append("| Id | Question | Status | Outcome | Safe |\n");
            md.append("|---|---|---|---|---|\n");
            for (CaseResult a : attacks) {
                md.append("| ").append(cell(a.id())).append(" | ").append(cell(a.question())).append(" | ")
                        .append(a.status()).append(" | ").append(cell(a.note())).append(" | ")
                        .append(a.passed() ? "yes" : "**NO**").append(" |\n");
            }
            md.append('\n');
        }

        List<CaseResult> failing = cases.stream().filter(c -> !c.passed()).toList();
        md.append("## Failing cases (").append(failing.size()).append(")\n\n");
        if (failing.isEmpty()) {
            md.append("None.\n");
        }
        for (CaseResult c : failing) {
            md.append("### ").append(c.id()).append(" (").append(describe(c)).append(")\n\n");
            md.append("- Question: ").append(c.question()).append('\n');
            if (c.rewrittenQuestion() != null && !c.rewrittenQuestion().equals(c.question())) {
                md.append("- Rewritten: ").append(c.rewrittenQuestion()).append('\n');
            }
            md.append("- Status: ").append(c.status()).append('\n');
            md.append("- Note: ").append(c.note() == null ? "-" : c.note()).append("\n\n");
            if (c.goldSql() != null) {
                md.append("Gold SQL:\n\n").append(sqlBlock(c.goldSql()));
            }
            md.append("Generated SQL:\n\n")
                    .append(c.generatedSql() == null ? "(none)\n\n" : sqlBlock(c.generatedSql()));
        }
        return md.toString();
    }

    private static void summaryRow(StringBuilder md, String label, List<CaseResult> cases,
                                   Predicate<CaseResult> filter) {
        List<CaseResult> group = cases.stream().filter(filter).toList();
        long passed = group.stream().filter(CaseResult::passed).count();
        md.append("| ").append(label).append(" | ").append(passed).append(" | ").append(group.size())
                .append(" | ").append(rate(passed, group.size())).append(" |\n");
    }

    static String rate(long passed, int total) {
        if (total == 0) {
            return "n/a";
        }
        BigDecimal pct = BigDecimal.valueOf(passed * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return pct.toPlainString() + "%";
    }

    private static String describe(CaseResult c) {
        StringBuilder s = new StringBuilder(c.kind());
        if (c.difficulty() != null) {
            s.append(", ").append(c.difficulty());
        }
        if (c.lang() != null) {
            s.append(", ").append(c.lang());
        }
        return s.toString();
    }

    private static String sqlBlock(String sql) {
        return "```sql\n" + sql.strip() + "\n```\n\n";
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replaceAll("\\s+", " ");
    }
}
