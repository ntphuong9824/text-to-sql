package com.fsoft.training.conversation;

import com.fsoft.training.chart.ChartSpec;
import com.fsoft.training.conversation.ConversationDtos.TurnDto;
import com.fsoft.training.nl2sql.TurnStatus;
import com.fsoft.training.warehouse.QueryResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class MessageRepository {

    private final JdbcClient jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public MessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int count(UUID conversationId) {
        return jdbc.sql("select count(*) from app.messages where conversation_id = :c")
                .param("c", conversationId).query(Integer.class).single();
    }

    public void insert(UUID conversationId, int turnIndex, String question, String rewritten, String sql,
                       TurnStatus status, String message, QueryResult result, ChartSpec chart) {
        jdbc.sql("insert into app.messages(conversation_id, turn_index, question, rewritten_question, sql, "
                        + "status, message, chart, result) values (:c, :idx, :q, :rq, :sql, :status, :msg, "
                        + "cast(:chart as jsonb), cast(:result as jsonb))")
                .param("c", conversationId).param("idx", turnIndex).param("q", question)
                .param("rq", rewritten).param("sql", sql).param("status", status.name())
                .param("msg", message)
                .param("chart", chart == null ? null : mapper.writeValueAsString(chart))
                .param("result", result == null ? null : mapper.writeValueAsString(result))
                .update();
    }

    /** All turns, oldest first. */
    public List<TurnDto> findAll(UUID conversationId) {
        return jdbc.sql("select turn_index, question, rewritten_question, sql, status, message, "
                        + "chart::text as chart, result::text as result, created_at "
                        + "from app.messages where conversation_id = :c order by turn_index")
                .param("c", conversationId).query(this::map).list();
    }

    private TurnDto map(ResultSet rs, int n) throws SQLException {
        String chart = rs.getString("chart");
        String result = rs.getString("result");
        return new TurnDto(rs.getInt("turn_index"), rs.getString("question"), rs.getString("rewritten_question"),
                rs.getString("sql"), TurnStatus.valueOf(rs.getString("status")), rs.getString("message"),
                result == null ? null : mapper.readValue(result, QueryResult.class),
                chart == null ? null : mapper.readValue(chart, ChartSpec.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
