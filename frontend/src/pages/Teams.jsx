import { useCallback, useEffect, useRef, useState } from 'react'
import { apiMessage } from '../api/client'
import { deleteTeam, fetchTeams, leaveTeam } from '../api/teams'
import TeamFormModal from '../components/teams/TeamFormModal'
import IncomingInvitations from '../components/teams/IncomingInvitations'
import InviteModal from '../components/teams/InviteModal'
import TeamCard from '../components/teams/TeamCard'
import { useAuth } from '../context/AuthContext'
import { useInvitations } from '../context/InvitationsContext'

const teamCreatorId = (team) => team.creator_id ?? team.creatorId

/**
 * Раздел «Команды»: команды пользователя-администратора отдельно от остальных,
 * создание, приглашения и управление участниками.
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
  const [editingTeam, setEditingTeam] = useState(null)
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

  const handleUpdated = async () => {
    setEditingTeam(null)
    setMessage('Изменения команды сохранены.')
    await loadTeams()
  }

  const handleLeave = async (team) => {
    await leaveTeam(team.id)
    setMessage(`Вы покинули команду «${team.name}».`)
    await loadTeams()
  }

  const handleDelete = async (team) => {
    await deleteTeam(team.id)
    setMessage(`Команда «${team.name}» удалена.`)
    await loadTeams()
  }

  const adminTeams = teams.filter((team) => teamCreatorId(team) === user?.id)
  const otherTeams = teams.filter((team) => teamCreatorId(team) !== user?.id)
  const placeById = new Map(teams.map((team, index) => [team.id, index + 1]))

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
        <>
          <section className="teams-section teams-section-admin" aria-labelledby="admin-teams-title">
            <div className="teams-section-head">
              <div>
                <h2 id="admin-teams-title" className="teams-section-title">
                  Мои команды
                  <span className="teams-section-badge">Администратор</span>
                </h2>
                <p className="teams-section-description">
                  Команды, которые вы создали и которыми управляете.
                </p>
              </div>
              <span className="teams-section-count">{adminTeams.length}</span>
            </div>
            {adminTeams.length === 0 ? (
              <div className="teams-section-empty">Вы пока не создали ни одной команды.</div>
            ) : (
              <div className="teams-grid">
                {adminTeams.map((team) => (
                  <TeamCard
                    key={team.id}
                    team={team}
                    place={placeById.get(team.id)}
                    onEdit={setEditingTeam}
                    onInvite={setInviteTeam}
                    onLeave={handleLeave}
                    onDelete={handleDelete}
                  />
                ))}
              </div>
            )}
          </section>

          <section className="teams-section" aria-labelledby="other-teams-title">
            <div className="teams-section-head">
              <div>
                <h2 id="other-teams-title" className="teams-section-title">Остальные команды</h2>
                <p className="teams-section-description">
                  Команды, в которых вы состоите, и все остальные команды платформы.
                </p>
              </div>
              <span className="teams-section-count">{otherTeams.length}</span>
            </div>
            {otherTeams.length === 0 ? (
              <div className="teams-section-empty">Других команд пока нет.</div>
            ) : (
              <div className="teams-grid">
                {otherTeams.map((team) => (
                  <TeamCard
                    key={team.id}
                    team={team}
                    place={placeById.get(team.id)}
                    onEdit={setEditingTeam}
                    onInvite={setInviteTeam}
                    onLeave={handleLeave}
                    onDelete={handleDelete}
                  />
                ))}
              </div>
            )}
          </section>
        </>
      )}

      {showCreate && (
        <TeamFormModal onClose={() => setShowCreate(false)} onSaved={handleCreated} />
      )}

      {editingTeam && (
        <TeamFormModal team={editingTeam} onClose={() => setEditingTeam(null)}
                       onSaved={handleUpdated} />
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
