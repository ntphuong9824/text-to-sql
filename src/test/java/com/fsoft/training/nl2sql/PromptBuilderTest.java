package com.fsoft.training.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.llm.ChatMessage;
import com.fsoft.training.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PromptBuilderTest extends PostgresIntegrationTest {

    @Autowired PromptBuilder builder;
    @Autowired FewShotSelector selector;

    @Test
    void generateHasSystemShotsAndQuestionLast() {
        List<FewShotExample> shots = selector.select("doanh thu theo khu vực", 4);
        List<ChatMessage> msgs = builder.buildGenerate("doanh thu theo khu vực", shots, null);
        assertThat(msgs.get(0).role()).isEqualTo("system");
        assertThat(msgs.get(0).content()).contains("dw.fact_sales").contains("REFUSE");
        assertThat(msgs).hasSize(1 + 2 * 4 + 1);
        ChatMessage last = msgs.get(msgs.size() - 1);
        assertThat(last.role()).isEqualTo("user");
        assertThat(last.content()).contains("doanh thu theo khu vực");
    }

    @Test
    void previousSqlIsIncludedInLastMessage() {
        TurnContext prev = new TurnContext("q", "doanh thu 2024", "SELECT 1 FROM dw.dim_date", List.of("a"));
        List<ChatMessage> msgs = builder.buildGenerate("còn 2025?", List.of(), prev);
        assertThat(msgs.get(msgs.size() - 1).content()).contains("SQL của câu trước: SELECT 1 FROM dw.dim_date");
    }

    @Test
    void fixAppendsBadSqlAndError() {
        List<ChatMessage> base = List.of(ChatMessage.system("s"), ChatMessage.user("q"));
        List<ChatMessage> msgs = builder.buildFix(base, "SELECT bad", "column bad does not exist");
        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(2)).isEqualTo(ChatMessage.assistant("SELECT bad"));
        assertThat(msgs.get(3).content()).contains("column bad does not exist");
    }

    @Test
    void rewriteContainsOldRewrittenQuestions() {
        List<TurnContext> history = List.of(
                new TurnContext("a", "doanh thu tháng 9", "SELECT 1", List.of()),
                new TurnContext("b", "doanh thu tháng 10", "SELECT 2", List.of()));
        String all = builder.buildRewrite("còn tháng 11?", history).toString();
        assertThat(all).contains("doanh thu tháng 9").contains("doanh thu tháng 10").contains("còn tháng 11?");
    }
}
