import { useCallback, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { apiMessage } from '../api/client'
import { deleteUser, fetchAllUsers } from '../api/users'
import ConfirmDialog from '../components/ConfirmDialog'
import { useAuth } from '../context/AuthContext'

function initials(user) {
  const name = user?.fullName || user?.username || '?'
  return name.slice(0, 2).toUpperCase()
}

/**
 * Раздел «Пользователи»: доступен только супер-администратору.
 * Позволяет удалить учётную запись вместе со всеми связанными данными.
 */
export default function Users() {
  const { user, isSuperAdmin } = useAuth()
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const [deleting, setDeleting] = useState(null)

  const loadUsers = useCallback(async () => {
    try {
      setUsers(await fetchAllUsers())
      setError('')
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (isSuperAdmin) loadUsers()
  }, [isSuperAdmin, loadUsers])

  // Уведомление исчезает само, чтобы не заслонять таблицу.
  useEffect(() => {
    if (!toast) return undefined
    const timer = setTimeout(() => setToast(''), 4000)
    return () => clearTimeout(timer)
  }, [toast])

  if (!isSuperAdmin) {
    return <Navigate to="/associations" replace />
  }

  const handleDelete = async () => {
    const target = deleting
    if (!target) return
    await deleteUser(target.id)
    setUsers((prev) => prev.filter((candidate) => candidate.id !== target.id))
    setDeleting(null)
    setToast('Пользователь успешно удален')
  }

  const isCurrentUser = (candidate) =>
    candidate.id != null && user?.id != null && String(candidate.id) === String(user.id)

  return (
    <div className="page users-page">
      <div className="page-header">
        <div>
          <h1>Пользователи</h1>
          <p className="users-subtitle">
            Управление учётными записями платформы. Удаление необратимо.
          </p>
        </div>
        <span className="users-count">{users.length}</span>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="page-loader">Загрузка пользователей…</div>
      ) : users.length === 0 ? (
        <div className="empty-state">
          <p>Пользователи не найдены.</p>
        </div>
      ) : (
        <div className="users-table-wrap">
          <table className="users-table">
            <thead>
              <tr>
                <th>Пользователь</th>
                <th>Полное имя</th>
                <th>Email</th>
                <th>Роль</th>
                <th className="users-col-actions">Действия</th>
              </tr>
            </thead>
            <tbody>
              {users.map((candidate) => {
                const self = isCurrentUser(candidate)
                return (
                  <tr key={candidate.id} className={self ? 'is-me' : ''}>
                    <td>
                      <div className="users-cell-user">
                        {candidate.avatarUrl
                          ? <img src={candidate.avatarUrl} alt="avatar" className="avatar avatar-sm" />
                          : (
                            <span className="avatar avatar-sm avatar-fallback">
                              {initials(candidate)}
                            </span>
                          )}
                        <span className="users-name">{candidate.username}</span>
                        {self && <span className="users-me-badge">это вы</span>}
                      </div>
                    </td>
                    <td>{candidate.fullName || '—'}</td>
                    <td>{candidate.email || '—'}</td>
                    <td>
                      <span className={`users-role${candidate.role === 'SUPERADMIN' ? ' is-superadmin' : ''}`}>
                        {candidate.role === 'SUPERADMIN' ? 'Super Admin' : 'Пользователь'}
                      </span>
                    </td>
                    <td className="users-actions">
                      <button
                        type="button"
                        className="btn btn-danger btn-small users-delete"
                        onClick={() => setDeleting(candidate)}
                        disabled={self}
                        title={self
                          ? 'Нельзя удалить собственную учётную запись'
                          : 'Удалить пользователя'}
                      >
                        🗑 Удалить
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {deleting && (
        <ConfirmDialog
          title="Удаление пользователя"
          message={`Вы уверены, что хотите удалить пользователя ${deleting.username}? Это действие необратимо и удалит все его данные`}
          hint="Его игры, команды и доски перейдут супер-администратору. Результаты, прогресс и приглашения будут удалены."
          confirmLabel="Удалить пользователя"
          busyLabel="Удаление…"
          errorMessage="Не удалось удалить пользователя"
          onConfirm={handleDelete}
          onClose={() => setDeleting(null)}
        />
      )}

      {toast && (
        <div className="assoc-toast" role="status">
          <span>{toast}</span>
          <button
            type="button"
            className="assoc-toast-close"
            onClick={() => setToast('')}
            aria-label="Закрыть"
          >
            ✕
          </button>
        </div>
      )}
    </div>
  )
}
