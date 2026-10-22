package com.fsoft.training.llm;

public class LlmException extends RuntimeException {

    private final String userMessage;

    public LlmException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public LlmException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    /** Vietnamese message safe to show to end users. */
    public String userMessage() {
        return userMessage;
    }
}
