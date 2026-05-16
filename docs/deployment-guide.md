# Инструкция по развертыванию (Docker)
1. Установить Docker и Docker Compose.
2. Выполнить `docker compose up --build`.
3. Проверить доступность:
   - `http://localhost:8080/api/auth/login`
   - `http://localhost:5173`
4. Для TLS на тестовом сервере использовать reverse-proxy (Nginx) с сертификатом.
