#!/usr/bin/env bash
# Одноразовая подготовка чистого сервера: Docker + ключ для доступа к репозиторию.
#
# Запуск с локальной машины (нужен работающий SSH-доступ):
#   ssh root@SERVER_IP 'bash -s' < deploy/server-bootstrap.sh
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/taskmanager}"
KEY_PATH="${KEY_PATH:-$HOME/.ssh/deploy_key}"

log() { printf '\n==> %s\n' "$*"; }

log "Система"
. /etc/os-release 2>/dev/null || true
echo "  ${PRETTY_NAME:-неизвестно}, $(uname -m)"

command -v apt-get >/dev/null 2>&1 || {
  echo "Этот скрипт рассчитан на Debian/Ubuntu (нужен apt-get)." >&2
  exit 1
}

log "Ставлю git, curl и Docker"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl git openssh-client
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
else
  echo "  Docker уже установлен"
fi
systemctl enable --now docker
docker compose version

log "Каталог приложения: $APP_DIR"
mkdir -p "$APP_DIR"

log "Ключ для доступа к репозиторию"
if [ ! -f "$KEY_PATH" ]; then
  ssh-keygen -t ed25519 -N '' -f "$KEY_PATH" -C "deploy@$(hostname)" >/dev/null
  echo "  создан $KEY_PATH"
else
  echo "  уже существует: $KEY_PATH"
fi

# Чтобы git clone к GitHub использовал именно этот ключ
mkdir -p "$HOME/.ssh"
if ! grep -q "IdentityFile $KEY_PATH" "$HOME/.ssh/config" 2>/dev/null; then
  {
    echo "Host github.com"
    echo "  IdentityFile $KEY_PATH"
    echo "  IdentitiesOnly yes"
    echo "  StrictHostKeyChecking accept-new"
  } >> "$HOME/.ssh/config"
  chmod 600 "$HOME/.ssh/config"
fi
ssh-keyscan -H github.com >> "$HOME/.ssh/known_hosts" 2>/dev/null || true
chmod 600 "$HOME/.ssh/known_hosts" 2>/dev/null || true

cat <<MSG

Сервер готов. Осталось добавить публичный ключ в репозиторий:

  GitHub -> Settings -> Deploy keys -> Add deploy key (доступ только на чтение)
  GitLab -> Settings -> Repository -> Deploy keys

Ключ (файл $KEY_PATH.pub):

$(cat "$KEY_PATH.pub" 2>/dev/null || echo '  (не удалось прочитать ключ)')

После этого деплой запускается автоматически из CI (push в main).
Ручная проверка на сервере:
  REPO_URL=git@github.com:USER/REPO.git bash deploy/deploy.sh
MSG
