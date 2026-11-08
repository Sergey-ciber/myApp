import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { apiMessage } from '../api/client'

export default function ResetPassword() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const [loading, setLoading] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
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
      await api.post('/auth/reset-password', { token, newPassword: password })
      setDone(true)
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }

  if (!token) {
    return (
      <div className="auth-page">
        <div className="auth-page-overlay" />
        <div className="auth-card">
          <h1>Сброс пароля</h1>
          <div className="alert alert-error">
            Ссылка недействительна: в ней нет токена сброса.
          </div>
          <p className="auth-switch">
            <Link to="/forgot-password">Запросить новую ссылку</Link>
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <div className="auth-page-overlay" />
      <form className="auth-card" onSubmit={submit} noValidate>
        <h1>Сброс пароля</h1>
        {done ? (
          <>
            <div className="alert alert-success">
              Пароль изменён. Теперь можно войти с новым паролем.
            </div>
            <p className="auth-switch">
              <Link to="/login">Перейти ко входу</Link>
            </p>
          </>
        ) : (
          <>
            {error && <div className="alert alert-error">{error}</div>}
            <label className="field">
              <span>Новый пароль</span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                     placeholder="Минимум 6 символов" autoComplete="new-password" autoFocus />
            </label>
            <label className="field">
              <span>Повторите пароль</span>
              <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
                     placeholder="Ещё раз" autoComplete="new-password" />
            </label>
            <button className="btn btn-primary btn-block" disabled={loading}>
              {loading ? 'Сохранение…' : 'Сохранить новый пароль'}
            </button>
            <p className="auth-switch">
              <Link to="/forgot-password">Запросить новую ссылку</Link>
            </p>
          </>
        )}
      </form>
    </div>
  )
}
