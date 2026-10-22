package com.fsoft.training.conversation;

/** Thrown when a conversation does not exist or belongs to another user (deliberately indistinguishable). */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversation not found");
    }
}
