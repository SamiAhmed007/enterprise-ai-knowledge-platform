ALTER TABLE chat_sessions
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE documents
    ADD CONSTRAINT documents_status_check
    CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')) NOT VALID;

ALTER TABLE documents VALIDATE CONSTRAINT documents_status_check;

DROP INDEX IF EXISTS idx_chunks_embedding;

CREATE INDEX idx_chunks_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops);
