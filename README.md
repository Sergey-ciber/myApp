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

Таблицы (`users`, `boards`, `tasks`) создаются автоматически
(Hibernate `ddl-auto: update`).

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

Все эндпоинты, кроме `/api/auth/**` и `/api/files/**`, требуют заголовок
`Authorization: Bearer <token>`.

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

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # затем перелогиниться
```

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
