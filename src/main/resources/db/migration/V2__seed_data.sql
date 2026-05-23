CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users(full_name, username, password_hash, role) VALUES
('Иван Петров','user1',crypt('password', gen_salt('bf', 10)),'USER'),
('Ольга Оператор','operator1',crypt('password', gen_salt('bf', 10)),'OPERATOR'),
('Анна Админ','admin1',crypt('password', gen_salt('bf', 10)),'ADMIN')
ON CONFLICT (username) DO UPDATE SET
  full_name = EXCLUDED.full_name,
  role = EXCLUDED.role,
  password_hash = crypt('password', gen_salt('bf', 10));

INSERT INTO tasks(task_number,title,description,status,priority,category,requester_id,assigned_to_id,created_at,resolution_deadline,resolution_comment) VALUES
('INC-1001','Нет доступа к VPN','Сотрудник не может подключиться к VPN после смены пароля','NEW','HIGH','ACCESS',1,2,now()-interval '2 hour',now()+interval '22 hour',null),
('INC-1002','Падение 1С','Не открывается база 1С, критичный простой отдела','IN_PROGRESS','URGENT','INCIDENT',1,2,now()-interval '4 hour',now()+interval '4 hour',null)
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_base_articles(title,content,category) VALUES
('Сброс пароля AD и VPN','Проверьте блокировку учетной записи в AD, выполните сброс, синхронизируйте VPN профиль.','ACCESS'),
('Диагностика недоступности 1С','Проверьте доступность сервера, логи кластера, состояние СУБД и сетевые задержки.','INCIDENT'),
('Эскалация критических инцидентов','Приоритет URGENT эскалируется дежурному инженеру в течение 15 минут.','INCIDENT')
ON CONFLICT DO NOTHING;
