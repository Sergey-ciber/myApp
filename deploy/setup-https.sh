#!/usr/bin/env bash
# Настройка HTTPS на сервере: TLS-прокси Caddy перед контейнером frontend.
#
# Запускается НА СЕРВЕРЕ (идемпотентно, можно повторять):
#   HTTPS_DOMAIN=мойсбой.рф HTTPS_EMAIL=admin@мойсбой.рф bash deploy/setup-https.sh
#
# Что делает:
#   - ставит Caddy из официального репозитория (если ещё не установлен);
#   - пишет /etc/caddy/Caddyfile: домен и www, сертификат Let's Encrypt
#     выпускается и продлевается автоматически;
#   - проксирует запросы на 127.0.0.1:${HTTP_PORT} (nginx контейнера frontend);
#   - HTTP автоматически редиректит на HTTPS;
#   - открывает 80/443 в ufw, если он включён.
#
# Перед запуском:
#   1. A-записи домена и www должны указывать на этот сервер.
#   2. В /opt/taskmanager/.env должно быть:
#        HTTP_PORT=8080
#        CORS_ORIGINS=https://<домен>,https://www.<домен>
#        APP_BASE_URL=https://<домен>
#   3. Контейнер frontend слушает только localhost (docker-compose.prod.yml).
#   4. Порты 80 и 443 свободны: если 443 занят (например, MTProto-прокси),
#      его нужно перенести на другой порт.
set -euo pipefail

HTTP_PORT="${HTTP_PORT:-8080}"
HTTPS_DOMAIN="${HTTPS_DOMAIN:-}"
HTTPS_EMAIL="${HTTPS_EMAIL:-}"
CADDYFILE="${CADDYFILE:-/etc/caddy/Caddyfile}"

log()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\n!! %s\n' "$*" >&2; exit 1; }

[ -n "$HTTPS_DOMAIN" ] || fail "Не задан HTTPS_DOMAIN (например: HTTPS_DOMAIN=мойсбой.рф)"
[ "$(id -u)" = "0" ] || fail "Запускать нужно от root (sudo)"

# Кириллический домен приводим к punycode: в Caddyfile и сертификате
# используется ASCII-форма (xn--...).
to_ascii() {
  local name="$1"
  if LC_ALL=C grep -q '[^ -~]' <<<"$name"; then
    if command -v idn2 >/dev/null 2>&1; then
      idn2 "$name"
    elif command -v python3 >/dev/null 2>&1; then
      python3 -c 'import sys; print(sys.argv[1].lower().encode("idna").decode())' "$name"
    else
      fail "Домен не в ASCII, а idn2/python3 не найдены. Передайте punycode-форму"
    fi
  else
    printf '%s\n' "${name,,}"
  fi
}

DOMAIN="$(to_ascii "$HTTPS_DOMAIN")"
WWW_DOMAIN="www.$DOMAIN"

# ---------- Caddy ----------
if ! command -v caddy >/dev/null 2>&1; then
  log "Устанавливаю Caddy"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https curl gnupg >/dev/null
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
    | gpg --batch --yes --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
    > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -qq
  apt-get install -y -qq caddy >/dev/null
fi

# ---------- проверка портов ----------
# Caddy должен занять 80 и 443. Если их держит кто-то другой (например,
# контейнер frontend на 0.0.0.0:80), сначала освободите порт.
for port in 80 443; do
  if ss -lntH "sport = :$port" 2>/dev/null | grep -q .; then
    if ss -lntp "sport = :$port" 2>/dev/null | grep -q 'caddy'; then
      continue
    fi
    log "ВНИМАНИЕ: порт $port уже занят:"
    ss -lntp "sport = :$port" || true
    fail "Освободите порт $port и запустите скрипт снова"
  fi
done

# ---------- конфигурация ----------
log "Пишу $CADDYFILE ($DOMAIN, $WWW_DOMAIN -> 127.0.0.1:$HTTP_PORT)"
mkdir -p "$(dirname "$CADDYFILE")"
if [ -n "$HTTPS_EMAIL" ]; then
  cat > "$CADDYFILE" <<CADDY
{
	email $HTTPS_EMAIL
}

$DOMAIN, $WWW_DOMAIN {
	encode zstd gzip
	reverse_proxy 127.0.0.1:$HTTP_PORT
}
CADDY
else
  cat > "$CADDYFILE" <<CADDY
$DOMAIN, $WWW_DOMAIN {
	encode zstd gzip
	reverse_proxy 127.0.0.1:$HTTP_PORT
}
CADDY
fi

caddy validate --config "$CADDYFILE" --adapter caddyfile

log "Включаю и перезапускаю Caddy"
systemctl enable caddy >/dev/null 2>&1 || true
systemctl restart caddy

# ---------- firewall ----------
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q 'Status: active'; then
  log "Открываю 80/tcp и 443/tcp в ufw"
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
fi

# ---------- проверка ----------
log "Жду сертификат и ответ https://$DOMAIN"
code=""
for _ in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://$DOMAIN/" || true)
  [ "$code" = "200" ] && break
  sleep 3
done

if [ "$code" != "200" ]; then
  log "HTTPS не отвечает (код: ${code:-нет ответа}). Логи Caddy:"
  journalctl -u caddy --no-pager | tail -40 || true
  fail "Не удалось поднять HTTPS"
fi

log "Готово. https://$DOMAIN и https://$WWW_DOMAIN работают, сертификат продлевается автоматически."
log "Не забудьте: CORS_ORIGINS=https://$DOMAIN,https://$WWW_DOMAIN и APP_BASE_URL=https://$DOMAIN"
