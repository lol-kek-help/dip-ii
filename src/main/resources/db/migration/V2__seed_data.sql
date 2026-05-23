INSERT INTO users(full_name, username, password_hash, role) VALUES
('Иван Петров','user1','$2a$10$yW73hTrE8ToSc6gBc6ql2uQ5/f4RQ6RhQVZgQes4RQzCaQ35Q7jCa','USER'),
('Ольга Оператор','operator1','$2a$10$yW73hTrE8ToSc6gBc6ql2uQ5/f4RQ6RhQVZgQes4RQzCaQ35Q7jCa','OPERATOR'),
('Анна Админ','admin1','$2a$10$yW73hTrE8ToSc6gBc6ql2uQ5/f4RQ6RhQVZgQes4RQzCaQ35Q7jCa','ADMIN')
ON CONFLICT (username) DO NOTHING;

INSERT INTO tasks(task_number,title,description,status,priority,category,requester_id,assigned_to_id,created_at,resolution_deadline,resolution_comment) VALUES
('INC-1001','Нет доступа к VPN','Сотрудник не может подключиться к VPN после смены пароля','NEW','HIGH','ACCESS',1,2,now()-interval '2 hour',now()+interval '22 hour',null),
('INC-1002','Падение 1С','Не открывается база 1С, критичный простой отдела','IN_PROGRESS','URGENT','INCIDENT',1,2,now()-interval '4 hour',now()+interval '4 hour',null)
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_base_articles(title,content,category) VALUES
('Сброс пароля AD и VPN','Проверьте блокировку учетной записи в AD, выполните сброс, синхронизируйте VPN профиль.','ACCESS'),
('Диагностика недоступности 1С','Проверьте доступность сервера, логи кластера, состояние СУБД и сетевые задержки.','INCIDENT'),
('Эскалация критических инцидентов','Приоритет URGENT эскалируется дежурному инженеру в течение 15 минут.','INCIDENT')
ON CONFLICT DO NOTHING;
