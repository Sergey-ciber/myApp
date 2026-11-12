import api from './client'

/** Список всех игр платформы: [{ id, title, createdAt, active, creatorId, creatorUsername }]. */
export const fetchGames = () => api.get('/games').then((res) => res.data)

/** Игра для прохождения: только стартовые слова + прогресс текущего пользователя. */
export const fetchGame = (id) => api.get(`/games/${id}`).then((res) => res.data)

/** Полная игра со всеми словами — автору или супер-администратору, для формы редактирования. */
export const fetchGameForEdit = (id) => api.get(`/games/${id}/edit`).then((res) => res.data)

/** Старт/продолжение попытки: возвращает { game, progress } текущего пользователя. */
export const startGame = (id) => api.post(`/games/${id}/start`).then((res) => res.data)

/** Проверка ответа на сервере: { column (1..5), position (1..N), answer } → { correct, progress }. */
export const checkAnswer = (gameId, column, position, answer, questionId) =>
  api.post(`/games/${gameId}/check`, { column, position, answer, questionId }).then((res) => res.data)

/** Начать заново: обнуляет прогресс только текущего пользователя. */
export const restartGame = (id) => api.post(`/games/${id}/restart`).then((res) => res.data)

/** Взять подсказку: сервер проверяет время и открывает первые буквы неразгаданных слов. */
export const requestHint = (id) => api.post(`/games/${id}/hint`).then((res) => res.data)

/** Подсказка конкретного вопроса обычного уровня: { questionId, hintIndex } → { firstLetter, text, imageUrl }. */
export const revealQuestionHint = (id, questionId, hintIndex) =>
  api.post(`/games/${id}/question-hint`, { questionId, hintIndex }).then((res) => res.data)

/** Время вышло: сервер раскрывает правильный ответ колонки 5 (рекорд не записывается). */
export const requestTimeout = (id) => api.post(`/games/${id}/timeout`).then((res) => res.data)

/** Создать игру. Принимает { title, timeLimitMinutes, columns }, автор — текущий пользователь. */
export const createGame = (game) => api.post('/games', game).then((res) => res.data)

/** Обновить игру (свою или любую — для супер-администратора). */
export const updateGame = (id, game) => api.put(`/games/${id}`, game).then((res) => res.data)

/** Открыть игру игроку по умолчанию (свою или любую — для супер-администратора). */
export const activateGame = (id) => api.post(`/games/${id}/activate`).then((res) => res.data)

/** Удалить игру (свою или любую — для супер-администратора). */
export const deleteGame = (id) => api.delete(`/games/${id}`)

/**
 * Сохранить время прохождения после победы.
 * levelsStats — детализация по уровням: сервер проверяет по ней,
 * что игра пройдена до конца, и считает общее время сам.
 */
export const submitResult = (gameId, timeSeconds, levelsStats) =>
  api.post(`/games/${gameId}/results`, { timeSeconds, levelsStats }).then((res) => res.data)

/** Рейтинг игры: [{ place, userId, username, timeSeconds, completedAt }]. */
export const fetchLeaderboard = (gameId) =>
  api.get(`/games/${gameId}/leaderboard`).then((res) => res.data)

/** Сохранить прогресс игры (автосохранение): уровень и JSON-состояние. */
export const saveGameProgress = (gameId, body) =>
  api.post(`/games/${gameId}/progress`, body).then((res) => res.data)

/** Загрузить сохранённый прогресс игры. */
export const loadGameProgress = (gameId) =>
  api.get(`/games/${gameId}/progress`).then((res) => (res.status === 204 || res.data == null ? null : res.data))

/** Очистить сохранённый прогресс игры (выход или полное прохождение). */
export const clearGameProgress = (gameId) =>
  api.delete(`/games/${gameId}/progress`).then((res) => res.data)

/** Загрузить медиафайл игры: возвращает публичный URL. */
export const uploadGameMedia = (file) => {
  const form = new FormData()
  form.append('file', file)
  return api.post('/files', form).then((res) => res.data.url)
}
