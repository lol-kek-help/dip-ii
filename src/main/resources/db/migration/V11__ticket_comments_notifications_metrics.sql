ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS before_value TEXT,
    ADD COLUMN IF NOT EXISTS after_value TEXT,
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64),
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(1000);

CREATE TABLE IF NOT EXISTS ticket_status_history (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    changed_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ticket_status_history_ticket_id ON ticket_status_history(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_status_history_status ON ticket_status_history(to_status);
CREATE INDEX IF NOT EXISTS idx_ticket_status_history_created_at ON ticket_status_history(created_at);

CREATE TABLE IF NOT EXISTS ai_recommendations (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    recommendation TEXT NOT NULL,
    steps TEXT,
    mode VARCHAR(64),
    sources TEXT,
    llm_status VARCHAR(128),
    raw_model_output TEXT,
    accepted BOOLEAN,
    usefulness_score INT,
    feedback_comment VARCHAR(1000),
    created_by_user_id BIGINT REFERENCES users(id),
    evaluated_by_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    evaluated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_recommendations_ticket_id ON ai_recommendations(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ai_recommendations_accepted ON ai_recommendations(accepted);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens(token);
