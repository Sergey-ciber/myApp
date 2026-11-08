import { useState } from 'react'
import { Link } from 'react-router-dom'
import api, { apiMessage } from '../api/client'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [sent, setSent] = useState(false)
  const [loading, setLoading] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    const value = email.trim()
    if (!value) {
      setError('Укажите email')
      return
    }
    if (!EMAIL_RE.test(value)) {
      setError('Введите корректный email')
      return
    }
    setLoading(true)
    setError('')
    try {
      await api.post('/auth/forgot-password', { email: value })
      setSent(true)
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-page-overlay" />
      <form className="auth-card" onSubmit={submit} noValidate>
        <h1>Восстановление пароля</h1>
        {sent ? (
          <>
            <div className="alert alert-success">
              Если такой email зарегистрирован, мы отправили письмо со ссылкой
              для сброса пароля. Проверьте почту и папку «Спам».
            </div>
            <p className="auth-switch">
              <Link to="/login">Вернуться ко входу</Link>
            </p>
          </>
        ) : (
          <>
            {error && <div className="alert alert-error">{error}</div>}
            <p className="hint" style={{ marginBottom: 14 }}>
              Укажите email, указанный при регистрации. Мы пришлём ссылку для
              создания нового пароля.
            </p>
            <label className="field">
              <span>Email</span>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                     placeholder="you@example.com" autoComplete="email" autoFocus />
            </label>
            <button className="btn btn-primary btn-block" disabled={loading}>
              {loading ? 'Отправка…' : 'Отправить ссылку'}
            </button>
            <p className="auth-switch">
              Вспомнили пароль? <Link to="/login">Войти</Link>
            </p>
          </>
        )}
      </form>
    </div>
  )
}
