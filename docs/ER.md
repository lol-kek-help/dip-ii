# ER-схема базы данных

Документ описывает актуальную ER-схему PostgreSQL для системы обработки заявок. В схеме указаны:

- `PK` — первичный ключ таблицы;
- `FK` — внешний (вторичный) ключ на связанную таблицу;
- `UQ` — уникальное ограничение;
- `IDX` — индекс для ускорения поиска.

## ER-диаграмма

```mermaid
erDiagram
    USERS ||--o{ TASKS : requester_id
    USERS ||--o{ TASKS : assigned_to_id
    USERS ||--o{ REFRESH_TOKENS : user_id
    USERS ||--o{ TICKET_COMMENTS : author_id
    USERS ||--o{ TICKET_ATTACHMENTS : uploaded_by
    USERS ||--o{ TICKET_STATUS_HISTORY : changed_by
    USERS ||--o{ AI_RECOMMENDATIONS : created_by_user_id
    USERS ||--o{ AI_RECOMMENDATIONS : evaluated_by_user_id
    USERS ||--o{ AUDIT_LOGS : actor_id
    USERS ||--o{ NOTIFICATIONS : user_id

    TASKS ||--o{ TICKET_COMMENTS : ticket_id
    TASKS ||--o{ TICKET_ATTACHMENTS : ticket_id
    TASKS ||--|| SLA_RECORDS : ticket_id
    TASKS ||--o{ TICKET_STATUS_HISTORY : ticket_id
    TASKS ||--o{ AI_RECOMMENDATIONS : ticket_id

    SLA_POLICIES ||--o{ SLA_RECORDS : policy_id

    USERS {
        bigint id PK
        varchar full_name
        varchar username UQ
        varchar password_hash
        varchar role
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    TASKS {
        bigint id PK
        varchar task_number
        varchar title
        text description
        varchar status
        varchar priority
        varchar category
        bigint requester_id FK
        bigint assigned_to_id FK
        timestamp created_at
        timestamp updated_at
        timestamp resolution_deadline
        text resolution_comment
        varchar created_by
        varchar updated_by
    }

    KNOWLEDGE_BASE_ARTICLES {
        bigint id PK
        varchar title
        text content
        varchar category
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    VECTOR_RECORDS {
        bigint id PK
        varchar source_type UQ
        bigint source_id UQ
        text text_content
        text embedding
        varchar embedding_provider
        integer embedding_dimension
        vector embedding_vector
    }

    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token UQ
        timestamp expires_at
        boolean revoked
    }

    TICKET_STATUSES {
        bigint id PK
        varchar code UQ
        varchar name
        text description
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    TICKET_PRIORITIES {
        bigint id PK
        varchar code UQ
        varchar name
        text description
        int sort_order
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    TICKET_CATEGORIES {
        bigint id PK
        varchar code UQ
        varchar name
        text description
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    TICKET_COMMENTS {
        bigint id PK
        bigint ticket_id FK
        bigint author_id FK
        text comment_text
        boolean internal_comment
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    TICKET_ATTACHMENTS {
        bigint id PK
        bigint ticket_id FK
        bigint uploaded_by FK
        varchar file_name
        varchar file_path
        bigint file_size
        varchar content_type
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    SLA_POLICIES {
        bigint id PK
        varchar name UQ
        varchar category_code
        varchar priority_code
        int first_response_minutes
        int resolution_minutes
        boolean active
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    SLA_RECORDS {
        bigint id PK
        bigint ticket_id FK
        bigint policy_id FK
        timestamp first_response_at
        timestamp resolved_at
        bigint frt_minutes
        bigint mttr_minutes
        boolean violated
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    AUDIT_LOGS {
        bigint id PK
        bigint actor_id FK
        varchar action
        varchar entity_type
        varchar entity_id
        text details
        text before_value
        text after_value
        varchar ip_address
        varchar user_agent
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    NOTIFICATIONS {
        bigint id PK
        bigint user_id FK
        varchar channel
        varchar subject
        text message
        boolean read
        timestamp created_at
        timestamp updated_at
        varchar created_by
        varchar updated_by
    }

    TICKET_STATUS_HISTORY {
        bigint id PK
        bigint ticket_id FK
        varchar from_status
        varchar to_status
        varchar reason
        bigint changed_by FK
        timestamp created_at
    }

    AI_RECOMMENDATIONS {
        bigint id PK
        bigint ticket_id FK
        text recommendation
        text steps
        varchar mode
        text sources
        varchar llm_status
        text raw_model_output
        boolean accepted
        int usefulness_score
        varchar feedback_comment
        bigint created_by_user_id FK
        bigint evaluated_by_user_id FK
        timestamp created_at
        timestamp evaluated_at
    }
```

## Таблицы, поля и ключи

### `users` — пользователи

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор пользователя. |
| `full_name` | `VARCHAR(255)` | `NOT NULL` | ФИО пользователя. |
| `username` | `VARCHAR(100)` | `NOT NULL`, `UQ` | Логин пользователя. |
| `password_hash` | `VARCHAR(255)` | `NOT NULL` | Хэш пароля. |
| `role` | `VARCHAR(20)` | `NOT NULL` | Роль пользователя (`USER`, `OPERATOR`, `ADMIN`). |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `tasks` — заявки/инциденты

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор заявки. |
| `task_number` | `VARCHAR(64)` |  | Номер заявки, например `INC-1001`. |
| `title` | `VARCHAR(500)` | `NOT NULL` | Заголовок заявки. |
| `description` | `TEXT` | `NOT NULL` | Подробное описание проблемы. |
| `status` | `VARCHAR(30)` | `IDX` | Текущий статус заявки. |
| `priority` | `VARCHAR(30)` | `IDX` | Приоритет заявки. |
| `category` | `VARCHAR(30)` |  | Категория заявки. |
| `requester_id` | `BIGINT` | `FK → users(id)` | Пользователь, создавший заявку. |
| `assigned_to_id` | `BIGINT` | `FK → users(id)` | Исполнитель заявки. |
| `created_at` | `TIMESTAMP` |  | Дата создания заявки. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления заявки. |
| `resolution_deadline` | `TIMESTAMP` |  | Плановый срок решения. |
| `resolution_comment` | `TEXT` |  | Комментарий к решению. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `knowledge_base_articles` — статьи базы знаний

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор статьи. |
| `title` | `VARCHAR(500)` | `NOT NULL` | Название статьи. |
| `content` | `TEXT` | `NOT NULL` | Текст статьи. |
| `category` | `VARCHAR(100)` | `NOT NULL` | Категория статьи. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `vector_records` — векторные записи RAG-поиска

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор векторной записи. |
| `source_type` | `VARCHAR(32)` | `NOT NULL`, `UQ(source_type, source_id)`, `IDX` | Тип источника данных. |
| `source_id` | `BIGINT` | `NOT NULL`, `UQ(source_type, source_id)` | Идентификатор источника. |
| `text_content` | `TEXT` | `NOT NULL` | Текст для поиска и эмбеддинга. |
| `embedding` | `TEXT` | `NOT NULL` | Текстовое представление эмбеддинга/метаданных. |
| `embedding_provider` | `VARCHAR(100)` | `NOT NULL`, `DEFAULT 'UNKNOWN'` | Провайдер эмбеддинга. |
| `embedding_dimension` | `INTEGER` |  | Размерность эмбеддинга. |
| `embedding_vector` | `vector(1024)` | `IDX` | Вектор pgvector для семантического поиска. |

### `refresh_tokens` — refresh-токены авторизации

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор токена. |
| `user_id` | `BIGINT` | `NOT NULL`, `FK → users(id) ON DELETE CASCADE`, `IDX` | Владелец токена. |
| `token` | `VARCHAR(255)` | `NOT NULL`, `UQ`, `IDX` | Значение/хэш refresh-токена. |
| `expires_at` | `TIMESTAMP` | `NOT NULL`, `IDX` | Срок действия токена. |
| `revoked` | `BOOLEAN` | `NOT NULL`, `DEFAULT FALSE` | Признак отзыва токена. |

### `ticket_statuses` — справочник статусов заявок

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор статуса. |
| `code` | `VARCHAR(64)` | `NOT NULL`, `UQ` | Код статуса. |
| `name` | `VARCHAR(255)` | `NOT NULL` | Название статуса. |
| `description` | `TEXT` |  | Описание статуса. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `ticket_priorities` — справочник приоритетов

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор приоритета. |
| `code` | `VARCHAR(64)` | `NOT NULL`, `UQ` | Код приоритета. |
| `name` | `VARCHAR(255)` | `NOT NULL` | Название приоритета. |
| `description` | `TEXT` |  | Описание приоритета. |
| `sort_order` | `INT` |  | Порядок сортировки. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `ticket_categories` — справочник категорий

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор категории. |
| `code` | `VARCHAR(64)` | `NOT NULL`, `UQ` | Код категории. |
| `name` | `VARCHAR(255)` | `NOT NULL` | Название категории. |
| `description` | `TEXT` |  | Описание категории. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `ticket_comments` — комментарии к заявкам

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор комментария. |
| `ticket_id` | `BIGINT` | `NOT NULL`, `FK → tasks(id) ON DELETE CASCADE`, `IDX` | Заявка, к которой относится комментарий. |
| `author_id` | `BIGINT` | `NOT NULL`, `FK → users(id)`, `IDX` | Автор комментария. |
| `comment_text` | `TEXT` | `NOT NULL` | Текст комментария. |
| `internal_comment` | `BOOLEAN` | `NOT NULL`, `DEFAULT FALSE` | Признак внутреннего комментария. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `ticket_attachments` — вложения к заявкам

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор вложения. |
| `ticket_id` | `BIGINT` | `NOT NULL`, `FK → tasks(id) ON DELETE CASCADE`, `IDX` | Заявка, к которой прикреплён файл. |
| `uploaded_by` | `BIGINT` | `NOT NULL`, `FK → users(id)`, `IDX` | Пользователь, загрузивший файл. |
| `file_name` | `VARCHAR(500)` | `NOT NULL` | Имя файла. |
| `file_path` | `VARCHAR(1000)` | `NOT NULL` | Путь к файлу. |
| `file_size` | `BIGINT` | `NOT NULL` | Размер файла в байтах. |
| `content_type` | `VARCHAR(255)` |  | MIME-тип файла. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `sla_policies` — политики SLA

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор политики SLA. |
| `name` | `VARCHAR(255)` | `NOT NULL`, `UQ` | Название политики. |
| `category_code` | `VARCHAR(64)` |  | Код категории, для которой действует политика. |
| `priority_code` | `VARCHAR(64)` |  | Код приоритета, для которого действует политика. |
| `first_response_minutes` | `INT` | `NOT NULL` | Норматив первого ответа в минутах. |
| `resolution_minutes` | `INT` | `NOT NULL` | Норматив решения в минутах. |
| `active` | `BOOLEAN` | `NOT NULL`, `DEFAULT TRUE` | Активность политики. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `sla_records` — метрики SLA по заявкам

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор записи SLA. |
| `ticket_id` | `BIGINT` | `NOT NULL`, `FK → tasks(id) ON DELETE CASCADE`, `UQ`, `IDX` | Заявка; связь один-к-одному. |
| `policy_id` | `BIGINT` | `FK → sla_policies(id)` | Применённая политика SLA. |
| `first_response_at` | `TIMESTAMP` |  | Время первого ответа. |
| `resolved_at` | `TIMESTAMP` |  | Время решения заявки. |
| `frt_minutes` | `BIGINT` |  | Фактическое время первого ответа в минутах. |
| `mttr_minutes` | `BIGINT` |  | Фактическое время решения в минутах. |
| `violated` | `BOOLEAN` | `NOT NULL`, `DEFAULT FALSE` | Признак нарушения SLA. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `audit_logs` — журнал аудита

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор записи аудита. |
| `actor_id` | `BIGINT` | `FK → users(id)`, `IDX` | Пользователь, выполнивший действие. |
| `action` | `VARCHAR(128)` | `NOT NULL`, `IDX` | Тип действия. |
| `entity_type` | `VARCHAR(128)` | `NOT NULL`, `IDX(entity_type, entity_id)` | Тип сущности. |
| `entity_id` | `VARCHAR(128)` | `IDX(entity_type, entity_id)` | Идентификатор сущности. |
| `details` | `TEXT` |  | Подробности действия. |
| `before_value` | `TEXT` |  | Значение до изменения. |
| `after_value` | `TEXT` |  | Значение после изменения. |
| `ip_address` | `VARCHAR(64)` |  | IP-адрес пользователя. |
| `user_agent` | `VARCHAR(1000)` |  | User-Agent клиента. |
| `created_at` | `TIMESTAMP` | `IDX` | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `notifications` — уведомления

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор уведомления. |
| `user_id` | `BIGINT` | `NOT NULL`, `FK → users(id) ON DELETE CASCADE`, `IDX(user_id, read)` | Получатель уведомления. |
| `channel` | `VARCHAR(32)` | `NOT NULL` | Канал уведомления. |
| `subject` | `VARCHAR(255)` | `NOT NULL` | Тема уведомления. |
| `message` | `TEXT` | `NOT NULL` | Текст уведомления. |
| `read` | `BOOLEAN` | `NOT NULL`, `DEFAULT FALSE`, `IDX(user_id, read)` | Признак прочтения. |
| `created_at` | `TIMESTAMP` |  | Дата создания записи. |
| `updated_at` | `TIMESTAMP` |  | Дата обновления записи. |
| `created_by` | `VARCHAR(100)` |  | Автор создания записи. |
| `updated_by` | `VARCHAR(100)` |  | Автор последнего изменения. |

### `ticket_status_history` — история изменения статусов

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор записи истории. |
| `ticket_id` | `BIGINT` | `NOT NULL`, `FK → tasks(id) ON DELETE CASCADE`, `IDX` | Заявка, у которой изменён статус. |
| `from_status` | `VARCHAR(30)` |  | Предыдущий статус. |
| `to_status` | `VARCHAR(30)` | `NOT NULL`, `IDX` | Новый статус. |
| `reason` | `VARCHAR(1000)` |  | Причина изменения статуса. |
| `changed_by` | `BIGINT` | `FK → users(id)` | Пользователь, изменивший статус. |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `IDX` | Дата изменения статуса. |

### `ai_recommendations` — рекомендации ИИ по заявкам

| Поле | Тип | Ключи/ограничения | Описание |
|---|---:|---|---|
| `id` | `BIGSERIAL` | `PK` | Идентификатор рекомендации. |
| `ticket_id` | `BIGINT` | `NOT NULL`, `FK → tasks(id) ON DELETE CASCADE`, `IDX` | Заявка, для которой сформирована рекомендация. |
| `recommendation` | `TEXT` | `NOT NULL` | Текст рекомендации. |
| `steps` | `TEXT` |  | Предложенные шаги решения. |
| `mode` | `VARCHAR(64)` |  | Режим генерации рекомендации. |
| `sources` | `TEXT` |  | Источники/статьи базы знаний. |
| `llm_status` | `VARCHAR(128)` |  | Статус ответа LLM. |
| `raw_model_output` | `TEXT` |  | Сырой ответ модели. |
| `accepted` | `BOOLEAN` | `IDX` | Принята ли рекомендация. |
| `usefulness_score` | `INT` |  | Оценка полезности. |
| `feedback_comment` | `VARCHAR(1000)` |  | Комментарий к оценке. |
| `created_by_user_id` | `BIGINT` | `FK → users(id)` | Пользователь, запросивший рекомендацию. |
| `evaluated_by_user_id` | `BIGINT` | `FK → users(id)` | Пользователь, оценивший рекомендацию. |
| `created_at` | `TIMESTAMP` | `NOT NULL` | Дата создания рекомендации. |
| `evaluated_at` | `TIMESTAMP` |  | Дата оценки рекомендации. |

## Список связей

| Связь | Кардинальность | Внешний ключ | Правило удаления |
|---|---:|---|---|
| `users` → `tasks` | `1:N` | `tasks.requester_id → users.id` | Не задано явно. |
| `users` → `tasks` | `1:N` | `tasks.assigned_to_id → users.id` | Не задано явно. |
| `users` → `refresh_tokens` | `1:N` | `refresh_tokens.user_id → users.id` | `ON DELETE CASCADE`. |
| `users` → `ticket_comments` | `1:N` | `ticket_comments.author_id → users.id` | Не задано явно. |
| `users` → `ticket_attachments` | `1:N` | `ticket_attachments.uploaded_by → users.id` | Не задано явно. |
| `users` → `ticket_status_history` | `1:N` | `ticket_status_history.changed_by → users.id` | Не задано явно. |
| `users` → `ai_recommendations` | `1:N` | `ai_recommendations.created_by_user_id → users.id` | Не задано явно. |
| `users` → `ai_recommendations` | `1:N` | `ai_recommendations.evaluated_by_user_id → users.id` | Не задано явно. |
| `users` → `audit_logs` | `1:N` | `audit_logs.actor_id → users.id` | Не задано явно. |
| `users` → `notifications` | `1:N` | `notifications.user_id → users.id` | `ON DELETE CASCADE`. |
| `tasks` → `ticket_comments` | `1:N` | `ticket_comments.ticket_id → tasks.id` | `ON DELETE CASCADE`. |
| `tasks` → `ticket_attachments` | `1:N` | `ticket_attachments.ticket_id → tasks.id` | `ON DELETE CASCADE`. |
| `tasks` → `sla_records` | `1:1` | `sla_records.ticket_id → tasks.id` | `ON DELETE CASCADE`. |
| `tasks` → `ticket_status_history` | `1:N` | `ticket_status_history.ticket_id → tasks.id` | `ON DELETE CASCADE`. |
| `tasks` → `ai_recommendations` | `1:N` | `ai_recommendations.ticket_id → tasks.id` | `ON DELETE CASCADE`. |
| `sla_policies` → `sla_records` | `1:N` | `sla_records.policy_id → sla_policies.id` | Не задано явно. |

## DBML

```dbml
Table users {
  id bigint [pk, increment]
  full_name varchar(255) [not null]
  username varchar(100) [not null, unique]
  password_hash varchar(255) [not null]
  role varchar(20) [not null]
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)
}

Table tasks {
  id bigint [pk, increment]
  task_number varchar(64)
  title varchar(500) [not null]
  description text [not null]
  status varchar(30)
  priority varchar(30)
  category varchar(30)
  requester_id bigint [ref: > users.id]
  assigned_to_id bigint [ref: > users.id]
  created_at timestamp
  updated_at timestamp
  resolution_deadline timestamp
  resolution_comment text
  created_by varchar(100)
  updated_by varchar(100)

  indexes {
    status [name: 'idx_tasks_status']
    priority [name: 'idx_tasks_priority']
  }
}

Table knowledge_base_articles {
  id bigint [pk, increment]
  title varchar(500) [not null]
  content text [not null]
  category varchar(100) [not null]
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)
}

Table vector_records {
  id bigint [pk, increment]
  source_type varchar(32) [not null]
  source_id bigint [not null]
  text_content text [not null]
  embedding text [not null]
  embedding_provider varchar(100) [not null, default: 'UNKNOWN']
  embedding_dimension integer
  embedding_vector vector(1024)

  indexes {
    (source_type, source_id) [unique, name: 'uq_vector_source']
    source_type [name: 'idx_vector_source_type']
    embedding_vector [name: 'idx_vector_records_embedding_vector']
  }
}

Table refresh_tokens {
  id bigint [pk, increment]
  user_id bigint [not null, ref: > users.id, delete: cascade]
  token varchar(255) [not null, unique]
  expires_at timestamp [not null]
  revoked boolean [not null, default: false]

  indexes {
    user_id [name: 'idx_refresh_tokens_user_id']
    expires_at [name: 'idx_refresh_tokens_expires_at']
    token [name: 'idx_refresh_tokens_token_hash']
  }
}

Table ticket_statuses {
  id bigint [pk, increment]
  code varchar(64) [not null, unique]
  name varchar(255) [not null]
  description text
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)
}

Table ticket_priorities {
  id bigint [pk, increment]
  code varchar(64) [not null, unique]
  name varchar(255) [not null]
  description text
  sort_order int
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)
}

Table ticket_categories {
  id bigint [pk, increment]
  code varchar(64) [not null, unique]
  name varchar(255) [not null]
  description text
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)
}

Table ticket_comments {
  id bigint [pk, increment]
  ticket_id bigint [not null, ref: > tasks.id, delete: cascade]
  author_id bigint [not null, ref: > users.id]
  comment_text text [not null]
  internal_comment boolean [not null, default: false]
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)

  indexes {
    ticket_id [name: 'idx_ticket_comments_ticket_id']
    author_id [name: 'idx_ticket_comments_author_id']
  }
}

Table ticket_attachments {
  id bigint [pk, increment]
  ticket_id bigint [not null, ref: > tasks.id, delete: cascade]
  uploaded_by bigint [not null, ref: > users.id]
  file_name varchar(500) [not null]
  file_path varchar(1000) [not null]
  file_size bigint [not null]
  content_type varchar(255)
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)

  indexes {
    ticket_id [name: 'idx_ticket_attachments_ticket_id']
    uploaded_by [name: 'idx_ticket_attachments_uploaded_by']
  }
}

Table sla_policies {
  id bigint [pk, increment]
  name varchar(255) [not null, unique]
  category_code varchar(64)
  priority_code varchar(64)
  first_response_minutes int [not null]
  resolution_minutes int [not null]
  active boolean [not null, default: true]
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)
}

Table sla_records {
  id bigint [pk, increment]
  ticket_id bigint [not null, unique, ref: > tasks.id, delete: cascade]
  policy_id bigint [ref: > sla_policies.id]
  first_response_at timestamp
  resolved_at timestamp
  frt_minutes bigint
  mttr_minutes bigint
  violated boolean [not null, default: false]
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)

  indexes {
    ticket_id [name: 'idx_sla_records_ticket_id']
  }
}

Table audit_logs {
  id bigint [pk, increment]
  actor_id bigint [ref: > users.id]
  action varchar(128) [not null]
  entity_type varchar(128) [not null]
  entity_id varchar(128)
  details text
  before_value text
  after_value text
  ip_address varchar(64)
  user_agent varchar(1000)
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)

  indexes {
    actor_id [name: 'idx_audit_logs_actor_id']
    (entity_type, entity_id) [name: 'idx_audit_logs_entity']
    created_at [name: 'idx_audit_logs_created_at']
    action [name: 'idx_audit_logs_action']
  }
}

Table notifications {
  id bigint [pk, increment]
  user_id bigint [not null, ref: > users.id, delete: cascade]
  channel varchar(32) [not null]
  subject varchar(255) [not null]
  message text [not null]
  read boolean [not null, default: false]
  created_at timestamp
  updated_at timestamp
  created_by varchar(100)
  updated_by varchar(100)

  indexes {
    (user_id, read) [name: 'idx_notifications_user_read']
  }
}

Table ticket_status_history {
  id bigint [pk, increment]
  ticket_id bigint [not null, ref: > tasks.id, delete: cascade]
  from_status varchar(30)
  to_status varchar(30) [not null]
  reason varchar(1000)
  changed_by bigint [ref: > users.id]
  created_at timestamp [not null]

  indexes {
    ticket_id [name: 'idx_ticket_status_history_ticket_id']
    to_status [name: 'idx_ticket_status_history_status']
    created_at [name: 'idx_ticket_status_history_created_at']
  }
}

Table ai_recommendations {
  id bigint [pk, increment]
  ticket_id bigint [not null, ref: > tasks.id, delete: cascade]
  recommendation text [not null]
  steps text
  mode varchar(64)
  sources text
  llm_status varchar(128)
  raw_model_output text
  accepted boolean
  usefulness_score int
  feedback_comment varchar(1000)
  created_by_user_id bigint [ref: > users.id]
  evaluated_by_user_id bigint [ref: > users.id]
  created_at timestamp [not null]
  evaluated_at timestamp

  indexes {
    ticket_id [name: 'idx_ai_recommendations_ticket_id']
    accepted [name: 'idx_ai_recommendations_accepted']
  }
}
```
