import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { apiMessage } from '../api/client'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (username.trim().length < 3) {
      setError('Логин должен содержать минимум 3 символа')
      return
    }
    if (password.length < 6) {
      setError('Пароль должен содержать минимум 6 символов')
      return
    }
    if (password !== confirm) {
      setError('Пароли не совпадают')
      return
    }
    setLoading(true)
    setError('')
    try {
      await register(username.trim(), password)
      navigate('/boards')
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-page-overlay" />
      <form className="auth-card" onSubmit={submit}>
        <h1>Регистрация</h1>
        {error && <div className="alert alert-error">{error}</div>}
        <label className="field">
          <span>Логин</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)}
                 placeholder="Придумайте логин" autoFocus />
        </label>
        <label className="field">
          <span>Пароль</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 placeholder="Минимум 6 символов" />
        </label>
        <label className="field">
          <span>Повторите пароль</span>
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
                 placeholder="Ещё раз" />
        </label>
        <button className="btn btn-primary btn-block" disabled={loading}>
          {loading ? 'Создание аккаунта…' : 'Зарегистрироваться'}
        </button>
        <p className="auth-switch">
          Уже есть аккаунт? <Link to="/login">Войти</Link>
        </p>
      </form>
    </div>
  )
}
