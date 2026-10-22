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

# Кодовое имя выпуска (focal, jammy...) нужно для репозитория Docker.
# VERSION_CODENAME есть не в каждом /etc/os-release, поэтому есть запасной путь.
CODENAME="${VERSION_CODENAME:-${UBUNTU_CODENAME:-}}"
if [ -z "$CODENAME" ] && command -v lsb_release >/dev/null 2>&1; then
  CODENAME="$(lsb_release -cs 2>/dev/null || true)"
fi
[ -n "$CODENAME" ] || {
  echo "Не удалось определить кодовое имя выпуска — посмотрите /etc/os-release." >&2
  exit 1
}
echo "  кодовое имя выпуска: $CODENAME"

case "${VERSION_ID:-}" in
  18.04|20.04|21.04|21.10|22.10)
    echo "  ВНИМАНИЕ: у этого выпуска закончилась стандартная поддержка."
    echo "  Docker и приложение работать будут, но обновлений безопасности больше нет."
    echo "  Стоит запланировать переезд на Ubuntu 24.04 LTS."
    ;;
esac

command -v apt-get >/dev/null 2>&1 || {
  echo "Этот скрипт рассчитан на Debian/Ubuntu (нужен apt-get)." >&2
  exit 1
}

log "Ставлю git, curl и Docker"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl git openssh-client

# Docker ставим из официального репозитория вручную, а не через get.docker.com:
# тот скрипт проверяет список поддерживаемых выпусков и на Ubuntu 20.04 (focal,
# EOL) падает на пакете docker-model-plugin, которого для focal не существует.
# Здесь же перечислены только те пакеты, что реально есть в репозитории.
install -m 0755 -d /etc/apt/keyrings
curl -fsSL "https://download.docker.com/linux/ubuntu/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Эти пакеты есть не во всех выпусках — без них приложение работает.
for pkg in docker-buildx-plugin docker-ce-rootless-extras; do
  apt-get install -y -qq "$pkg" 2>/dev/null || echo "  $pkg недоступен для $CODENAME — пропускаю (не критично)"
done

systemctl enable --now docker
docker --version
docker compose version
if docker info >/dev/null 2>&1; then
  echo "  демон Docker отвечает"
else
  echo "  ВНИМАНИЕ: демон Docker не отвечает, смотрите: systemctl status docker" >&2
fi

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

# ---------- ключ для входа на сервер (необязательно) ----------
# Положите рядом публичный ключ, которым хотите заходить на сервер без пароля
# (он же потом идёт в секрет SSH_KEY для GitHub Actions):
#   scp ~/.ssh/id_ed25519.pub root@SERVER:/root/ci_key.pub
CI_PUBKEY_FILE="${CI_PUBKEY_FILE:-$HOME/ci_key.pub}"
if [ -f "$CI_PUBKEY_FILE" ]; then
  log "Добавляю публичный ключ из $CI_PUBKEY_FILE"
  mkdir -p "$HOME/.ssh"
  chmod 700 "$HOME/.ssh"
  touch "$HOME/.ssh/authorized_keys"
  chmod 600 "$HOME/.ssh/authorized_keys"
  pubkey="$(head -n1 "$CI_PUBKEY_FILE" | tr -d '\r' | sed 's/[[:space:]]*$//')"
  if [ -n "$pubkey" ] && grep -qF "$pubkey" "$HOME/.ssh/authorized_keys"; then
    echo "  такой ключ уже добавлен"
  elif [ -n "$pubkey" ]; then
    printf '%s\n' "$pubkey" >> "$HOME/.ssh/authorized_keys"
    echo "  ключ добавлен: $(printf '%s' "$pubkey" | awk '{print $1, $NF}')"
  else
    echo "  файл пуст — пропускаю"
  fi
else
  echo
  echo "  Подсказка: положите публичный ключ в $CI_PUBKEY_FILE,"
  echo "  чтобы включить вход по ключу (без пароля)."
fi

cat <<MSG

Сервер готов. Дальше:

1) Добавьте публичный ключ сервера (deploy key) в репозиторий:

  GitHub -> Settings -> Deploy keys -> Add deploy key (доступ только на чтение)
  GitLab -> Settings -> Repository -> Deploy keys

Ключ (файл $KEY_PATH.pub):

$(cat "$KEY_PATH.pub" 2>/dev/null || echo '  (не удалось прочитать ключ)')

2) Задайте секреты в GitHub (Settings -> Secrets and variables -> Actions):
   SSH_HOST, SSH_USER, SSH_KEY, POSTGRES_PASSWORD, JWT_SECRET, CORS_ORIGINS

3) Дальше деплой запускается сам по push в main.

Проверить выкат вручную на сервере:
  cd /opt/taskmanager && bash deploy/deploy.sh
MSG
