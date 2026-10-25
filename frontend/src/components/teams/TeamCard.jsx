import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ConfirmDialog from '../ConfirmDialog'
import { membersLabel, winsLabel } from '../../utils/plural'
import TeamAvatar from './TeamAvatar'

const placeLabel = (place) => {
  if (place === 1) return '🥇 1 место'
  if (place === 2) return '🥈 2 место'
  if (place === 3) return '🥉 3 место'
  return `${place} место`
}

/** Карточка команды: рейтинг, участники и действия по правам текущего пользователя. */
export default function TeamCard({ team, place, onInvite, onLeave, onRemove }) {
  const [confirm, setConfirm] = useState(null)
  const [leaving, setLeaving] = useState(false)
  const navigate = useNavigate()

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

  const confirmRemove = async () => {
    await onRemove(team, confirm.member.userId)
    setConfirm(null)
  }

  const leaveHint = team.memberCount > 1
    ? (team.currentUserCreator
      ? 'Вы создатель: права перейдут самому раннему участнику команды.'
      : 'Вы сможете вернуться, если получите новое приглашение.')
    : 'Вы последний участник — команда будет удалена.'

  return (
    <>
      <article
        className="team-card"
        role="link"
        tabIndex={0}
        aria-label={`Открыть карточку команды «${team.name}»`}
        onClick={openTeam}
        onKeyDown={openTeamFromKeyboard}
      >
        <div className="team-card-head">
          <TeamAvatar src={team.avatarUrl} name={team.name} size="lg" />
          <div className="team-card-title">
            <h2>{team.name}</h2>
            <div className="team-rating">🏆 {winsLabel(team.wins)}</div>
            <div className="team-card-meta">
              <span>{membersLabel(team.memberCount)}</span>
              <span className="team-place">{placeLabel(place)}</span>
            </div>
          </div>
        </div>

        <div className="team-card-actions">
          {team.currentUserCreator && (
            <button type="button" className="btn btn-primary btn-small"
                    onClick={(event) => { event.stopPropagation(); onInvite(team) }}>
              Пригласить пользователя
            </button>
          )}
          {team.currentUserMember && (
            <button type="button" className="btn btn-outline-danger btn-small"
                    onClick={(event) => { event.stopPropagation(); setConfirm({ type: 'leave' }) }}>
              Покинуть команду
            </button>
          )}
        </div>

        <ul className="team-members">
          {team.members.map((member) => {
            const isCreator = member.userId === team.creatorId
            return (
              <li className="team-member" key={member.userId}>
                <TeamAvatar src={member.avatarUrl} name={member.fullName || member.username} size="sm" />
                <div className="team-member-info">
                  <span className="team-member-name">{member.fullName || member.username}</span>
                  <span className="team-member-login">@{member.username}</span>
                </div>
                <span className={`team-role ${isCreator ? 'team-role-admin' : ''}`}>
                  {isCreator ? 'Создатель' : 'Участник'}
                </span>
                {team.currentUserCreator && !isCreator && (
                  <button type="button" className="btn btn-ghost btn-small team-remove"
                          onClick={(event) => { event.stopPropagation(); setConfirm({ type: 'remove', member }) }}>
                    Исключить
                  </button>
                )}
              </li>
            )
          })}
        </ul>
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
    </>
  )
}
