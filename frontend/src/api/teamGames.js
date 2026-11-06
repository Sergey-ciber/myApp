import api from './client'

/** Подготовка к командной игре: возвращает лобби и (если партия идёт) её сессию. */
export const createTeamGameLobby = (teamId, gameId) =>
  api.post('/team-games/lobby', { teamId, gameId }).then((res) => res.data)

/** Запуск командной игры: создаёт сессию, текущий пользователь становится хостом. */
export const startTeamGameSession = (teamId, gameId) =>
  api.post('/team-games/start', { teamId, gameId }).then((res) => res.data)

/** Активные партии команд пользователя — карточки в списке игр. */
export const fetchActiveTeamSessions = () =>
  api.get('/team-games/active').then((res) => res.data)

/** Полный слепок состояния партии — по нему игрок попадает на текущий момент игры. */
export const getTeamGameState = (sessionId) =>
  api.get(`/team-games/${sessionId}/state`).then((res) => res.data)

/** Присоединиться к идущей партии. */
export const joinTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/join`).then((res) => res.data)

/** Ответ в командной игре: сервер рассылает результат всем участникам. */
export const submitTeamAnswer = (sessionId, questionId, answer) =>
  api.post(`/team-games/${sessionId}/answer`, { sessionId, questionId, answer }).then((res) => res.data)

/** Подсказка: штраф начисляется, событие рассылается всем участникам. */
export const requestTeamHint = (sessionId, questionId, hintIndex) =>
  api.post(`/team-games/${sessionId}/hint`, { sessionId, questionId, hintIndex }).then((res) => res.data)

/** Открыть следующую по порядку подсказку вопроса (сервер проверяет время и порядок). */
export const revealTeamHint = (sessionId, questionId) =>
  api.post(`/team-games/${sessionId}/questions/${questionId}/hints/reveal`).then((res) => res.data)

/** Открыть конкретную подсказку вопроса; повторный вызов идемпотентен. */
export const revealTeamHintById = (sessionId, questionId, hintId) =>
  api.post(`/team-games/${sessionId}/questions/${questionId}/hints/${hintId}/reveal`).then((res) => res.data)

/** Завершить партию — может только хост, иначе сервер вернёт 403. */
export const endTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/end`, { sessionId }).then((res) => res.data)

/** Выйти из партии: партия продолжается, хост передаёт права. */
export const leaveTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/leave`).then((res) => res.data)

/** Отключение (закрыта вкладка). */
export const disconnectTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/disconnect`).then((res) => res.data)

/** Heartbeat: продлевает отметку «онлайн». */
export const heartbeatTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/heartbeat`).then((res) => res.data)

/** Серверная статистика времени по уровням (доступна всем участникам команды). */
export const getTeamGameStatistics = (sessionId) =>
  api.get(`/team-games/${sessionId}/statistics`).then((res) => res.data)
