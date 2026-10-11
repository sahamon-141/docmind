-- V3: persistent chat history.
CREATE TABLE conversations (
    id         UUID PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_conversations_owner_updated ON conversations (owner_id, updated_at DESC);

CREATE TABLE messages (
    id                UUID PRIMARY KEY,
    conversation_id   UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    -- Document the question was scoped to. Not a foreign key: history must survive document deletion.
    document_id       UUID,
    reply_to_id       UUID REFERENCES messages (id) ON DELETE CASCADE,
    client_message_id UUID,
    response_time_ms  BIGINT,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_messages_status CHECK (status IN ('COMPLETE', 'FAILED'))
);

CREATE INDEX ix_messages_conversation_created ON messages (conversation_id, created_at, id);
CREATE INDEX ix_messages_reply_to ON messages (reply_to_id);
-- Idempotency key that rejects duplicate submissions of the same client message.
CREATE UNIQUE INDEX ux_messages_client_id ON messages (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

-- Citations are a snapshot of the retrieved chunks, so they stay readable after a document is deleted.
CREATE TABLE message_citations (
    message_id       UUID         NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    position         INTEGER      NOT NULL,
    document_id      UUID,
    file_name        VARCHAR(255) NOT NULL,
    chunk_index      INTEGER,
    page_number      INTEGER,
    snippet          TEXT         NOT NULL,
    similarity_score DOUBLE PRECISION,
    PRIMARY KEY (message_id, position)
);
