package com.fsoft.training.llm;

import java.util.List;

public interface LlmClient {

    /** Sends the conversation to the model and returns the assistant's reply text. */
    String complete(List<ChatMessage> messages);
}
