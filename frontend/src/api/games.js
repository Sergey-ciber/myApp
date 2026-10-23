import api from './client'

/** Список всех игр платформы: [{ id, title, createdAt, active, creatorId, creatorUsername }]. */
export const fetchGames = () => api.get('/games').then((res) => res.data)

/** Полная игра: { id, title, active, creatorId, creatorUsername, columns: [[16], [8], [4], [2], [1]] }. */
export const fetchGame = (id) => api.get(`/games/${id}`).then((res) => res.data)

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
