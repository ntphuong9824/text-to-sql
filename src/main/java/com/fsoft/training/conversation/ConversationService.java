package com.fsoft.training.conversation;

import com.fsoft.training.conversation.ConversationDtos.ChatResponse;
import com.fsoft.training.conversation.ConversationDtos.ConversationDetail;
import com.fsoft.training.conversation.ConversationDtos.ConversationSummary;
import com.fsoft.training.conversation.ConversationDtos.TurnDto;
import com.fsoft.training.nl2sql.Nl2SqlPipeline;
import com.fsoft.training.nl2sql.TurnContext;
import com.fsoft.training.nl2sql.TurnOutcome;
import com.fsoft.training.nl2sql.TurnStatus;
import com.fsoft.training.warehouse.ColumnInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConversationService {

    private static final int MAX_TITLE = 60;
    private static final int MAX_HISTORY = 3;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Nl2SqlPipeline pipeline;
    private final TransactionTemplate tx;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               Nl2SqlPipeline pipeline, TransactionTemplate tx) {
        this.conversations = conversations;
        this.messages = messages;
        this.pipeline = pipeline;
        this.tx = tx;
    }

    /** Runs the pipeline outside any transaction; only the persistence step is transactional. */
    public ChatResponse ask(long userId, UUID conversationIdOrNull, String question) {
        UUID id;
        String title;
        List<TurnContext> history = List.of();
        if (conversationIdOrNull == null) {
            id = UUID.randomUUID();
            title = titleOf(question);
            conversations.create(id, userId, title);
        } else {
            var row = conversations.findOwned(conversationIdOrNull, userId)
                    .orElseThrow(ConversationNotFoundException::new);
            id = row.id();
            title = row.title();
            history = historyOf(messages.findAll(id));
        }

        TurnOutcome outcome = pipeline.run(question, history);

        TurnDto turn = tx.execute(status -> {
            // Serializes concurrent asks on one conversation and detects deletion during the pipeline run.
            if (!conversations.lockOwned(id, userId)) {
                throw new ConversationNotFoundException();
            }
            int index = messages.count(id);
            Instant createdAt = messages.insert(id, index, question, outcome.rewrittenQuestion(), outcome.sql(),
                    outcome.status(), outcome.message(), outcome.result(), outcome.chart());
            conversations.touch(id);
            return new TurnDto(index, question, outcome.rewrittenQuestion(), outcome.sql(), outcome.status(),
                    outcome.message(), outcome.result(), outcome.chart(), createdAt);
        });
        return new ChatResponse(id, title, turn);
    }

    public List<ConversationSummary> list(long userId) {
        return conversations.list(userId);
    }

    public ConversationDetail get(long userId, UUID id) {
        var row = conversations.findOwned(id, userId).orElseThrow(ConversationNotFoundException::new);
        return new ConversationDetail(row.id(), row.title(), messages.findAll(id));
    }

    public void delete(long userId, UUID id) {
        if (!conversations.delete(id, userId)) {
            throw new ConversationNotFoundException();
        }
    }

    static String titleOf(String question) {
        String trimmed = question.trim();
        return trimmed.length() > MAX_TITLE ? trimmed.substring(0, MAX_TITLE) + "…" : trimmed;
    }

    private static List<TurnContext> historyOf(List<TurnDto> turns) {
        List<TurnDto> ok = turns.stream().filter(t -> t.status() == TurnStatus.OK).toList();
        List<TurnDto> recent = ok.subList(Math.max(0, ok.size() - MAX_HISTORY), ok.size());
        List<TurnContext> out = new ArrayList<>();
        for (TurnDto t : recent) {
            List<String> cols = t.result() == null ? List.of()
                    : t.result().columns().stream().map(ColumnInfo::name).toList();
            out.add(new TurnContext(t.question(), t.rewrittenQuestion(), t.sql(), cols));
        }
        return out;
    }
}
