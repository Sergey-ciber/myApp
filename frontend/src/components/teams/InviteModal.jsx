import { useEffect, useState } from 'react'
import { apiMessage } from '../../api/client'
import { inviteUser, searchUsers } from '../../api/teams'
import Modal from '../Modal'
import TeamAvatar from './TeamAvatar'

/** Поиск зарегистрированных пользователей и отправка приглашения. */
export default function InviteModal({ team, onClose, onInvited }) {
  const [query, setQuery] = useState('')
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [invited, setInvited] = useState(() => new Set())
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    const timer = setTimeout(() => {
      searchUsers(query)
        .then((data) => {
          if (!cancelled) setUsers(data)
        })
        .catch((err) => {
          if (!cancelled) setError(apiMessage(err))
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
    }, 250)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [query])

  const memberIds = new Set(team.members.map((member) => member.userId))

  const sendInvite = async (user) => {
    setBusyId(user.id)
    setError('')
    setMessage('')
    try {
      await inviteUser(team.id, user.id)
      setInvited((current) => new Set(current).add(user.id))
      setMessage(`Приглашение отправлено: ${user.fullName || user.username}`)
      onInvited?.()
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <Modal title={`Пригласить в «${team.name}»`} onClose={onClose}>
      <div className="invite-search">
        <label className="field">
          <span>Поиск пользователей</span>
          <input value={query} onChange={(e) => setQuery(e.target.value)}
                 placeholder="Логин или имя" autoFocus />
        </label>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert alert-success">{message}</div>}

        <div className="invite-list">
          {loading ? (
            <p className="invite-empty">Поиск…</p>
          ) : users.length === 0 ? (
            <p className="invite-empty">Пользователи не найдены</p>
          ) : (
            users.map((user) => {
              const isMember = memberIds.has(user.id)
              const isInvited = invited.has(user.id)
              return (
                <div className="invite-row" key={user.id}>
                  <TeamAvatar src={user.avatarUrl} name={user.fullName || user.username} size="sm" />
                  <div className="invite-person">
                    <span className="invite-name">{user.fullName || user.username}</span>
                    <span className="invite-login">@{user.username}</span>
                  </div>
                  {isMember ? (
                    <span className="invite-state">Уже в команде</span>
                  ) : isInvited ? (
                    <span className="invite-state invite-state-sent">Отправлено</span>
                  ) : (
                    <button type="button" className="btn btn-primary btn-small"
                            disabled={busyId === user.id}
                            onClick={() => sendInvite(user)}>
                      {busyId === user.id ? 'Отправка…' : 'Пригласить'}
                    </button>
                  )}
                </div>
              )
            })
          )}
        </div>
      </div>
    </Modal>
  )
}
