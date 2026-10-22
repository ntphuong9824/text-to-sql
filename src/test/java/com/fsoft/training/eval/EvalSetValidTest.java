package com.fsoft.training.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.guardrail.GuardResult;
import com.fsoft.training.guardrail.SqlGuard;
import com.fsoft.training.nl2sql.FewShotExample;
import com.fsoft.training.nl2sql.FewShotSelector;
import com.fsoft.training.nl2sql.TextNormalizer;
import com.fsoft.training.support.PostgresIntegrationTest;
import com.fsoft.training.warehouse.QueryResult;
import com.fsoft.training.warehouse.SqlExecutor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EvalSetValidTest extends PostgresIntegrationTest {

    static final Path EVAL_SET = Path.of("eval/eval-set.yaml");

    @Autowired SqlGuard guard;
    @Autowired SqlExecutor executor;
    @Autowired FewShotSelector fewShots;

    private final EvalSet set = EvalSetLoader.load(EVAL_SET);

    private record Gold(String id, String sql) {}

    private List<Gold> allGold() {
        List<Gold> out = new ArrayList<>();
        set.items().forEach(i -> out.add(new Gold(i.id(), i.goldSql())));
        for (EvalSet.Chain c : set.conversations()) {
            for (int t = 0; t < c.turns().size(); t++) {
                out.add(new Gold(c.id() + "/t" + t, c.turns().get(t).goldSql()));
            }
        }
        return out;
    }

    @Test
    void everyGoldSqlPassesGuardAndReturnsRows() {
        for (Gold g : allGold()) {
            GuardResult check = guard.check(g.sql());
            assertThat(check.allowed()).as("guard " + g.id() + ": " + check.reason()).isTrue();
            QueryResult r = executor.execute(check.safeSql());
            assertThat(r.rows()).as("rows of " + g.id()).isNotEmpty();
            assertThat(r.truncated()).as("truncated " + g.id()).isFalse();
        }
    }

    @Test
    void hasExactlyFortySingleQuestionsWithTheAgreedMix() {
        assertThat(set.items()).hasSize(40);
        Map<String, Long> byDifficulty = set.items().stream()
                .collect(Collectors.groupingBy(EvalSet.Item::difficulty, Collectors.counting()));
        assertThat(byDifficulty).containsExactlyInAnyOrderEntriesOf(Map.of("easy", 12L, "medium", 16L, "hard", 12L));
        Map<String, Long> byLang = set.items().stream()
                .collect(Collectors.groupingBy(EvalSet.Item::lang, Collectors.counting()));
        assertThat(byLang).containsExactlyInAnyOrderEntriesOf(Map.of("vi", 24L, "en", 16L));
        assertThat(set.items()).extracting(EvalSet.Item::question).contains("Doanh thu tháng 9 theo khu vực");
    }

    @Test
    void chainsAndAttacksHaveTheAgreedShape() {
        assertThat(set.conversations()).hasSize(5);
        assertThat(set.conversations()).allSatisfy(c -> assertThat(c.turns()).hasSizeBetween(2, 3));
        assertThat(set.attacks()).hasSize(6);
    }

    @Test
    void idsAreUnique() {
        List<String> ids = new ArrayList<>();
        set.items().forEach(i -> ids.add(i.id()));
        set.conversations().forEach(c -> ids.add(c.id()));
        set.attacks().forEach(a -> ids.add(a.id()));
        assertThat(ids).doesNotHaveDuplicates().doesNotContainNull();
    }

    @Test
    void noEvalQuestionDuplicatesAFewShotQuestion() {
        Set<String> shots = fewShots.all().stream().map(FewShotExample::question)
                .map(TextNormalizer::normalize).collect(Collectors.toSet());
        List<String> questions = new ArrayList<>();
        set.items().forEach(i -> questions.add(i.question()));
        set.conversations().forEach(c -> c.turns().forEach(t -> questions.add(t.question())));
        set.attacks().forEach(a -> questions.add(a.question()));
        assertThat(questions).map(TextNormalizer::normalize).noneMatch(shots::contains);
        assertThat(shots).isNotEmpty();
    }
}
