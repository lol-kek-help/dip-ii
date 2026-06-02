# Система поддержки пользователей (backend + frontend)

## Генерация JWT_SECRET
```bash
[Convert]::ToBase64String((1..64 | ForEach-Object {Get-Random -Maximum 256}))
```

## Быстрый запуск backend

### Вариант 1: полностью в Docker
```bash
docker compose up -d
```

### Вариант 2: БД в Docker + backend локально
```bash
docker compose up -d postgres
./mvnw spring-boot:run
```


## Сохранность данных PostgreSQL

`compose.yaml` использует именованный Docker volume `postgres-data`, поэтому созданные заявки сохраняются при обычном перезапуске backend-приложения и контейнера PostgreSQL:

```bash
docker compose restart postgres
# или просто перезапустите Spring Boot backend в IDE / через ./mvnw spring-boot:run
```

Важно: команда `docker compose down -v` удаляет volume PostgreSQL и вместе с ним все созданные заявки, refresh-токены, SLA и аудит. Для обычной остановки используйте:

```bash
docker compose down
```

Если после перезапуска backend новые заявки пропали, проверьте, что backend подключается к той же базе (`DB_URL`, `DB_USER`, `DB_PASSWORD`) и что volume не был удалён через `down -v` или Docker Desktop.

## Запуск frontend (React + Vite)
Frontend находится в каталоге `frontend/`.

```bash
cd frontend
npm install
npm run dev
```

По умолчанию frontend доступен на `http://localhost:5173`.

### URL backend для frontend
По умолчанию frontend обращается к `http://localhost:8080`.
При необходимости можно переопределить через переменную окружения:

```bash
$env:VITE_API_URL="http://localhost:8080"
```

## Production-сборка frontend
```bash
cd frontend
npm install
npm run build
npm run preview
```

## Тестовые пользователи
Пароль для всех пользователей: `password`
- `user1` (роль `USER`)
- `operator1` (роль `OPERATOR`)
- `admin1` (роль `ADMIN`)

## Документация для ВКР
- Раздел 2.6 «Тестирование системы»: `docs/testing.md`

## Что реализовано на текущем этапе
- JWT-аутентификация: `/auth/login`, `/auth/logout`, `/auth/refresh`
- API обращений: `/tickets/**`
- AI API для оператора: `/ai/classify`, `/ai/similar`, `/ai/recommend`
- База знаний API: `/knowledge`, `/knowledge/{id}`
- Админ API: `/admin/users`, `/admin/audit`, `/admin/dictionaries`
- Комментарии, история статусов и сохранённые AI-рекомендации по обращениям
- Уведомления: `/notifications/**`
- Расширенная SLA-аналитика и метрики качества AI
- Базовый RAG-сценарий через `knowledge_base_articles` и исторические заявки
- Миграции Flyway и тестовые данные

## Переменные окружения backend
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION_SECONDS`

## Переменные окружения для GigaChat
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
