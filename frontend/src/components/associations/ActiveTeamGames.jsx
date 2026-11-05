import { useCallback, useEffect, useState } from 'react'
import { apiMessage } from '../../api/client'
import { fetchActiveTeamSessions } from '../../api/teamGames'
import TeamAvatar from '../teams/TeamAvatar'

/**
 * Блок «Активные игры команды».
 *
 * Показывается в самом верху списка игр, если в одной из команд игрока
 * идёт активная партия. В карточке — игра, хост (тот, кто её запустил)
 * и кнопка «Присоединиться к игре».
 */
export default function ActiveTeamGames({ onJoin }) {
  const [sessions, setSessions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [joiningId, setJoiningId] = useState(null)

  const load = useCallback(async () => {
    try {
      const data = await fetchActiveTeamSessions()
      setSessions(Array.isArray(data) ? data : [])
      setError('')
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
    // активная партия может начаться, пока игрок на другой странице
    const timer = setInterval(load, 15000)
    return () => clearInterval(timer)
  }, [load])

  const handleJoin = async (session) => {
    if (joiningId) return
    setJoiningId(session.sessionId)
    setError('')
    try {
      await onJoin?.(session)
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setJoiningId(null)
    }
  }

  if (loading || sessions.length === 0) {
    return null
  }

  return (
    <section className="assoc-active-games" aria-labelledby="active-team-games-title">
      <div className="assoc-active-games-head">
        <h2 id="active-team-games-title" className="assoc-active-games-title">
          🎮 Активные игры команды
        </h2>
        <span className="assoc-active-games-count">{sessions.length}</span>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <ul className="assoc-active-games-list">
        {sessions.map((session) => (
          <li key={session.sessionId} className="assoc-active-game">
            <TeamAvatar src={session.teamAvatarUrl} name={session.teamName} size="md" />
            <div className="assoc-active-game-info">
              <span className="assoc-active-game-title">{session.gameTitle}</span>
              <span className="assoc-active-game-meta">
                {session.teamName} · хост: {session.hostUsername || '—'}
              </span>
              <span className="assoc-active-game-meta">
                Уровень {(session.currentLevelIndex || 0) + 1} из {session.levelsCount || 1}
              </span>
            </div>
            <button
              type="button"
              className="btn btn-primary"
              disabled={joiningId === session.sessionId}
              onClick={() => handleJoin(session)}
            >
              {joiningId === session.sessionId ? 'Подключаем…' : 'Присоединиться к игре'}
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
