#!/usr/bin/env bash
# Деплой приложения на сервер.
#
# Выполняется НА СЕРВЕРЕ. Обычно его вызывает GitHub Actions, но можно и вручную:
#   ssh root@SERVER 'bash -s' < deploy/deploy.sh
# или прямо на сервере (секреты подхватятся из уже существующего .env):
#   cd /opt/taskmanager && bash deploy/deploy.sh
#
# Переменные окружения (все необязательные, если есть .env):
#   REPO_URL           адрес репозитория (обязателен при первом запуске)
#   GIT_REF            ветка для выката (по умолчанию main)
#   APP_DIR            каталог приложения (по умолчанию /opt/taskmanager)
#   HTTP_PORT          порт nginx на хосте (по умолчанию 80)
#   POSTGRES_DB        имя БД (по умолчанию taskmanager)
#   POSTGRES_USER      пользователь БД (по умолчанию postgres)
#   POSTGRES_PASSWORD  пароль БД (обязателен)
#   JWT_SECRET         секрет подписи JWT (обязателен)
#   CORS_ORIGINS       публичный адрес приложения (обязателен)
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/taskmanager}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
GIT_REF="${GIT_REF:-main}"

log()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\n!! %s\n' "$*" >&2; exit 1; }

# ---------- значения из существующего .env ----------
# Нужно только для ручного запуска: если переменная не задана снаружи,
# берём её из .env. То, что передал CI, всегда имеет приоритет.
if [ -f "$APP_DIR/.env" ]; then
  load_env() {
    local name="$1" line
    [ -z "${!name+x}" ] || return 0            # уже задана снаружи
    line="$(grep -m1 "^$name=" "$APP_DIR/.env" 2>/dev/null || true)"
    [ -n "$line" ] || return 0                 # нет в .env
    export "$name=${line#*=}"
  }
  load_env HTTP_PORT
  load_env POSTGRES_DB
  load_env POSTGRES_USER
  load_env POSTGRES_PASSWORD
  load_env JWT_SECRET
  load_env CORS_ORIGINS
fi

# ---------- значения по умолчанию ----------
HTTP_PORT="${HTTP_PORT:-80}"
POSTGRES_DB="${POSTGRES_DB:-taskmanager}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"

# ---------- проверки ----------
command -v docker >/dev/null 2>&1 || fail "Docker не установлен. Сначала запустите deploy/server-bootstrap.sh"
docker compose version >/dev/null 2>&1 || fail "Не найден плагин 'docker compose' (нужен Docker Compose v2)"
command -v git >/dev/null 2>&1 || fail "git не установлен"

[ -n "${POSTGRES_PASSWORD:-}" ] || fail "Не задан POSTGRES_PASSWORD"
[ -n "${JWT_SECRET:-}" ]        || fail "Не задан JWT_SECRET"
[ -n "${CORS_ORIGINS:-}" ]      || fail "Не задан CORS_ORIGINS"

# ---------- код ----------
if [ ! -d "$APP_DIR/.git" ]; then
  [ -n "${REPO_URL:-}" ] || fail "В $APP_DIR нет git-репозитория, а REPO_URL не задан"
  if [ -n "$(ls -A "$APP_DIR" 2>/dev/null)" ]; then
    # Каталог уже чем-то занят (например, туда скопировали проект руками):
    # не удаляем содержимое, а превращаем его в рабочую копию репозитория.
    log "В $APP_DIR нет git — инициализирую репозиторий"
    git -C "$APP_DIR" init -q
    git -C "$APP_DIR" remote add origin "$REPO_URL" 2>/dev/null \
      || git -C "$APP_DIR" remote set-url origin "$REPO_URL"
  else
    log "Клонирую $REPO_URL в $APP_DIR"
    mkdir -p "$(dirname "$APP_DIR")"
    git clone -q --branch "$GIT_REF" "$REPO_URL" "$APP_DIR"
  fi
fi

[ -n "${REPO_URL:-}" ] || REPO_URL="$(git -C "$APP_DIR" remote get-url origin)"
log "Обновляю код из $REPO_URL (ветка/тег: $GIT_REF)"
git -C "$APP_DIR" remote set-url origin "$REPO_URL"
git -C "$APP_DIR" fetch --prune origin "$GIT_REF"
# FETCH_HEAD подходит и для ветки, и для тега, и для коммита (origin/$GIT_REF
# не существует для тега). reset --hard: сервер — не место для локальных
# правок, код всегда равен выкачиваемой ревизии (.env не в git, он цел)
git -C "$APP_DIR" reset --hard FETCH_HEAD

# ---------- настройки ----------
# .env перезаписывается на каждом деплое: источник истины — секреты в CI.
# Правки этого файла прямо на сервере будут потеряны при следующем деплое.
log "Записываю $APP_DIR/.env"
umask 077
cat > "$APP_DIR/.env" <<ENV
POSTGRES_DB=$POSTGRES_DB
POSTGRES_USER=$POSTGRES_USER
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
JWT_SECRET=$JWT_SECRET
CORS_ORIGINS=$CORS_ORIGINS
HTTP_PORT=$HTTP_PORT
ENV

# ---------- выкат ----------
log "Собираю образы и поднимаю контейнеры"
cd "$APP_DIR"
docker compose -f "$COMPOSE_FILE" up -d --build --remove-orphans

# ---------- проверка ----------
log "Жду ответа приложения на порту $HTTP_PORT"
page=""; api=""; ok=""
for _ in $(seq 1 40); do
  page=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$HTTP_PORT/" || true)
  api=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$HTTP_PORT/api/boards" || true)
  # 200 на странице + 401/403 на /api означает: nginx, backend и БД живы
  if [ "$page" = "200" ] && { [ "$api" = "401" ] || [ "$api" = "403" ]; }; then
    ok=1
    break
  fi
  sleep 3
done

echo
docker compose -f "$COMPOSE_FILE" ps

if [ -z "$ok" ]; then
  log "Проверка не прошла (страница: ${page:-нет ответа}, /api: ${api:-нет ответа})"
  docker compose -f "$COMPOSE_FILE" logs --tail=80
  fail "Деплой завершился с ошибкой, приложение не отвечает"
fi

log "Готово. Приложение доступно на порту $HTTP_PORT"
docker image prune -f >/dev/null 2>&1 || true
