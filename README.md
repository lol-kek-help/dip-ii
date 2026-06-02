# Система поддержки пользователей (backend + frontend)

## CI/CD

В проект добавлен GitHub Actions workflow `.github/workflows/ci-cd.yml`. Для учебного проекта он запускается только вручную через `Actions` → `CI/CD` → `Run workflow`, чтобы конвейер не стартовал автоматически на каждый `push`, `pull request` или тег.

1. `Backend tests` поднимает PostgreSQL с pgvector и выполняет `mvn -B test`.
2. `Frontend checks` устанавливает зависимости через `npm ci` и выполняет `npm run build`.
3. `Delivery artifacts` запускается только после успешного предварительного тестирования, собирает backend JAR и frontend `dist`, затем загружает их как артефакты workflow.

Чтобы сделать скриншот успешного выполнения: откройте вкладку `Actions` в GitHub, выберите workflow `CI/CD`, нажмите на последний успешный запуск с зелёной галочкой и сделайте скриншот страницы, где видны все три успешные jobs (`Backend tests`, `Frontend checks`, `Delivery artifacts`).

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

Если реальный клиент GigaChat возвращает `HTTP 402 Payment Required`, проверьте оплату/баланс, доступность выбранного тарифа и модели (`GIGACHAT_MODEL`), либо временно выставьте `AI_LLM_ENABLED=false`, чтобы приложение продолжило работать на локальном fallback без LLM.

### Примечание по токенам
`access_token` теперь обновляется автоматически внутри backend. В IDEA нужно хранить только `GIGACHAT_AUTH_KEY`, а не вручную обновлять `GIGACHAT_ACCESS_TOKEN`.
