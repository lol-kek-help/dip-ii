# Система поддержки пользователей (backend)

## Запуск
```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

## Тестовые пользователи
Пароль для всех пользователей: `password`
- `user1` (роль `USER`)
- `operator1` (роль `OPERATOR`)
- `admin1` (роль `ADMIN`)

## Что реализовано на текущем этапе
- JWT-аутентификация: `/auth/login`, `/auth/logout`, `/auth/refresh`
- Защищённый API заявок: `/task/**`
- AI API для оператора: `/ai/classify`, `/ai/similar`, `/ai/recommend`
- Базовый RAG-сценарий через `knowledge_base_articles` и исторические заявки
- Миграции Flyway и тестовые данные

## Переменные окружения
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION_SECONDS`


## Переменные окружения для реального GigaChat
- `AI_LLM_ENABLED=true` — включить реальный HTTP-клиент GigaChat.
- `GIGACHAT_AUTH_KEY=<ваш_auth_key>` — ключ для OAuth-обмена (Basic) и автоматического получения access token.
- `GIGACHAT_API_URL` (опционально) — URL API chat/completions.
- `GIGACHAT_AUTH_URL` (опционально) — URL OAuth endpoint (по умолчанию `https://ngw.devices.sberbank.ru:9443/api/v2/oauth`).
- `GIGACHAT_SCOPE` (опционально, по умолчанию `GIGACHAT_API_PERS`).
- `GIGACHAT_MODEL` (опционально, по умолчанию `GigaChat-Pro`).
- `GIGACHAT_TEMPERATURE` (опционально, по умолчанию `0.2`).

Если `AI_LLM_ENABLED=false` (или не задано), используется деградированный `FallbackLlmClient`.


### Примечание по токенам
`access_token` теперь обновляется автоматически внутри backend. В IDEA нужно хранить только `GIGACHAT_AUTH_KEY`, а не вручную обновлять `GIGACHAT_ACCESS_TOKEN`.
