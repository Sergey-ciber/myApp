-- Разовая миграция для БД, созданных до перехода командных сессий на table team_sessions.
--
-- В старых базах у таблиц team_session_presence, team_session_progress и
-- team_session_events остаются внешние ключи на неиспользуемую (пустую) таблицу
-- team_game_sessions. Из-за них вставка участия падает на уровне БД:
--   ERROR: insert or update on table "team_session_presence" violates foreign key
--          constraint ... Key (session_id)=(N) is not present in table "team_game_sessions"
-- а приложение отдаёт 500 "null id in ... TeamSessionPresence entry".
--
-- Скрипт идемпотентен: удаляет любые FK с этих трёх таблиц на team_game_sessions.
--
-- Применение на сервере с docker compose:
--   docker exec -i taskmanager-db-1 psql -U postgres -d taskmanager \
--     < deploy/fix-legacy-team-game-session-fks.sql

do $$
declare
    r record;
begin
    if to_regclass('team_game_sessions') is null then
        raise notice 'team_game_sessions отсутствует — миграция не нужна';
        return;
    end if;

    for r in
        select c.conrelid::regclass as tbl, c.conname
        from pg_constraint c
        where c.contype = 'f'
          and c.confrelid = 'team_game_sessions'::regclass
          and c.conrelid in (
              'team_session_presence'::regclass,
              'team_session_progress'::regclass,
              'team_session_events'::regclass
          )
    loop
        execute format('alter table %s drop constraint %I', r.tbl, r.conname);
        raise notice 'удалён устаревший FK %.%', r.tbl, r.conname;
    end loop;
end $$;
