import api from './client'

export const createTeamGameLobby = (teamId, gameId) =>
  api.post('/team-games/lobby', { teamId, gameId }).then((res) => res.data)

export const getTeamGameLobby = (sessionId) =>
  api.get(`/team-games/lobby/${sessionId}`).then((res) => res.data)

export const startTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/start`).then((res) => res.data)

export const submitTeamAnswer = (sessionId, questionId, answer) =>
  api.post(`/team-games/${sessionId}/answer`, { sessionId, questionId, answer }).then((res) => res.data)

export const requestTeamHint = (sessionId, questionId, hintIndex) =>
  api.post(`/team-games/${sessionId}/hint`, { sessionId, questionId, hintIndex }).then((res) => res.data)

export const getTeamGameState = (sessionId) =>
  api.get(`/team-games/${sessionId}/state`).then((res) => res.data)

export const joinTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/join`).then((res) => res.data)

export const leaveTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/leave`).then((res) => res.data)

export const heartbeatTeamGame = (sessionId) =>
  api.post(`/team-games/${sessionId}/heartbeat`).then((res) => res.data)
