CREATE TABLE app.users (
    id            bigserial PRIMARY KEY,
    username      varchar(50) NOT NULL UNIQUE,
    password_hash text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app.conversations (
    id         uuid PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES app.users (id),
    title      varchar(80) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_updated ON app.conversations (user_id, updated_at DESC);

CREATE TABLE app.messages (
    id                 bigserial PRIMARY KEY,
    conversation_id    uuid        NOT NULL REFERENCES app.conversations (id) ON DELETE CASCADE,
    turn_index         int         NOT NULL,
    question           text        NOT NULL,
    rewritten_question text,
    sql                text,
    status             varchar(10) NOT NULL,
    message            text,
    chart              jsonb,
    result             jsonb,
    created_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation ON app.messages (conversation_id, turn_index);
