CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    task_number VARCHAR(255),
    title VARCHAR(255),
    description VARCHAR(255),
    status VARCHAR(255),
    priority VARCHAR(255),
    category VARCHAR(255),
    requester_id BIGINT REFERENCES users(id),
    assigned_to_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP,
    resolution_deadline TIMESTAMP,
    resolution_comment VARCHAR(255)
);
