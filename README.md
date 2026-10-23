# Task Manager

Full-stack приложение для управления досками и заданиями (Trello-подобное).

- **Backend:** Java 17, Spring Boot 3 (Spring Security + JWT, Spring Data JPA), Maven
- **Frontend:** React 18 + Vite, React Router, Axios
- **База данных:** PostgreSQL 16

## Функциональность

- Регистрация и авторизация по логину/паролю (JWT)
- Настройки профиля (полное имя, email) и загрузка аватара
- Создание, редактирование и удаление досок (цвет, название, описание)
- Работа с заданиями на доске в виде канбан-колонок: «К выполнению», «В работе», «Готово»
- Карточка задания: название, описание, статус, срок, удаление
- Раздел «Игра в ассоциации»: пользователь создаёт игру-пирамиду из 31 слова
  (16 + 8 + 4 + 2 + 1), любой авторизованный игрок разгадывает её, подсказки
  открываются по парам слов
- Игра принадлежит создателю: редактировать и удалять её может только автор,
  остальные видят игру в общем списке и играют
- Таймер прохождения, сохранение результата после победы и рейтинг игры
  (место, логин, время, дата)

## Структура проекта

```
DemoProject/
├── backend/                  # Spring Boot REST API (порт 8080)
│   ├── Dockerfile            # сборка и запуск backend в контейнере
│   └── src/main/resources/application.yml
├── frontend/                 # React SPA (порт 5173)
│   ├── Dockerfile            # сборка статики + nginx
│   └── nginx.conf            # раздача SPA и прокси /api
├── docker-compose.yml        # только PostgreSQL (локальная разработка)
├── docker-compose.prod.yml   # PostgreSQL + backend + frontend (сервер)
├── .env.example              # шаблон настроек для развёртывания
├── start-backend.cmd         # запуск backend на Windows
└── start-frontend.cmd        # запуск frontend на Windows
```

## Быстрый старт

### Вариант 1: скрипты (Windows)

В корне проекта два файла — просто запустите их двойным кликом, каждый в своём окне:

| Файл | Что делает |
|---|---|
| `start-backend.cmd` | проверяет PostgreSQL на 5433 и занятость порта 8080, затем `mvn spring-boot:run` |
| `start-frontend.cmd` | при первом запуске делает `npm install`, затем `npm run dev` на порту 5173 |

Сообщения в скриптах намеренно написаны латиницей: cmd.exe некорректно
разбирает .cmd-файлы, содержащие кириллицу в UTF-8 (ломаются строки).

### Вариант 2: вручную

#### 1. PostgreSQL

Приложение настроено на **локальный PostgreSQL**: `localhost:5433`, база
`taskmanager`, пользователь `postgres`, пароль `serg6363`. Создайте базу, если
её ещё нет:

```sql
CREATE DATABASE taskmanager;
```

Таблицы (`users`, `boards`, `tasks`, `games`, `game_words`, `results`,
`user_progress`) создаются автоматически (Hibernate `ddl-auto: update`).

Настройки подключения — в `backend/src/main/resources/application.yml`.
Любое значение можно переопределить переменными окружения, не меняя файл:

| Переменная | Значение по умолчанию |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/taskmanager` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `serg6363` |

Альтернатива — PostgreSQL в Docker (`docker compose up -d`, порт **5434**,
пароль `postgres`). Чтобы использовать его, переопределите подключение:

```bash
DB_URL=jdbc:postgresql://localhost:5434/taskmanager DB_PASSWORD=postgres mvn spring-boot:run
```

#### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

#### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Откройте http://localhost:5173 — запросы к `/api` проксируются на backend.

## Структура REST API

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/auth/register` | Регистрация `{username, password}` → JWT + пользователь |
| POST | `/api/auth/login` | Авторизация `{username, password}` → JWT + пользователь |
| GET | `/api/users/me` | Текущий пользователь |
| PUT | `/api/users/me` | Обновление профиля `{fullName, email}` |
| POST | `/api/users/me/avatar` | Загрузка аватара (multipart, поле `file`) |
| GET | `/api/files/{name}` | Отдача файла аватара |
| GET | `/api/boards` | Список досок текущего пользователя |
| POST | `/api/boards` | Создание доски `{title, description, color}` |
| GET | `/api/boards/{id}` | Доска |
| PUT | `/api/boards/{id}` | Редактирование доски |
| DELETE | `/api/boards/{id}` | Удаление доски (вместе с заданиями) |
| GET | `/api/boards/{id}/tasks` | Задания доски |
| POST | `/api/boards/{id}/tasks` | Создание задания |
| PUT | `/api/boards/tasks/{taskId}` | Редактирование задания |
| PATCH | `/api/boards/tasks/{taskId}/move` | Смена статуса/позиции `{status, position}` |
| DELETE | `/api/boards/tasks/{taskId}` | Удаление задания |
| GET | `/api/games` | Все игры платформы: `{id, title, createdAt, active, creatorId, creatorUsername}` |
| POST | `/api/games` | Создание игры `{title, columns}`, автор — текущий пользователь |
| GET | `/api/games/{id}` | Игра для игрока: название, автор, 16 стартовых слов и **свой** прогресс (ответов колонок 2–5 в ответе нет) |
| GET | `/api/games/{id}/edit` | Полная игра с 5 колонками для формы (только создатель, иначе 403) |
| PUT | `/api/games/{id}` | Обновление игры (только её создатель, иначе 403); прогресс игроков сбрасывается |
| POST | `/api/games/{id}/activate` | Игра по умолчанию для режима игрока (только создатель) |
| DELETE | `/api/games/{id}` | Удаление игры со словами, результатами и прогрессом (только создатель) |
| POST | `/api/games/{id}/start` | Открыть/продолжить свою попытку → `{game, progress}` |
| POST | `/api/games/{id}/check` | Проверка ответа на сервере `{column, position, answer}` → `{correct, progress}` |
| POST | `/api/games/{id}/restart` | «Начать заново»: очистить свой прогресс и запустить таймер → `progress` |
| POST | `/api/games/{id}/results` | Сохранить время прохождения `{timeSeconds}` → 201 (время сервера важнее клиентского) |
| GET | `/api/games/{id}/leaderboard` | Рейтинг игры: лучшее время каждого игрока |

Все эндпоинты, кроме `/api/auth/**` и `/api/files/**`, требуют заголовок
`Authorization: Bearer <token>`.

## Игра в ассоциации

Раздел `/associations` — переключатель режима «Администратор» / «Игрок».
Игра — это пирамида из 31 слова: 16 + 8 + 4 + 2 + 1. Ответ в колонке N+1
загадывается по паре слов (2i−1, 2i) из колонки N:

```json
{
  "title": "Животный мир",
  "columns": [
    ["хобот", "Африка", "горб", "пустыня", "... ещё 12 слов"],
    ["слон", "верблюд", "... ещё 6 ответов"],
    ["травоядные", "... ещё 3"],
    ["звери", "млекопитающие"],
    ["животные"]
  ]
}
```

- **Администратор** заполняет название и все 31 поле; пустые поля подсвечиваются,
  сохранение идёт в `POST/PUT /api/games`. В списке видны все игры платформы:
  у своих есть кнопки «Редактировать» и «Удалить», у чужих — только «Играть».
  «Играть» у своей игры ещё и делает её игрой по умолчанию.
- **Игрок** выбирает любую игру из списка (в подписи — автор). Ответы сравниваются
  без учёта регистра и пробелов по краям; проверка идёт **на сервере**
  (`POST /api/games/{id}/check`), клиент получает только `correct: true/false`.
  Верное слово блокирует поле и подсвечивает карточку зелёным, неверное — красным
  до следующего ввода. Подсказка карточки открывается, когда разгаданы оба слова
  её пары (в колонке 2 — сразу). Верное слово в колонке 5 сразу даёт победу.
- **Ответы игры не уходят клиенту**: `GET /api/games/{id}` и `/start` отдают только
  16 стартовых слов, а полные 5 колонок доступны лишь автору через `/edit`.
- **Прогресс хранится на сервере** в таблице `user_progress` (уникальная пара
  «пользователь + игра»). Пользователь видит только свой прогресс: при входе другим
  аккаунтом поля пустые, карточки нейтральные, таймер начинается с нуля.
  Перезагрузка страницы прогресс не сбрасывает, «Начать заново» — сбрасывает.
- **Таймер** стартует при открытии игры и останавливается на верном слове колонки 5.
  Момент старта и время финиша хранятся на сервере (`user_progress`), поэтому F5
  не обнуляет время, а результат нельзя подделать клиентским значением.
  После победы время уходит в `POST /api/games/{id}/results`, а в окне победы
  появляется «🏆 Поздравляем! Вы победитель! Ваше время: X сек.».
- **Рейтинг игры** — таблица «Место / Игрок / Время / Дата»: в зачёт идёт лучшая
  попытка каждого пользователя, сортировка по возрастанию времени. Таблица видна
  и в интерфейсе игры, и в окне победы.
- Режим и выбранная игра хранятся в `localStorage` под ключами с id пользователя
  (`assoc:mode:<id>`, `assoc:game:<id>`) и очищаются при входе/выходе.

## Сборка релизов

```bash
cd backend && mvn package      # jar: backend/target/taskmanager-backend-1.0.0.jar
cd frontend && npm run build   # статика: frontend/dist
```

Аватары сохраняются в каталоге `backend/uploads/`.

## Развёртывание на удалённом сервере

Понадобится Linux-сервер (Ubuntu 22.04+), Docker с плагином Compose, ~2 ГБ RAM
и открытый порт 80 (или 443). Готовы `docker-compose.prod.yml` (PostgreSQL +
backend + frontend на nginx) и `Dockerfile` для каждой части.

### 1. Установить Docker на сервере

На актуальных выпусках (Ubuntu 22.04/24.04) достаточно:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # затем перелогиниться
```

На выпусках, где стандартная поддержка уже закончилась (Ubuntu 20.04 «focal»),
этот установщик падает на пакете `docker-model-plugin`, которого для focal нет,
и из-за этого не ставит вообще ничего. Там репозиторий подключается вручную:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo tee /etc/apt/keyrings/docker.asc >/dev/null
sudo chmod a+r /etc/apt/keyrings/docker.asc
CODENAME=$(. /etc/os-release && echo "$VERSION_CODENAME")
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $CODENAME stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

Проще всего не делать это руками, а сразу запустить `deploy/server-bootstrap.sh`
(см. раздел «Деплой из git») — он ставит Docker именно так, создаёт каталог
приложения и ключ для доступа к репозиторию.

### 2. Скопировать проект

```bash
git clone <адрес-репозитория> taskmanager && cd taskmanager
# либо с локальной машины:
# scp -r C:\projects\DemoProject user@SERVER_IP:~/taskmanager
```

### 3. Создать `.env`

```bash
cp .env.example .env
nano .env
```

| Переменная | Что указать |
|---|---|
| `POSTGRES_PASSWORD` | надёжный пароль БД |
| `JWT_SECRET` | случайные ≥32 символа: `openssl rand -base64 48` |
| `CORS_ORIGINS` | **публичный адрес приложения**, например `http://203.0.113.10` или `https://tasks.example.com` |
| `HTTP_PORT` | порт nginx на хосте (по умолчанию 80) |

`CORS_ORIGINS` — самое важное: браузер присылает этот адрес в заголовке
`Origin` для POST/PUT/PATCH, и если он не совпадёт, запросы вернут **403**
(проверено). Указывайте схему и домен ровно так, как их видит пользователь.

### 4. Запустить

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps          # статусы
docker compose -f docker-compose.prod.yml logs -f backend
```

Приложение доступно на `http://SERVER_IP:${HTTP_PORT}`. Порт 8080 наружу не
публикуется — backend доступен только из внутренней сети Docker, `/api`
проксирует nginx (поэтому фронтенд и API живут на одном origin).

### 5. Обновление версии

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

### 6. Резервные копии

```bash
# база
docker compose -f docker-compose.prod.yml exec -T db \
  pg_dump -U postgres taskmanager > backup-$(date +%F).sql
# аватары (именованный volume uploads)
docker run --rm -v demoproject_uploads:/data -v $(pwd):/out alpine \
  tar czf /out/uploads-$(date +%F).tar.gz -C /data .
```

### 7. HTTPS

Проще всего поставить на хост Caddy или nginx+certbot и проксировать на
`127.0.0.1:${HTTP_PORT}` (в `.env` тогда укажите `CORS_ORIGINS=https://домен`,
`HTTP_PORT=8080` или другой локальный порт). Caddy получит сертификат сам:

```
tasks.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Затем откройте нужные порты: `sudo ufw allow 80,443/tcp`.

### Заметки по эксплуатации

- **Внешняя БД.** Если вместо контейнера `db` используете managed PostgreSQL,
  уберите сервис `db` и задайте backend-переменные `DB_URL`, `DB_USERNAME`,
  `DB_PASSWORD` в compose-файле.
- **JWT_SECRET менять нельзя** на работающем приложении — это разлогинит всех
  пользователей.
- **Аватары** лежат на локальном volume `uploads`. При запуске нескольких
  реплик backend их нужно вынести в объектное хранилище (S3 и т.п.).
- **Без Docker:** `mvn package` даёт jar — запускайте его через systemd, а
  `frontend/dist` раздавайте nginx-ом с тем же правилом `try_files ... /index.html`
  и проксированием `/api/` (см. `frontend/nginx.conf`).

## Деплой из git (CI/CD)

Автоматический выкат: `git push` в ветку `main` → GitHub Actions подключается к
серверу по SSH и запускает `deploy/deploy.sh`, который обновляет код, пишет
`.env`, пересобирает образы и проверяет, что сайт и API отвечают. Если проверка
не прошла — деплой падает с красным статусом и показывает логи контейнеров.

```
разработчик --push--> GitHub --Actions--> ssh --> сервер
                                                 |- git fetch + reset --hard
                                                 |- docker compose up -d --build
                                                 '- health-check (страница + /api)
```

| Файл | Назначение |
| --- | --- |
| `deploy/server-bootstrap.sh` | один раз на чистом сервере: git, Docker + Compose, каталог приложения, ключ для клонирования репозитория |
| `deploy/deploy.sh` | сам выкат (запускается на сервере) |
| `.github/workflows/deploy.yml` | вызывает `deploy.sh` по push в `main` |

### Шаг 1. Репозиторий

```bash
git remote add origin git@github.com:USER/REPO.git
git push -u origin main
```

Локальный репозиторий уже создан и первый коммит сделан — осталось добавить
remote и запушить. Репозиторий может быть приватным: сервер получит доступ
по deploy-ключу (шаг 3).

### Шаг 2. Подготовка сервера (один раз)

```bash
ssh root@2.27.33.116 'bash -s' < deploy/server-bootstrap.sh
```

Скрипт поставит git, curl, Docker и Compose, создаст `/opt/taskmanager`, сгенерирует
ключ `/root/.ssh/deploy_key` и распечатает его публичную часть.

### Шаг 3. Deploy key в репозитории

GitHub → Settings → Deploy keys → Add deploy key (галочка «только чтение»),
вставить ключ, напечатанный на шаге 2. Проверка на сервере:

```bash
ssh -T git@github.com      # должно ответить "Hi ...! You've successfully authenticated"
```

### Шаг 4. Секреты и переменные в GitHub

Settings → Secrets and variables → Actions.

Secrets:

| Секрет | Значение |
| --- | --- |
| `SSH_HOST` | `2.27.33.116` |
| `SSH_USER` | `root` |
| `SSH_KEY` | приватный ключ, которым вы заходите на сервер (содержимое `~/.ssh/xorek_ed25519`) |
| `POSTGRES_PASSWORD` | пароль БД |
| `JWT_SECRET` | длинная случайная строка (менять на работающем приложении нельзя — разлогинит всех) |
| `CORS_ORIGINS` | `http://2.27.33.116` |

Необязательные: секрет `SSH_PORT` (по умолчанию 22) и переменные (`Variables`)
`HTTP_PORT` (по умолчанию 80), `APP_DIR` (по умолчанию `/opt/taskmanager`),
`REPO_URL` (если репозиторий не на github.com).

### Шаг 5. Выкат

```bash
git push origin main
```

Прогресс — во вкладке Actions; повторный запуск без коммита — кнопкой
«Run workflow». Состояние на сервере:

```bash
cd /opt/taskmanager
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

### Ручной деплой (без CI)

Тот же скрипт можно запускать руками — на сервере он подхватит секреты из
существующего `.env`:

```bash
ssh root@2.27.33.116
cd /opt/taskmanager && bash deploy/deploy.sh          # обновить до последнего main
GIT_REF=v1.2.0 bash deploy/deploy.sh                 # выкатить тег/ветку (откат тоже)
```

### Что важно знать

- **`.env` на сервере принадлежит CI.** Он перезаписывается при каждом деплое из
  секретов GitHub — правки файла прямо на сервере потеряются.
- **Сборка идёт на сервере** (`mvn package` + `vite build` в Docker): нужно
  ~2 ГБ свободной RAM и 5–15 минут на первый выкат. Если сервер слабый, соберите
  образы в CI и публикуйте в GHCR, а на сервере делайте `docker compose pull`.
- **База не теряется:** тома `pgdata` и `uploads` не пересоздаются, `down -v`
  в скрипте не используется.
- **GitLab вместо GitHub:** логика та же, меняется только `.gitlab-ci.yml`
  (вместо `deploy.yml`), а секреты задаются в Settings → CI/CD → Variables.

