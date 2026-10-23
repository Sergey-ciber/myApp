import api from './client'

/** Список всех игр платформы: [{ id, title, createdAt, active, creatorId, creatorUsername }]. */
export const fetchGames = () => api.get('/games').then((res) => res.data)

/** Игра для прохождения: только стартовые слова + прогресс текущего пользователя. */
export const fetchGame = (id) => api.get(`/games/${id}`).then((res) => res.data)

/** Полная игра со всеми словами — только автору, для формы редактирования. */
export const fetchGameForEdit = (id) => api.get(`/games/${id}/edit`).then((res) => res.data)

/** Старт/продолжение попытки: возвращает { game, progress } текущего пользователя. */
export const startGame = (id) => api.post(`/games/${id}/start`).then((res) => res.data)

/** Проверка ответа на сервере: { column (1..5), position (1..N), answer } → { correct, progress }. */
export const checkAnswer = (gameId, column, position, answer) =>
  api.post(`/games/${gameId}/check`, { column, position, answer }).then((res) => res.data)

/** Начать заново: обнуляет прогресс только текущего пользователя. */
export const restartGame = (id) => api.post(`/games/${id}/restart`).then((res) => res.data)

/** Взять подсказку: сервер проверяет время и открывает первые буквы неразгаданных слов. */
export const requestHint = (id) => api.post(`/games/${id}/hint`).then((res) => res.data)

/** Создать игру. Принимает { title, columns }, автор — текущий пользователь. */
export const createGame = (game) => api.post('/games', game).then((res) => res.data)

/** Обновить игру (только свою). */
export const updateGame = (id, game) => api.put(`/games/${id}`, game).then((res) => res.data)

/** Открыть игру игроку по умолчанию (только свою). */
export const activateGame = (id) => api.post(`/games/${id}/activate`).then((res) => res.data)

/** Удалить игру (только свою). */
export const deleteGame = (id) => api.delete(`/games/${id}`)

/** Сохранить время прохождения после победы. */
export const submitResult = (gameId, timeSeconds) =>
  api.post(`/games/${gameId}/results`, { timeSeconds }).then((res) => res.data)

/** Рейтинг игры: [{ place, userId, username, timeSeconds, completedAt }]. */
export const fetchLeaderboard = (gameId) =>
  api.get(`/games/${gameId}/leaderboard`).then((res) => res.data)
