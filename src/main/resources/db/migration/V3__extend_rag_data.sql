INSERT INTO tasks(task_number,title,description,status,priority,category,requester_id,assigned_to_id,created_at,resolution_deadline,resolution_comment) VALUES
('INC-1003','Сброс MFA не проходит','Пользователь не может завершить MFA после смены телефона','RESOLVED','HIGH','ACCESS',1,2,now()-interval '15 day',now()-interval '14 day 22 hour','Перепривязали устройство MFA через IdM, синхронизировали токены, проверили вход.'),
('INC-1004','Outlook не подключается к Exchange','Ошибка авторизации в Outlook после изменения политики безопасности','RESOLVED','MEDIUM','ACCESS',1,2,now()-interval '12 day',now()-interval '11 day 20 hour','Очистили учетные данные Windows Credential Manager, повторно выдали OAuth-токен.'),
('INC-1005','Не открывается 1С у отдела закупок','Клиент 1С зависает на этапе инициализации конфигурации','RESOLVED','URGENT','INCIDENT',1,2,now()-interval '10 day',now()-interval '9 day 20 hour','Перезапустили кластер 1С, восстановили блокировки сеансов, проверили доступность SQL.'),
('INC-1006','Сбой печати из SAP','Документы не уходят на сетевой принтер из SAP GUI','RESOLVED','HIGH','INCIDENT',1,2,now()-interval '9 day',now()-interval '8 day 18 hour','Обновили spool-сервис, переподключили очередь печати и драйвер принтера.'),
('INC-1007','Почта не отправляется во внешний домен','SMTP отклоняет отправку с ошибкой relay denied','RESOLVED','HIGH','INCIDENT',1,2,now()-interval '7 day',now()-interval '6 day 16 hour','Добавили разрешение relay для сервисного контура и актуализировали SPF/DKIM.'),
('INC-1008','Нет доступа к файловой шаре отдела','После смены группы AD пользователь не открывает общую папку','RESOLVED','MEDIUM','ACCESS',1,2,now()-interval '6 day',now()-interval '5 day 22 hour','Переинициализировали kerberos ticket, пересчитали ACL, выдали группу доступа повторно.'),
('INC-1009','VPN отваливается через 1-2 минуты','Сессия обрывается после успешной авторизации','RESOLVED','HIGH','ACCESS',1,2,now()-interval '5 day',now()-interval '4 day 22 hour','Отключили конфликтующий профиль, обновили клиент VPN, проверили MTU/маршруты.'),
('INC-1010','Ошибка 503 на портале самообслуживания','Портал недоступен для части пользователей','RESOLVED','URGENT','INCIDENT',1,2,now()-interval '4 day',now()-interval '3 day 18 hour','Переключили трафик на резервный инстанс, очистили кэш gateway, стабилизировали сервис.')
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_base_articles(title,content,category) VALUES
('Восстановление доступа после блокировки AD','Порядок разблокировки учетной записи, сброса пароля и проверки синхронизации с SSO.','ACCESS'),
('Чек-лист диагностики VPN','Проверка клиента, сертификатов, MFA, маршрутов, DNS и MTU при обрывах VPN-сессии.','ACCESS'),
('Ошибки Outlook/Exchange после смены пароля','Очистка сохраненных credential, обновление токенов и повторная привязка профиля.','ACCESS'),
('Диагностика ошибки 503 на внутренних порталах','Проверка gateway, балансировщика, состояния backend-пулов и кэша.','INCIDENT'),
('Порядок обработки критического простоя 1С','Шаги triage, эскалации и технические проверки кластера 1С и SQL.','INCIDENT'),
('Сбой SMTP relay во внешние домены','Проверка relay-политик, SPF/DKIM/DMARC и журналов MTA.','INCIDENT')
ON CONFLICT DO NOTHING;
