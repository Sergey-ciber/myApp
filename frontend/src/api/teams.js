import api from './client'

/** Все команды платформы, отсортированные сервером по числу побед. */
export const fetchTeams = () => api.get('/teams').then((res) => res.data)

/** Детальная карточка команды: статистика, участники и права текущего пользователя. */
export const fetchTeam = (teamId) => api.get(`/teams/${teamId}`).then((res) => res.data)

/** Создать команду: { name, avatarUrl }. Создатель становится администратором. */
export const createTeam = (team) => api.post('/teams', team).then((res) => res.data)

/** Изменить название и/или аватар команды (только создатель/администратор). */
export const updateTeam = (teamId, team) =>
  api.put(`/teams/${teamId}`, team).then((res) => res.data)

/** Удалить команду вместе с составом и приглашениями (только создатель). */
export const deleteTeam = (teamId) => api.delete(`/teams/${teamId}`)

/** Загрузить аватар команды и получить публичный URL. */
export const uploadTeamAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return api.post('/teams/avatar', form).then((res) => res.data.avatarUrl)
}

/** Отправить приглашение пользователю (только создатель команды). */
export const inviteUser = (teamId, userId) =>
  api.post(`/teams/${teamId}/invite`, { userId }).then((res) => res.data)

/** Входящие приглашения текущего пользователя. */
export const fetchInvitations = () => api.get('/users/me/invitations').then((res) => res.data)

export const acceptInvitation = (id) =>
  api.post(`/invitations/${id}/accept`).then((res) => res.data)

export const declineInvitation = (id) =>
  api.post(`/invitations/${id}/decline`).then((res) => res.data)

/** Исключить участника (только создатель команды). */
export const removeTeamMember = (teamId, userId) =>
  api.delete(`/teams/${teamId}/members/${userId}`)

/** Покинуть команду. */
export const leaveTeam = (teamId) => api.delete(`/teams/${teamId}/leave`)

/** Поиск зарегистрированных пользователей для приглашения. */
export const searchUsers = (query) =>
  api.get('/users', { params: { search: query } }).then((res) => res.data)
