import { useRef, useState } from 'react'
import api, { apiMessage } from '../api/client'
import { useAuth } from '../context/AuthContext'

export default function Profile() {
  const { user, setUser } = useAuth()
  const [fullName, setFullName] = useState(user?.fullName || '')
  const [email, setEmail] = useState(user?.email || '')
  const [message, setMessage] = useState(null)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileRef = useRef(null)

  const save = async (e) => {
    e.preventDefault()
    setSaving(true)
    setMessage(null)
    setError('')
    try {
      const res = await api.put('/users/me', { fullName, email })
      setUser(res.data)
      localStorage.setItem('user', JSON.stringify(res.data))
      setMessage('Профиль сохранён')
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const pickAvatar = () => fileRef.current?.click()

  const uploadAvatar = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    setUploading(true)
    setMessage(null)
    setError('')
    try {
      const res = await api.post('/users/me/avatar', form)
      setUser(res.data)
      localStorage.setItem('user', JSON.stringify(res.data))
      setMessage('Аватар обновлён')
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  return (
    <div className="page page-narrow">
      <div className="page-header">
        <h1>Настройки профиля</h1>
      </div>

      <div className="profile-card">
        <div className="profile-avatar-section">
          {user?.avatarUrl ? (
            <img src={user.avatarUrl} alt="Аватар" className="avatar avatar-lg" />
          ) : (
            <span className="avatar avatar-lg avatar-fallback">
              {(user?.fullName || user?.username || '?').slice(0, 2).toUpperCase()}
            </span>
          )}
          <div>
            <button className="btn btn-secondary" onClick={pickAvatar} disabled={uploading}>
              {uploading ? 'Загрузка…' : 'Установить аватар'}
            </button>
            <p className="hint">PNG, JPEG, WEBP или GIF, до 5 МБ</p>
            <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp,image/gif"
                   style={{ display: 'none' }} onChange={uploadAvatar} />
          </div>
        </div>

        {message && <div className="alert alert-success">{message}</div>}
        {error && <div className="alert alert-error">{error}</div>}

        <form onSubmit={save} className="form">
          <label className="field">
            <span>Логин</span>
            <input value={user?.username || ''} disabled />
          </label>
          <label className="field">
            <span>Полное имя</span>
            <input value={fullName} onChange={(e) => setFullName(e.target.value)}
                   placeholder="Как вас зовут" />
          </label>
          <label className="field">
            <span>Email</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                   placeholder="you@example.com" />
          </label>
          <div className="form-actions">
            <button className="btn btn-primary" disabled={saving}>
              {saving ? 'Сохранение…' : 'Сохранить изменения'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
