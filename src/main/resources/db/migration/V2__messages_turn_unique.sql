ALTER TABLE app.messages
    ADD CONSTRAINT uq_messages_conversation_turn UNIQUE (conversation_id, turn_index);
