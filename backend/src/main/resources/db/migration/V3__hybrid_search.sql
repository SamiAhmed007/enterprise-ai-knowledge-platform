ALTER TABLE document_chunks
    ADD COLUMN page_number INTEGER;

ALTER TABLE document_chunks
    ADD CONSTRAINT document_chunks_page_number_check
    CHECK (page_number IS NULL OR page_number > 0);

ALTER TABLE document_chunks
    ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_chunks_search_vector ON document_chunks
    USING GIN (search_vector);
