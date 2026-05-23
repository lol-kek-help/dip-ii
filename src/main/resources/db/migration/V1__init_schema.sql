CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR(255) NOT NULL,
  username VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
  id BIGSERIAL PRIMARY KEY,
  task_number VARCHAR(64),
  title VARCHAR(500) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(30),
  priority VARCHAR(30),
  category VARCHAR(30),
  requester_id BIGINT REFERENCES users(id),
  assigned_to_id BIGINT REFERENCES users(id),
  created_at TIMESTAMP,
  resolution_deadline TIMESTAMP,
  resolution_comment TEXT
);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);

CREATE TABLE IF NOT EXISTS knowledge_base_articles (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  content TEXT NOT NULL,
  category VARCHAR(100) NOT NULL
);
