package com.fsoft.training.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.chart.ChartType;
import com.fsoft.training.llm.LlmException;
import com.fsoft.training.support.FakeLlmClient;
import com.fsoft.training.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class Nl2SqlPipelineTest extends PostgresIntegrationTest {

    static final String GOOD_SQL = "SELECT c.region AS region, SUM(f.revenue) AS revenue FROM dw.fact_sales f "
            + "JOIN dw.dim_customer c ON c.customer_key = f.customer_key GROUP BY c.region ORDER BY revenue DESC";
    static final String BAD_SQL = "SELECT c.regionx AS region FROM dw.dim_customer c";

    @Autowired Nl2SqlPipeline pipeline;
    @Autowired FakeLlmClient llm;

    @BeforeEach
    void setUp() {
        llm.reset();
    }

    @Test
    void successPath() {
        llm.enqueue("```sql\n" + GOOD_SQL + ";\n```");
        TurnOutcome out = pipeline.run("doanh thu theo khu vực", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.OK);
        assertThat(out.result().rows()).hasSize(4);
        assertThat(out.chart().type()).isEqualTo(ChartType.BAR);
        assertThat(llm.calls()).hasSize(1);
    }

    @Test
    void historyTriggersRewrite() {
        llm.enqueue("doanh thu tháng 10 theo khu vực", GOOD_SQL);
        List<TurnContext> history = List.of(new TurnContext("doanh thu tháng 9 theo khu vực",
                "doanh thu tháng 9 theo khu vực", GOOD_SQL, List.of("region", "revenue")));
        TurnOutcome out = pipeline.run("còn tháng 10?", history);
        assertThat(out.rewrittenQuestion()).isEqualTo("doanh thu tháng 10 theo khu vực");
        assertThat(out.status()).isEqualTo(TurnStatus.OK);
        assertThat(llm.calls()).hasSize(2);
    }

    @Test
    void guardBlocksDelete() {
        llm.enqueue("DELETE FROM dw.fact_sales");
        TurnOutcome out = pipeline.run("xóa hết doanh thu", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.BLOCKED);
        assertThat(out.sql()).isEqualTo("DELETE FROM dw.fact_sales");
        assertThat(out.result()).isNull();
        assertThat(llm.calls()).hasSize(1);
        assertThat(superuserJdbc().queryForObject("SELECT count(*) FROM dw.fact_sales", Integer.class))
                .isEqualTo(2240);
    }

    @Test
    void selfRepairsAfterDbError() {
        llm.enqueue(BAD_SQL, GOOD_SQL);
        TurnOutcome out = pipeline.run("doanh thu theo khu vực", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.OK);
        assertThat(llm.calls()).hasSize(2);
        var second = llm.calls().get(1);
        assertThat(second.get(second.size() - 1).content()).contains("regionx").contains("does not exist");
    }

    @Test
    void repairGivesUpAfterTwoAttempts() {
        llm.enqueue(BAD_SQL, BAD_SQL, BAD_SQL);
        TurnOutcome out = pipeline.run("doanh thu theo khu vực", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.ERROR);
        assertThat(out.message()).startsWith("Không chạy được câu SQL sau 2 lần sửa: ");
        assertThat(llm.calls()).hasSize(3);
    }

    @Test
    void refuseIsBlockedWithReason() {
        llm.enqueue("-- REFUSE: không được xóa dữ liệu");
        TurnOutcome out = pipeline.run("xóa dữ liệu", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.BLOCKED);
        assertThat(out.message()).isEqualTo("không được xóa dữ liệu");
    }

    @Test
    void llmFailureBecomesError() {
        llm.enqueueFailure(new LlmException("Dịch vụ AI đang bận"));
        TurnOutcome out = pipeline.run("doanh thu", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.ERROR);
        assertThat(out.message()).isEqualTo("Dịch vụ AI đang bận");
    }
}
