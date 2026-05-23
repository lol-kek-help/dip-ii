# Система поддержки пользователей (backend)

## Запуск (нужно добавить переменные среды)
```bash
docker compose up -d 
```
```bash
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
