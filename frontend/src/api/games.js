import api from './client'

/** Список сохранённых игр: [{ id, title, createdAt, active }]. */
export const fetchGames = () => api.get('/games').then((res) => res.data)

/** Полная игра: { id, title, active, columns: [[16], [8], [4], [2], [1]] }. */
export const fetchGame = (id) => api.get(`/games/${id}`).then((res) => res.data)

/** Создать игру. Принимает { title, columns }. */
export const createGame = (game) => api.post('/games', game).then((res) => res.data)

/** Обновить игру (название и все слова). */
export const updateGame = (id, game) => api.put(`/games/${id}`, game).then((res) => res.data)

/** Сделать игру активной — она откроется игроку по умолчанию. */
export const activateGame = (id) => api.post(`/games/${id}/activate`).then((res) => res.data)

export const deleteGame = (id) => api.delete(`/games/${id}`)
