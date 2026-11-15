import axios from 'axios'
import { clearAssocStorage } from '../utils/assocStorage'

// Таймаут обязателен: без него «зависший» запрос (например, восстановление
// командной сессии) навсегда оставляет интерфейс на экране загрузки.
// Загрузка файлов его отключает — см. timeout: 0 в uploadGameMedia/uploadTeamAvatar.
const api = axios.create({ baseURL: '/api', timeout: 30000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAssocStorage()
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!window.location.pathname.startsWith('/login') &&
          !window.location.pathname.startsWith('/register')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export function apiMessage(error) {
  return error.response?.data?.message || error.message || 'Произошла ошибка'
}

export default api
