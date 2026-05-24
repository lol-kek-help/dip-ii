CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE vector_records
    ADD COLUMN IF NOT EXISTS embedding_vector vector(128);

CREATE INDEX IF NOT EXISTS idx_vector_records_embedding_vector
    ON vector_records USING ivfflat (embedding_vector vector_cosine_ops)
    WITH (lists = 100);
