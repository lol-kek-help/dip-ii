# Support Desk (backend in progress)

## Run
```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Default users (password for all: `password`):
- `user1` (USER)
- `operator1` (OPERATOR)
- `admin1` (ADMIN)

## Implemented in this iteration
- JWT auth endpoints: `/auth/login`, `/auth/logout`, `/auth/refresh`
- Secured task API `/task/**`
- AI API: `/ai/classify`, `/ai/similar`, `/ai/recommend`
- RAG seed via `knowledge_base_articles` + historical tasks
- Flyway migrations and test data

## Environment
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION_SECONDS`
