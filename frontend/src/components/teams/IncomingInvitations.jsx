import { useState } from 'react'
import { apiMessage } from '../../api/client'
import { useInvitations } from '../../context/InvitationsContext'
import TeamAvatar from './TeamAvatar'

/** Заметный блок входящих приглашений на странице «Команды». */
export default function IncomingInvitations({ onChanged }) {
  const { invitations, accept, decline } = useInvitations()
  const [busyId, setBusyId] = useState(null)
  const [error, setError] = useState('')

  if (invitations.length === 0) return null

  const respond = async (invitation, action) => {
    setBusyId(invitation.id)
    setError('')
    try {
      await action(invitation.id)
      onChanged?.()
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="invitations-panel">
      <div className="invitations-panel-head">
        <h2>🔔 Входящие приглашения</h2>
        <span className="invitations-count">{invitations.length}</span>
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      <ul className="invitations-list">
        {invitations.map((invitation) => (
          <li className="invitation-row" key={invitation.id}>
            <TeamAvatar src={invitation.teamAvatarUrl} name={invitation.teamName} size="sm" />
            <div className="invitation-info">
              <span className="invitation-team">{invitation.teamName}</span>
              <span className="invitation-meta">приглашение от @{invitation.inviterUsername}</span>
            </div>
            <div className="invitation-actions">
              <button type="button" className="btn btn-primary btn-small"
                      disabled={busyId === invitation.id}
                      onClick={() => respond(invitation, accept)}>
                Принять
              </button>
              <button type="button" className="btn btn-ghost btn-small"
                      disabled={busyId === invitation.id}
                      onClick={() => respond(invitation, decline)}>
                Отклонить
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
