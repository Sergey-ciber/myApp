import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiMessage } from '../api/client'
import { useInvitations } from '../context/InvitationsContext'
import TeamAvatar from './teams/TeamAvatar'

/** Колокольчик в шапке: счётчик и быстрые действия по приглашениям. */
export default function InvitationsBell() {
  const { invitations, count, accept, decline } = useInvitations()
  const [open, setOpen] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [error, setError] = useState('')
  const rootRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (!open) return undefined
    const onClick = (event) => {
      if (rootRef.current && !rootRef.current.contains(event.target)) setOpen(false)
    }
    const onKeyDown = (event) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const respond = async (invitation, action, goToTeams) => {
    setBusyId(invitation.id)
    setError('')
    try {
      await action(invitation.id)
      if (goToTeams) {
        setOpen(false)
        navigate('/teams')
      }
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="navbar-bell-wrap" ref={rootRef}>
      <button
        type="button"
        className={`navbar-bell ${count > 0 ? 'has-count' : ''}`}
        aria-label={`Приглашения в команды: ${count}`}
        title="Приглашения в команды"
        onClick={() => {
          setOpen((value) => !value)
          setError('')
        }}
      >
        <span aria-hidden="true">🔔</span>
        {count > 0 && <span className="bell-badge">{count > 99 ? '99+' : count}</span>}
      </button>

      {open && (
        <div className="bell-dropdown">
          <div className="bell-dropdown-head">
            <strong>Приглашения в команды</strong>
            {count > 0 && <span className="bell-count">{count}</span>}
          </div>

          {error && <div className="bell-error">{error}</div>}

          {invitations.length === 0 ? (
            <p className="bell-empty">Новых приглашений нет</p>
          ) : (
            <ul className="bell-list">
              {invitations.map((invitation) => (
                <li className="bell-item" key={invitation.id}>
                  <TeamAvatar src={invitation.teamAvatarUrl} name={invitation.teamName} size="sm" />
                  <div className="bell-item-info">
                    <span className="bell-team">{invitation.teamName}</span>
                    <span className="bell-meta">от @{invitation.inviterUsername}</span>
                    <div className="bell-actions">
                      <button type="button" className="btn btn-primary btn-small"
                              disabled={busyId === invitation.id}
                              onClick={() => respond(invitation, accept, true)}>
                        Принять
                      </button>
                      <button type="button" className="btn btn-ghost btn-small"
                              disabled={busyId === invitation.id}
                              onClick={() => respond(invitation, decline, false)}>
                        Отклонить
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}

          <button type="button" className="bell-footer"
                  onClick={() => { setOpen(false); navigate('/teams') }}>
            Перейти в «Команды»
          </button>
        </div>
      )}
    </div>
  )
}
