import { useCallback, useEffect, useRef, useState } from 'react'
import { apiMessage } from '../api/client'
import { fetchTeams, leaveTeam, removeTeamMember } from '../api/teams'
import CreateTeamModal from '../components/teams/CreateTeamModal'
import IncomingInvitations from '../components/teams/IncomingInvitations'
import InviteModal from '../components/teams/InviteModal'
import TeamCard from '../components/teams/TeamCard'
import { useAuth } from '../context/AuthContext'
import { useInvitations } from '../context/InvitationsContext'

/**
 * Раздел «Команды»: общий рейтинг, создание команды, приглашения
 * и управление участниками.
 */
export default function Teams() {
  const { user } = useAuth()
  const { invitations } = useInvitations()
  const [teams, setTeams] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [inviteTeam, setInviteTeam] = useState(null)
  const previousInvitationCount = useRef(null)

  const loadTeams = useCallback(async () => {
    try {
      setTeams(await fetchTeams())
      setError('')
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadTeams()
  }, [loadTeams])

  // приняли приглашение (в том числе через колокольчик) — обновляем составы команд
  useEffect(() => {
    if (previousInvitationCount.current === null) {
      previousInvitationCount.current = invitations.length
      return
    }
    if (previousInvitationCount.current !== invitations.length) {
      previousInvitationCount.current = invitations.length
      loadTeams()
    }
  }, [invitations.length, loadTeams])

  const handleCreated = async () => {
    setShowCreate(false)
    setMessage('Команда создана — вы её создатель и администратор.')
    await loadTeams()
  }

  const handleLeave = async (team) => {
    await leaveTeam(team.id)
    setMessage(`Вы покинули команду «${team.name}».`)
    await loadTeams()
  }

  const handleRemove = async (team, userId) => {
    await removeTeamMember(team.id, userId)
    setMessage('Участник исключён из команды.')
    await loadTeams()
  }

  return (
    <div className="page teams-page">
      <div className="page-header teams-header">
        <div>
          <h1>Команды</h1>
          <p className="teams-subtitle">
            Объединяйтесь, приглашайте участников и поднимайте команду в рейтинге побед.
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Создать команду
        </button>
      </div>

      <IncomingInvitations onChanged={loadTeams} />

      {message && <div className="alert alert-success">{message}</div>}
      {error && <div className="alert alert-error">{error}</div>}

      <div className="teams-rating-note">
        <span>🏆</span>
        <span>Рейтинг команды — количество побед её участников в игре «Ассоциации».</span>
      </div>

      {loading ? (
        <div className="page-loader">Загрузка команд…</div>
      ) : teams.length === 0 ? (
        <div className="empty-state teams-empty">
          <p>Пока нет ни одной команды.</p>
          <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
            Создать первую команду
          </button>
        </div>
      ) : (
        <div className="teams-grid">
          {teams.map((team, index) => (
            <TeamCard
              key={team.id}
              team={team}
              place={index + 1}
              onInvite={setInviteTeam}
              onLeave={handleLeave}
              onRemove={handleRemove}
            />
          ))}
        </div>
      )}

      {showCreate && (
        <CreateTeamModal onClose={() => setShowCreate(false)} onCreated={handleCreated} />
      )}

      {inviteTeam && (
        <InviteModal team={inviteTeam} onClose={() => setInviteTeam(null)} />
      )}

      <p className="teams-hint">
        Вы вошли как <strong>{user?.fullName || user?.username}</strong>. Один пользователь может
        состоять в нескольких командах.
      </p>
    </div>
  )
}
