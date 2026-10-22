#!/usr/bin/env bash
# Деплой приложения на сервер.
#
# Выполняется НА СЕРВЕРЕ. Обычно его вызывает GitHub Actions, но можно и вручную:
#   ssh root@SERVER 'bash -s' < deploy/deploy.sh
#   или так, с явным адресом репозитория:
#   ssh root@SERVER "REPO_URL=git@github.com:user/repo.git bash -s" < deploy/deploy.sh
#
# Переменные окружения:
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
HTTP_PORT="${HTTP_PORT:-80}"
POSTGRES_DB="${POSTGRES_DB:-taskmanager}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"

log()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\n!! %s\n' "$*" >&2; exit 1; }

# ---------- проверки ----------
command -v docker >/dev/null 2>&1 || fail "Docker не установлен. Сначала запустите deploy/server-bootstrap.sh"
docker compose version >/dev/null 2>&1 || fail "Не найден плагин 'docker compose' (нужен Docker Compose v2)"
command -v git >/dev/null 2>&1 || fail "git не установлен"

[ -n "${POSTGRES_PASSWORD:-}" ] || fail "Не задан POSTGRES_PASSWORD"
[ -n "${JWT_SECRET:-}" ]        || fail "Не задан JWT_SECRET"
[ -n "${CORS_ORIGINS:-}" ]      || fail "Не задан CORS_ORIGINS"

# ---------- код ----------
if [ -d "$APP_DIR/.git" ]; then
  [ -n "${REPO_URL:-}" ] || REPO_URL="$(git -C "$APP_DIR" remote get-url origin)"
  log "Обновляю код в $APP_DIR (ветка $GIT_REF)"
  git -C "$APP_DIR" remote set-url origin "$REPO_URL"
  git -C "$APP_DIR" fetch --prune origin "$GIT_REF"
  # reset --hard: сервер — не место для локальных правок, поэтому история всегда
  # ровно совпадает с origin/$GIT_REF (файл .env не в git и не пострадает)
  git -C "$APP_DIR" reset --hard "origin/$GIT_REF"
else
  [ -n "${REPO_URL:-}" ] || fail "Каталог $APP_DIR пуст, а REPO_URL не задан"
  log "Клонирую $REPO_URL в $APP_DIR"
  mkdir -p "$(dirname "$APP_DIR")"
  git clone --branch "$GIT_REF" "$REPO_URL" "$APP_DIR"
fi

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
