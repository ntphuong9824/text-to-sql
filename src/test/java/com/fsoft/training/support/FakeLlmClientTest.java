package com.fsoft.training.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fsoft.training.llm.ChatMessage;
import com.fsoft.training.llm.LlmException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FakeLlmClientTest {

    private final FakeLlmClient fake = new FakeLlmClient();

    @BeforeEach
    void setUp() {
        fake.reset();
    }

    @Test
    void returnsQueuedResponsesInOrderAndRecordsCalls() {
        fake.enqueue("a", "b");
        List<ChatMessage> first = List.of(ChatMessage.user("q1"));
        assertThat(fake.complete(first)).isEqualTo("a");
        assertThat(fake.complete(List.of(ChatMessage.user("q2")))).isEqualTo("b");
        assertThat(fake.calls()).hasSize(2).first().isEqualTo(first);
    }

    @Test
    void throwsQueuedFailure() {
        fake.enqueueFailure(new LlmException("boom"));
        assertThatThrownBy(() -> fake.complete(List.of())).isInstanceOf(LlmException.class).hasMessage("boom");
    }

    @Test
    void emptyQueueThrows() {
        assertThatThrownBy(() -> fake.complete(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FakeLlmClient: no response queued");
    }
}
