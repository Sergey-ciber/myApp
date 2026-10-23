import { useEffect, useState } from 'react'
import { apiMessage } from '../../api/client'
import { fetchGame } from '../../api/games'
import GameBoard from './GameBoard'

/**
 * Режим игрока: выбор игры по названию и игровое поле.
 */
export default function PlayerPanel({ games, gameId, onSelectGame }) {
  const [game, setGame] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!gameId) {
      setGame(null)
      return
    }
    let cancelled = false
    setLoading(true)
    setError('')
    fetchGame(gameId)
      .then((data) => { if (!cancelled) setGame(data) })
      .catch((err) => { if (!cancelled) setError(apiMessage(err)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [gameId])

  if (games.length === 0) {
    return (
      <div className="empty-state">
        Игра ещё не создана. Обратитесь к администратору.
      </div>
    )
  }

  if (error) return <div className="alert alert-error">{error}</div>
  if (loading && !game) return <div className="page-loader">Загрузка игры…</div>
  if (!game) return null

  return (
    <div className="assoc-player">
      <div className="assoc-player-top">
        <h2 className="assoc-player-title">Игра в ассоциации — {game.title}</h2>

        {games.length > 1 && (
          <label className="assoc-game-select">
            <span>Выбрать игру</span>
            <select value={game.id} onChange={(event) => onSelectGame(Number(event.target.value))}>
              {games.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title}{item.active ? ' (активная)' : ''}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {/* key по id игры: при переключении прогресс и состояние начинаются заново */}
      <GameBoard key={game.id} game={game} />
    </div>
  )
}
