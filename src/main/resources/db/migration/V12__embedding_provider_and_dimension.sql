ALTER TABLE vector_records
    ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE vector_records
    ADD COLUMN IF NOT EXISTS embedding_dimension INTEGER;

DROP INDEX IF EXISTS idx_vector_records_embedding_vector;

ALTER TABLE vector_records
    DROP COLUMN IF EXISTS embedding_vector;

ALTER TABLE vector_records
    ADD COLUMN embedding_vector vector(1024);

CREATE INDEX IF NOT EXISTS idx_vector_records_embedding_vector
    ON vector_records USING ivfflat (embedding_vector vector_cosine_ops)
    WITH (lists = 100);
