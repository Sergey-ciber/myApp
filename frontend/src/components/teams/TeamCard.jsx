import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import ConfirmDialog from '../ConfirmDialog'
import { membersLabel, winsLabel } from '../../utils/plural'
import TeamAvatar from './TeamAvatar'

const placeLabel = (place) => {
  if (place === 1) return '🥇 1 место'
  if (place === 2) return '🥈 2 место'
  if (place === 3) return '🥉 3 место'
  return `${place} место`
}

/** Горизонтальная строка команды: статистика и действия по правам пользователя. */
export default function TeamCard({ team, place, onEdit, onInvite, onLeave, onDelete }) {
  const [confirm, setConfirm] = useState(null)
  const [leaving, setLeaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const navigate = useNavigate()

  const isAdmin = team.currentUserCreator
  const isMember = team.currentUserMember && !isAdmin
  const openTeam = () => navigate(`/teams/${team.id}`)

  const openTeamFromKeyboard = (event) => {
    if (event.target !== event.currentTarget) return
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      openTeam()
    }
  }

  const confirmLeave = async () => {
    setLeaving(true)
    try {
      await onLeave(team)
      setConfirm(null)
    } finally {
      setLeaving(false)
    }
  }

  const confirmDelete = async () => {
    setDeleting(true)
    try {
      await onDelete(team)
      setConfirm(null)
    } finally {
      setDeleting(false)
    }
  }

  const leaveHint = team.memberCount > 1
    ? 'Вы сможете вернуться, если получите новое приглашение.'
    : 'Вы последний участник — команда будет удалена.'

  return (
    <>
      <article
        className={`team-card${isAdmin ? ' team-card-admin' : ''}`}
        role="link"
        tabIndex={0}
        aria-label={`Открыть карточку команды «${team.name}»`}
        onClick={openTeam}
        onKeyDown={openTeamFromKeyboard}
      >
        <div className="team-card-head">
          <TeamAvatar src={team.avatarUrl} name={team.name} size="lg" />
          <div className="team-card-title">
            <div className="team-card-name-row">
              <h2>{team.name}</h2>
              {isAdmin && <span className="team-admin-badge">Админ</span>}
            </div>
            <div className="team-card-meta">
              <span className="team-rating">🏆 {winsLabel(team.wins)}</span>
              <span>{membersLabel(team.memberCount)}</span>
              <span className="team-place">{placeLabel(place)}</span>
            </div>
          </div>
        </div>

        <div className="team-card-actions" onClick={(event) => event.stopPropagation()}>
          {isAdmin && (
            <>
              <button type="button" className="btn btn-secondary btn-small"
                      onClick={() => onEdit(team)}>
                Редактировать
              </button>
              <button type="button" className="btn btn-primary btn-small"
                      onClick={() => onInvite(team)}>
                Пригласить пользователя
              </button>
              <button type="button" className="btn btn-outline-danger btn-small"
                      onClick={() => setConfirm({ type: 'delete' })}>
                Удалить
              </button>
            </>
          )}
          {isMember && (
            <button type="button" className="btn btn-outline-danger btn-small"
                    onClick={() => setConfirm({ type: 'leave' })}>
              Покинуть команду
            </button>
          )}
          {!isAdmin && (
            <Link to={`/teams/${team.id}`} className="btn btn-secondary btn-small">
              Открыть
            </Link>
          )}
        </div>
      </article>

      {confirm?.type === 'leave' && (
        <ConfirmDialog
          title="Покинуть команду"
          message={<>Покинуть команду «<strong>{team.name}</strong>»?</>}
          hint={leaveHint}
          confirmLabel="Покинуть"
          busyLabel="Выход…"
          errorMessage="Не удалось покинуть команду"
          onConfirm={confirmLeave}
          onClose={() => { if (!leaving) setConfirm(null) }}
        />
      )}

      {confirm?.type === 'delete' && (
        <ConfirmDialog
          title="Удалить команду"
          message={<>Удалить команду «<strong>{team.name}</strong>»?</>}
          hint="Команда, её состав и приглашения будут удалены без возможности восстановления."
          confirmLabel="Удалить"
          busyLabel="Удаление…"
          errorMessage="Не удалось удалить команду"
          onConfirm={confirmDelete}
          onClose={() => { if (!deleting) setConfirm(null) }}
        />
      )}
    </>
  )
}
