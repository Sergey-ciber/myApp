-- Причина завершения попытки: results.finish_reason и user_progress.finish_reason.
--
-- Скрипт идемпотентен и рассчитан на запуск ДО старта backend: так Hibernate
-- не пытается менять уже приведённую схему сам. На чистой базе таблиц ещё нет —
-- тогда скрипт пропускается, и колонки создаст ddl-auto=update.
--
-- Вручную на существующей базе:
--   docker compose -f docker-compose.prod.yml exec -T db \
--     psql -v ON_ERROR_STOP=1 -U postgres -d taskmanager < deploy/add-finish-reason.sql
--
-- Отдельно чиним games.difficulty: исторически колонка была varchar(20),
-- а сущность Game ожидает integer. Hibernate не может аккуратно привести
-- тип с помощью ddl-auto=update, поэтому приводим его здесь.
DO $$
BEGIN
    -- 1. games.difficulty: varchar -> integer, значение по умолчанию 3.
    --    Скрипт запускается на каждом деплое, поэтому тип проверяем заранее:
    --    повторное приведение уже integer-колонки падает на btrim(integer).
    IF to_regclass('games') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'games'
              AND column_name = 'difficulty'
              AND data_type <> 'integer'
        ) THEN
            ALTER TABLE games ALTER COLUMN difficulty DROP DEFAULT;
            ALTER TABLE games
                ALTER COLUMN difficulty TYPE integer
                USING (
                    CASE
                        WHEN difficulty IS NULL OR btrim(difficulty::text) = '' THEN NULL
                        ELSE btrim(difficulty::text)::integer
                    END
                );
            ALTER TABLE games ALTER COLUMN difficulty SET DEFAULT 3;
            RAISE NOTICE 'games.difficulty приведён к integer';
        ELSE
            RAISE NOTICE 'games.difficulty уже integer — миграция не нужна';
        END IF;
        UPDATE games SET difficulty = 3 WHERE difficulty IS NULL;
    ELSE
        RAISE NOTICE 'games не найден — миграция difficulty пропущена';
    END IF;

    -- 2. results.finish_reason: старые записи считаем полностью пройденными.
    IF to_regclass('results') IS NOT NULL THEN
        ALTER TABLE results ADD COLUMN IF NOT EXISTS finish_reason varchar(30);
        UPDATE results SET finish_reason = 'COMPLETED' WHERE finish_reason IS NULL;
    ELSE
        RAISE NOTICE 'results не найден — колонка finish_reason появится при старте backend';
    END IF;

    -- 3. user_progress.finish_reason: NULL — попытка ещё активна.
    --    Для уже завершённых попыток фиксируем COMPLETED.
    IF to_regclass('user_progress') IS NOT NULL THEN
        ALTER TABLE user_progress ADD COLUMN IF NOT EXISTS finish_reason varchar(30);
        UPDATE user_progress
        SET finish_reason = 'COMPLETED'
        WHERE finish_reason IS NULL
          AND finished_seconds IS NOT NULL;
    ELSE
        RAISE NOTICE 'user_progress не найден — колонка finish_reason появится при старте backend';
    END IF;

    -- 4. Попытки, завершённые победой до появления серверного таймера:
    --    раньше finished_seconds не заполнялся, поэтому таймер мог бы принять
    --    такую попытку за активную и закрыть её как TIME_EXPIRED. Победную
    --    попытку узнаём по результату COMPLETED, созданному после её старта.
    IF to_regclass('user_progress') IS NOT NULL AND to_regclass('results') IS NOT NULL THEN
        UPDATE user_progress p
        SET finished_seconds = r.time_seconds,
            updated_at = now()
        FROM (
            SELECT DISTINCT ON (user_id, game_id)
                   user_id, game_id, time_seconds, completed_at
            FROM results
            WHERE finish_reason = 'COMPLETED'
            ORDER BY user_id, game_id, completed_at DESC
        ) r
        WHERE p.user_id = r.user_id
          AND p.game_id = r.game_id
          AND p.finished_seconds IS NULL
          AND p.finish_reason IS NULL
          AND r.completed_at >= p.started_at;
    END IF;

    -- 5. Быстрый поиск активных попыток для серверного таймера.
    --    Частичный индекс: строки завершённых попыток в него не попадают.
    IF to_regclass('user_progress') IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = current_schema()
             AND table_name = 'user_progress'
             AND column_name = 'finished_seconds'
       )
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = current_schema()
             AND table_name = 'user_progress'
             AND column_name = 'finish_reason'
       ) THEN
        CREATE INDEX IF NOT EXISTS idx_user_progress_active
            ON user_progress (id)
            WHERE finished_seconds IS NULL AND finish_reason IS NULL;
    END IF;
END $$;
