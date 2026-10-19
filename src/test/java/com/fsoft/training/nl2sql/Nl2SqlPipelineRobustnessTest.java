package com.fsoft.training.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsoft.training.chart.ChartSuggester;
import com.fsoft.training.guardrail.GuardResult;
import com.fsoft.training.guardrail.SqlGuard;
import com.fsoft.training.llm.LlmClient;
import com.fsoft.training.warehouse.SqlExecutionException;
import com.fsoft.training.warehouse.SqlExecutor;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;

/** Pure unit tests (mocked collaborators) for failure classification in the pipeline. */
class Nl2SqlPipelineRobustnessTest {

    FewShotSelector selector = mock(FewShotSelector.class);
    PromptBuilder prompts = mock(PromptBuilder.class);
    LlmClient llm = mock(LlmClient.class);
    SqlGuard guard = mock(SqlGuard.class);
    SqlExecutor executor = mock(SqlExecutor.class);
    ChartSuggester chart = mock(ChartSuggester.class);
    QuestionRewriter rewriter = mock(QuestionRewriter.class);
    Nl2SqlPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new Nl2SqlPipeline(rewriter, selector, prompts, llm, guard, executor, chart);
        when(rewriter.rewrite(anyString(), anyList())).thenAnswer(i -> i.getArgument(0));
        when(selector.select(anyString(), anyInt())).thenReturn(List.of());
        when(prompts.buildGenerate(anyString(), anyList(), any())).thenReturn(List.of());
        when(llm.complete(anyList())).thenReturn("SELECT 1 FROM dw.dim_date");
        when(guard.check(anyString())).thenReturn(GuardResult.allowed("SELECT 1 FROM dw.dim_date LIMIT 1000"));
    }

    @Test
    void unexpectedRuntimeExceptionBecomesError() {
        when(selector.select(anyString(), anyInt())).thenThrow(new IllegalStateException("boom"));
        TurnOutcome out = pipeline.run("câu hỏi", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.ERROR);
        assertThat(out.message()).isEqualTo("Đã xảy ra lỗi khi xử lý câu hỏi, vui lòng thử lại");
        assertThat(out.message()).doesNotContain("boom");
    }

    @Test
    void statementTimeoutIsNotRepaired() {
        SQLException sql = new SQLException("canceling statement due to statement timeout", "57014");
        when(executor.execute(anyString())).thenThrow(new SqlExecutionException(sql.getMessage(),
                new UncategorizedSQLException("t", "s", sql)));
        TurnOutcome out = pipeline.run("câu hỏi", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.ERROR);
        assertThat(out.message()).isEqualTo("Truy vấn chạy quá lâu (quá 10 giây), hãy thu hẹp câu hỏi");
        verify(llm, times(1)).complete(anyList());
        verify(prompts, never()).buildFix(anyList(), anyString(), anyString());
    }

    @Test
    void springQueryTimeoutIsNotRepaired() {
        when(executor.execute(anyString())).thenThrow(
                new SqlExecutionException("timeout", new QueryTimeoutException("timeout")));
        TurnOutcome out = pipeline.run("câu hỏi", List.of());
        assertThat(out.message()).contains("quá lâu");
        verify(llm, times(1)).complete(anyList());
    }

    @Test
    void connectionFailureIsNotRepaired() {
        SQLException sql = new SQLException("connection refused", "08001");
        when(executor.execute(anyString())).thenThrow(new SqlExecutionException(sql.getMessage(),
                new DataAccessResourceFailureException("conn", sql)));
        TurnOutcome out = pipeline.run("câu hỏi", List.of());
        assertThat(out.status()).isEqualTo(TurnStatus.ERROR);
        assertThat(out.message()).isEqualTo("Không kết nối được cơ sở dữ liệu");
        verify(llm, times(1)).complete(anyList());
    }
}
