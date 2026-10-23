import { createContext, useContext, useEffect, useState } from 'react'
import api from '../api/client'
import { clearAssocStorage } from '../utils/assocStorage'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user')
    return stored ? JSON.parse(stored) : null
  })
  const [loading, setLoading] = useState(!!localStorage.getItem('token'))

  useEffect(() => {
    if (!localStorage.getItem('token')) return
    api.get('/users/me')
      .then((res) => {
        setUser(res.data)
        localStorage.setItem('user', JSON.stringify(res.data))
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const applyAuth = (data) => {
    // новая сессия не должна видеть состояние предыдущего пользователя
    clearAssocStorage()
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    setUser(data.user)
  }

  const login = async (username, password) => {
    const res = await api.post('/auth/login', { username, password })
    applyAuth(res.data)
  }

  const register = async (username, password) => {
    const res = await api.post('/auth/register', { username, password })
    applyAuth(res.data)
  }

  const logout = () => {
    clearAssocStorage()
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
