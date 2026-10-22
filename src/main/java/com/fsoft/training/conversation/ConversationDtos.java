package com.fsoft.training.conversation;

import com.fsoft.training.chart.ChartSpec;
import com.fsoft.training.nl2sql.TurnStatus;
import com.fsoft.training.warehouse.QueryResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConversationDtos {

    private ConversationDtos() {
    }

    public record ChatRequest(
            UUID conversationId,
            @NotBlank(message = "Câu hỏi không được để trống")
            @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự")
            String question) {
    }

    public record TurnDto(int turnIndex, String question, String rewrittenQuestion, String sql, TurnStatus status,
                          String message, QueryResult result, ChartSpec chart, Instant createdAt) {
    }

    public record ChatResponse(UUID conversationId, String title, TurnDto turn) {
    }

    public record ConversationSummary(UUID id, String title, Instant updatedAt) {
    }

    public record ConversationDetail(UUID id, String title, List<TurnDto> turns) {
    }
}
