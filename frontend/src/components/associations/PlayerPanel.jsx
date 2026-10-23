import { useCallback, useEffect, useState } from 'react'
import { apiMessage } from '../../api/client'
import { fetchLeaderboard, startGame, submitResult } from '../../api/games'
import GameBoard from './GameBoard'
import Leaderboard from './Leaderboard'

/**
 * Режим игрока: выбор любой игры платформы, игровое поле, таймер и рейтинг.
 *
 * Прогресс приходит с сервера и принадлежит только текущему пользователю:
 * POST /api/games/{id}/start возвращает структуру поля без ответов и личный прогресс.
 */
export default function PlayerPanel({ games, gameId, onSelectGame, currentUserId }) {
  const [game, setGame] = useState(null)
  const [progress, setProgress] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [leaderboard, setLeaderboard] = useState([])
  const [leaderboardLoading, setLeaderboardLoading] = useState(false)
  const [resultError, setResultError] = useState('')

  useEffect(() => {
    if (!gameId) {
      setGame(null)
      setProgress(null)
      return undefined
    }
    let cancelled = false
    setLoading(true)
    setError('')
    // старт/продолжение попытки: сервер создаёт отдельный прогресс для этого пользователя
    startGame(gameId)
      .then((data) => {
        if (cancelled) return
        setGame(data.game)
        setProgress(data.progress)
      })
      .catch((err) => { if (!cancelled) setError(apiMessage(err)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [gameId, currentUserId])

  const loadLeaderboard = useCallback(async (id) => {
    if (!id) return
    setLeaderboardLoading(true)
    try {
      setLeaderboard(await fetchLeaderboard(id))
    } catch (err) {
      setResultError(apiMessage(err))
    } finally {
      setLeaderboardLoading(false)
    }
  }, [])

  useEffect(() => {
    setLeaderboard([])
    setResultError('')
    loadLeaderboard(game?.id)
  }, [game, loadLeaderboard])

  /** Победа: сохраняем время на бэкенде и обновляем рейтинг. */
  const handleWin = async (timeSeconds) => {
    setResultError('')
    try {
      await submitResult(game.id, timeSeconds)
    } catch (err) {
      setResultError(`Не удалось сохранить результат: ${apiMessage(err)}`)
    }
    await loadLeaderboard(game.id)
  }

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
        <div className="assoc-player-heading">
          <h2 className="assoc-player-title">Игра в ассоциации — {game.title}</h2>
          <span className="assoc-player-author">автор: {game.creatorUsername || 'неизвестен'}</span>
        </div>

        <label className="assoc-game-select">
          <span>Выбрать игру</span>
          <select value={game.id} onChange={(event) => onSelectGame(Number(event.target.value))}>
            {games.map((item) => (
              <option key={item.id} value={item.id}>
                {item.title} — {item.creatorUsername || 'без автора'}
              </option>
            ))}
          </select>
        </label>
      </div>

      {/* key по пользователю и игре: при смене игрока или игры поле начинается с чистого листа */}
      <GameBoard
        key={`${currentUserId ?? 'anon'}-${game.id}`}
        game={game}
        progress={progress}
        onWin={handleWin}
        leaderboard={leaderboard}
        leaderboardLoading={leaderboardLoading}
        currentUserId={currentUserId}
        resultError={resultError}
      />

      <Leaderboard
        entries={leaderboard}
        loading={leaderboardLoading}
        currentUserId={currentUserId}
        title="Рейтинг игры"
      />
    </div>
  )
}
