-- Подготовка таблицы users к уникальному email (версия с восстановлением пароля).
--
-- Скрипт идемпотентен. deploy/deploy.sh применяет его автоматически после
-- старта backend (к этому моменту колонки email/reset_token уже созданы).
-- Вручную на существующей базе:
--   docker compose -f docker-compose.prod.yml exec -T db \
--     psql -v ON_ERROR_STOP=1 -U postgres -d taskmanager < deploy/fix-user-emails.sql
--
-- На очень старой базе колонки email ещё может не быть: тогда скрипт
-- пропускается, и его нужно запустить повторно после первого старта backend.
DO $$
DECLARE
    duplicates integer;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'users'
          AND column_name = 'email'
    ) THEN
        RAISE NOTICE 'users.email не найден — миграция пропущена, запустите её после старта backend';
        RETURN;
    END IF;

    -- 1. Пустая строка — это «email не указан», а не отдельное значение.
    UPDATE users SET email = NULL WHERE email IS NOT NULL AND btrim(email) = '';

    -- 2. Понятная ошибка вместо невнятного сбоя CREATE INDEX.
    SELECT count(*) INTO duplicates
    FROM (
        SELECT lower(email)
        FROM users
        WHERE email IS NOT NULL
        GROUP BY lower(email)
        HAVING count(*) > 1
    ) d;

    IF duplicates > 0 THEN
        RAISE EXCEPTION 'В users % дубликатов email без учёта регистра. Устраните их и повторите.', duplicates;
    END IF;

    -- 3. Регистронезависимая уникальность для непустых email.
    CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower
        ON users (lower(email))
        WHERE email IS NOT NULL;
END $$;
