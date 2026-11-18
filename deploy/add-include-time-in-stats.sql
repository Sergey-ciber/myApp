-- Флаг учёта времени уровня в общей статистике и рейтинге игры.
-- game_levels.include_time_in_stats: по умолчанию true, чтобы существующее
-- поведение и все старые уровни (включая игру 121) не изменились.
--
-- Скрипт идемпотентен и рассчитан на запуск ДО старта backend: так Hibernate
-- не пытается менять уже приведённую схему сам. На чистой базе таблицы ещё
-- нет — тогда скрипт пропускается, и колонку создаст ddl-auto=update.
--
-- Вручную на существующей базе:
--   docker compose -f docker-compose.prod.yml exec -T db \
--     psql -v ON_ERROR_STOP=1 -U postgres -d taskmanager < deploy/add-include-time-in-stats.sql
DO $$
BEGIN
    IF to_regclass('game_levels') IS NOT NULL THEN
        ALTER TABLE game_levels
            ADD COLUMN IF NOT EXISTS include_time_in_stats boolean;

        UPDATE game_levels
        SET include_time_in_stats = TRUE
        WHERE include_time_in_stats IS NULL;

        ALTER TABLE game_levels
            ALTER COLUMN include_time_in_stats SET DEFAULT TRUE;

        ALTER TABLE game_levels
            ALTER COLUMN include_time_in_stats SET NOT NULL;
    ELSE
        RAISE NOTICE 'game_levels не найден — колонка include_time_in_stats появится при старте backend';
    END IF;
END $$;
