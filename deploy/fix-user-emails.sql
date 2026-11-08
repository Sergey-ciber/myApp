-- Подготовка таблицы users к уникальному email (версия с восстановлением пароля).
--
-- Выполните один раз на существующей базе ДО выката новой версии:
--   docker compose -f docker-compose.prod.yml exec -T db \
--     psql -U postgres -d taskmanager < deploy/fix-user-emails.sql
--
-- 1. Пустая строка — это «email не указан», а не отдельное значение.
UPDATE users SET email = NULL WHERE email IS NOT NULL AND btrim(email) = '';

-- 2. Регистронезависимая уникальность для непустых email.
--    Если в базе уже есть дубликаты (например, Foo@mail.ru и foo@mail.ru),
--    сначала устраните их вручную — иначе индекс не создастся.
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower
    ON users (lower(email))
    WHERE email IS NOT NULL;
