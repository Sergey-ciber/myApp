import { createContext, useContext, useEffect, useState } from 'react'
import api from '../api/client'
import { clearAssocStorage } from '../utils/assocStorage'

const AuthContext = createContext(null)

/** Декодировать payload JWT без проверки подписи — только для чтения роли в UI. */
function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    return JSON.parse(atob(padded))
  } catch {
    return null
  }
}

/** Привести роль к виду SUPERADMIN: бэкенд может отдать SUPERADMIN или ROLE_SUPERADMIN. */
function normalizeRole(rawRole) {
  if (!rawRole) return ''
  let role = String(rawRole).trim().toUpperCase()
  if (role.startsWith('ROLE_')) role = role.slice('ROLE_'.length)
  return role
}

/** Роль из user или, если её нет в объекте, из claim JWT. */
function normalizeUser(user) {
  if (!user) return user
  let role = normalizeRole(user.role)
  if (!role) {
    const token = localStorage.getItem('token')
    const payload = token ? decodeJwtPayload(token) : null
    const candidates = [payload?.role, payload?.roles, payload?.authorities]
    for (const candidate of candidates) {
      const value = Array.isArray(candidate) ? candidate[0] : candidate
      role = normalizeRole(value)
      if (role) break
    }
  }
  return { ...user, role: role || 'USER' }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user')
    return stored ? normalizeUser(JSON.parse(stored)) : null
  })
  const [loading, setLoading] = useState(!!localStorage.getItem('token'))

  useEffect(() => {
    if (!localStorage.getItem('token')) return
    api.get('/users/me')
      .then((res) => {
        const nextUser = normalizeUser(res.data)
        setUser(nextUser)
        localStorage.setItem('user', JSON.stringify(nextUser))
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const applyAuth = (data) => {
    // новая сессия не должна видеть состояние предыдущего пользователя
    clearAssocStorage()
    localStorage.setItem('token', data.token)
    const nextUser = normalizeUser(data.user)
    localStorage.setItem('user', JSON.stringify(nextUser))
    setUser(nextUser)
  }

  const login = async (username, password) => {
    const res = await api.post('/auth/login', { username, password })
    applyAuth(res.data)
  }

  const register = async (username, password, email) => {
    const res = await api.post('/auth/register', { username, password, email })
    applyAuth(res.data)
  }

  const logout = () => {
    clearAssocStorage()
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setUser(null)
  }

  // Флаг из сессии (JWT/UserResponse) используется для отрисовки кнопок управления.
  const isSuperAdmin = normalizeRole(user?.role) === 'SUPERADMIN'

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, register, logout, isSuperAdmin }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
