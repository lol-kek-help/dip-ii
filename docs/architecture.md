# ER-диаграмма
```mermaid
flowchart LR

  User[Пользователь / Оператор]

  FE[Frontend<br/>React]
  BE[Backend<br/>Spring Boot]
  DB[(PostgreSQL)]
  AI[AI-модуль<br/>Классификация и поиск похожих случаев]

  User --> FE
  FE -->|REST API| BE
  BE --> DB
  BE --> AI
```
Клиентская часть реализована на React.

Серверная часть — Spring Boot 3.x.

Взаимодействие осуществляется через REST API.

Данные хранятся в PostgreSQL.

Интеллектуальный модуль встроен в backend и используется для: классификации обращения, поиска похожих кейсов, подбора статей из базы знаний
