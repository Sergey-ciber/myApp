import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiMessage } from '../api/client'
import { fetchTeam, leaveTeam, removeTeamMember } from '../api/teams'
import ConfirmDialog from '../components/ConfirmDialog'
import InviteModal from '../components/teams/InviteModal'
import TeamAvatar from '../components/teams/TeamAvatar'
import TeamFormModal from '../components/teams/TeamFormModal'
import { gamesWord, membersLabel, membersWord, winsWord } from '../utils/plural'

const formatDate = (value) => {
  if (!value) return ''
  return new Date(value).toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

const roleLabel = (member, creatorId) => {
  if (member.userId === creatorId) return 'Создатель'
  if (member.role === 'ADMIN') return 'Администратор'
  return 'Участник'
}

/** Подробная карточка команды: статистика, участники и действия по правам. */
export default function TeamDetails() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [team, setTeam] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [notFound, setNotFound] = useState(false)
  const [message, setMessage] = useState('')
  const [editing, setEditing] = useState(false)
  const [inviting, setInviting] = useState(false)
  const [confirm, setConfirm] = useState(null)

  const loadTeam = useCallback(async () => {
    try {
      const data = await fetchTeam(id)
      setTeam(data)
      setLoadError('')
      setNotFound(false)
    } catch (err) {
      setTeam(null)
      setLoadError(apiMessage(err))
      setNotFound(err.response?.status === 404)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    loadTeam()
  }, [loadTeam])

  const handleSaved = (updated) => {
    setEditing(false)
    setTeam(updated)
    setMessage('Изменения сохранены.')
  }

  const confirmRemove = async () => {
    await removeTeamMember(team.id, confirm.member.userId)
    setConfirm(null)
    setMessage('Участник исключён из команды.')
    await loadTeam()
  }

  const confirmLeave = async () => {
    await leaveTeam(team.id)
    navigate('/teams')
  }

  const backLink = <Link to="/teams" className="back-link">← Назад к командам</Link>

  if (loading) {
    return (
      <div className="page team-details-page">
        {backLink}
        <div className="page-loader">Загрузка команды…</div>
      </div>
    )
  }

  if (!team) {
    return (
      <div className="page team-details-page">
        {backLink}
        <div className="empty-state team-details-error">
          <h1>{notFound ? 'Команда не найдена' : 'Не удалось загрузить команду'}</h1>
          <p>{loadError || 'Попробуйте вернуться к списку команд.'}</p>
          <Link to="/teams" className="btn btn-primary">Вернуться к командам</Link>
        </div>
      </div>
    )
  }

  const canManage = team.currentUserCreator
  const canLeave = team.currentUserMember && !team.currentUserCreator
  const leaveHint = team.membersCount > 1
    ? 'Вы сможете вернуться, если получите новое приглашение.'
    : 'Вы последний участник — команда будет удалена.'

  return (
    <div className="page team-details-page">
      {backLink}

      {message && <div className="alert alert-success">{message}</div>}

      <section className="team-hero">
        <TeamAvatar src={team.avatarUrl} name={team.name} size="xl" />
        <div className="team-hero-main">
          <p className="team-hero-kicker">Карточка команды</p>
          <h1>{team.name}</h1>
          <p className="team-hero-meta">
            Создатель: <strong>{team.creatorUsername}</strong>
            <span className="team-meta-separator">·</span>
            {membersLabel(team.membersCount)}
          </p>
        </div>
        <div className="team-hero-actions">
          {canManage && (
            <button type="button" className="btn btn-primary" onClick={() => setEditing(true)}>
              Редактировать команду
            </button>
          )}
          {canManage && (
            <button type="button" className="btn btn-secondary" onClick={() => setInviting(true)}>
              Пригласить пользователя
            </button>
          )}
          {canLeave && (
            <button type="button" className="btn btn-outline-danger"
                    onClick={() => setConfirm({ type: 'leave' })}>
              Покинуть команду
            </button>
          )}
        </div>
      </section>

      <section className="team-stats" aria-label="Статистика команды">
        <div className="team-stat team-stat-wins">
          <span className="team-stat-icon" aria-hidden="true">🏆</span>
          <span className="team-stat-label">Рейтинг</span>
          <strong className="team-stat-value">{team.wins}</strong>
          <span className="team-stat-caption">{winsWord(team.wins)}</span>
        </div>
        <div className="team-stat">
          <span className="team-stat-icon" aria-hidden="true">👥</span>
          <span className="team-stat-label">Участники</span>
          <strong className="team-stat-value">{team.membersCount}</strong>
          <span className="team-stat-caption">{membersWord(team.membersCount)}</span>
        </div>
        <div className="team-stat">
          <span className="team-stat-icon" aria-hidden="true">🎮</span>
          <span className="team-stat-label">Сыграно игр</span>
          <strong className="team-stat-value">{team.gamesPlayed}</strong>
          <span className="team-stat-caption">{gamesWord(team.gamesPlayed)}</span>
        </div>
      </section>
      <p className="team-stats-note">
        В счётчике игр учитываются уникальные сохранённые результаты всех участников команды.
      </p>

      <section className="team-members-card">
        <div className="team-members-head">
          <div>
            <h2>Участники команды</h2>
            <p>{membersLabel(team.membersCount)}</p>
          </div>
          {canManage && (
            <button type="button" className="btn btn-secondary btn-small"
                    onClick={() => setInviting(true)}>
              + Пригласить
            </button>
          )}
        </div>

        <ul className="team-detail-members">
          {team.members.map((member) => {
            const isCreator = member.userId === team.creatorId
            return (
              <li className="team-detail-member" key={member.userId}>
                <TeamAvatar src={member.avatarUrl} name={member.fullName || member.username}
                            size="md" />
                <div className="team-detail-person">
                  <span className="team-detail-name">{member.fullName || member.username}</span>
                  <span className="team-detail-login">@{member.username}</span>
                </div>
                <span className={`team-role ${member.role === 'ADMIN' ? 'team-role-admin' : ''}`}>
                  {roleLabel(member, team.creatorId)}
                </span>
                {member.joinedAt && (
                  <span className="team-detail-joined">с {formatDate(member.joinedAt)}</span>
                )}
                {canManage && !isCreator && (
                  <button type="button" className="btn btn-ghost btn-small team-remove"
                          onClick={() => setConfirm({ type: 'remove', member })}>
                    Исключить
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      </section>

      {editing && (
        <TeamFormModal team={team} onClose={() => setEditing(false)} onSaved={handleSaved} />
      )}

      {inviting && (
        <InviteModal team={team} onClose={() => setInviting(false)} onInvited={loadTeam} />
      )}

      {confirm?.type === 'remove' && (
        <ConfirmDialog
          title="Исключение участника"
          message={<>Исключить <strong>{confirm.member.fullName || confirm.member.username}</strong> из команды «{team.name}»?</>}
          hint="Пользователь сможет вернуться по новому приглашению."
          confirmLabel="Исключить"
          busyLabel="Исключение…"
          errorMessage="Не удалось исключить участника"
          onConfirm={confirmRemove}
          onClose={() => setConfirm(null)}
        />
      )}

      {confirm?.type === 'leave' && (
        <ConfirmDialog
          title="Покинуть команду"
          message={<>Покинуть команду «<strong>{team.name}</strong>»?</>}
          hint={leaveHint}
          confirmLabel="Покинуть"
          busyLabel="Выход…"
          errorMessage="Не удалось покинуть команду"
          onConfirm={confirmLeave}
          onClose={() => setConfirm(null)}
        />
      )}
    </div>
  )
}
