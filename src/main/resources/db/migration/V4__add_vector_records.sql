CREATE TABLE IF NOT EXISTS vector_records (
  id BIGSERIAL PRIMARY KEY,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  text_content TEXT NOT NULL,
  embedding TEXT NOT NULL,
  CONSTRAINT uq_vector_source UNIQUE (source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_vector_source_type ON vector_records(source_type);
