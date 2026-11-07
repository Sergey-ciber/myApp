import { useCallback, useEffect, useRef, useState } from 'react'
import { apiMessage } from '../api/client'
import { deleteTeam, fetchTeams, leaveTeam } from '../api/teams'
import TeamFormModal from '../components/teams/TeamFormModal'
import IncomingInvitations from '../components/teams/IncomingInvitations'
import InviteModal from '../components/teams/InviteModal'
import TeamCard from '../components/teams/TeamCard'
import { useAuth } from '../context/AuthContext'
import { useInvitations } from '../context/InvitationsContext'

/**
 * Раздел «Команды»: команды, в которых состоит пользователь, отдельно от остальных,
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

  // «Мои команды» — все, в которых авторизованный пользователь состоит:
  // и созданные им (администратор), и те, куда его пригласили.
  const isMyTeam = (team) => team.currentUserCreator || team.currentUserMember
  const adminTeams = teams.filter((team) => team.currentUserCreator)
  const memberTeams = teams.filter((team) => team.currentUserMember && !team.currentUserCreator)
  const myTeams = teams.filter(isMyTeam)
  const otherTeams = teams.filter((team) => !isMyTeam(team))
  const placeById = new Map(teams.map((team, index) => [team.id, index + 1]))

  return (
    <div className="page teams-page">
      <div className="page-header teams-header">
        <div>
          <h1>Команды</h1>
          {/*<p className="teams-subtitle">*/}
          {/*  Объединяйтесь, приглашайте участников и поднимайте команду в рейтинге побед.*/}
          {/*</p>*/}
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Создать команду
        </button>
      </div>

      <IncomingInvitations onChanged={loadTeams} />

      {message && <div className="alert alert-success">{message}</div>}
      {error && <div className="alert alert-error">{error}</div>}

      {/*<div className="teams-rating-note">*/}
      {/*  <span>🏆</span>*/}
      {/*  <span>Рейтинг команды — количество побед её участников в игре «Ассоциации».</span>*/}
      {/*</div>*/}

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
                  <span className="teams-section-badge">Участник</span>
                </h2>
                <p className="teams-section-description">
                  Все команды, в которых вы состоите: созданные вами и те, куда вас пригласили.
                </p>
              </div>
              <span className="teams-section-count">{myTeams.length}</span>
            </div>
            {myTeams.length === 0 ? (
              <div className="teams-section-empty">Вы пока не состоите ни в одной команде.</div>
            ) : (
              <>
                {/* Команды, созданные текущим пользователем (он же администратор) */}
                {adminTeams.length > 0 && (
                  <div className="teams-subsection">
                    <div className="teams-subsection-head">
                      <span className="teams-subsection-title">Созданы мной</span>
                      <span className="teams-subsection-count">{adminTeams.length}</span>
                    </div>
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
                  </div>
                )}
                {/* Команды, куда пользователя пригласили */}
                {memberTeams.length > 0 && (
                  <div className="teams-subsection">
                    <div className="teams-subsection-head">
                      <span className="teams-subsection-title">Пригласили меня</span>
                      <span className="teams-subsection-count">{memberTeams.length}</span>
                    </div>
                    <div className="teams-grid">
                      {memberTeams.map((team) => (
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
                  </div>
                )}
              </>
            )}
          </section>

          <section className="teams-section" aria-labelledby="other-teams-title">
            <div className="teams-section-head">
              <div>
                <h2 id="other-teams-title" className="teams-section-title">Остальные команды</h2>
                <p className="teams-section-description">
                  Все остальные команды платформы, в которых вы не состоите.
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
