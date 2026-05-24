# DBML
```DBML
Table roles {
  id integer [pk]
  name varchar(50) [not null, unique]
  priority integer
  class varchar(50)
}

Table users {
  id integer [pk]
  username varchar(100) [not null, unique]
  email varchar(150) [unique]
  password_hash text [not null]
  role_id integer [not null]
  is_active boolean [default: true]
  created_at timestamp
}

Table categories {
  id integer [pk]
  name varchar(100) [not null, unique]
  description text
}

Table priorities {
  id integer [pk]
  name varchar(50) [not null, unique]
  level integer [not null]
}


Table tickets {
  id integer [pk]
  title varchar(255) [not null]
  description text [not null]
  status_id integer [not null]
  priority_id integer [not null]
  category_id integer [not null]
  user_id integer [not null]
  assigned_to integer
  created_at timestamp
  updated_at timestamp
}

Table messages {
  id integer [pk]
  ticket_id integer [not null]
  sender_id integer [not null]
  message_text text [not null]
  created_at timestamp
}

Table attachments {
  id integer [pk]
  ticket_id integer [not null]
  file_name varchar(255) [not null]
  file_path text [not null]
  uploaded_at timestamp
}

Table audit_logs {
  id integer [pk]
  user_id integer
  action varchar(255) [not null]
  entity_type varchar(100)
  entity_id integer
  created_at timestamp
}

Ref: users.role_id > roles.id
Ref: tickets.priority_id > priorities.id
Ref: tickets.category_id > categories.id
Ref: tickets.user_id > users.id
Ref: tickets.assigned_to > users.id
Ref: messages.ticket_id > tickets.id
Ref: messages.sender_id > users.id
Ref: attachments.ticket_id > tickets.id
Ref: audit_logs.user_id > users.id
```
