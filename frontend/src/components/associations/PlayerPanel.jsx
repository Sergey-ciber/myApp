import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiMessage } from '../../api/client'
import { fetchLeaderboard, startGame, submitResult, restartGame } from '../../api/games'
import GameBoard from './GameBoard'
import NormalLevelBoard from './NormalLevelBoard'
import Leaderboard from './Leaderboard'
import { formatMinutes, formatTime } from './constants'

/**
 * Режим игрока: последовательное прохождение уровней игры.
 *
 * - «Начать игру» запускает первый уровень.
 * - После прохождения уровня (или истечения времени уровня) загружается следующий.
 * - После последнего уровня — экран итоговой статистики.
 */
export default function PlayerPanel({ games, gameId, onSelectGame, currentUserId }) {
  const navigate = useNavigate()
  const [game, setGame] = useState(null)
  const [progress, setProgress] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [leaderboard, setLeaderboard] = useState([])
  const [leaderboardLoading, setLeaderboardLoading] = useState(false)
  const [resultError, setResultError] = useState('')

  const [gameStarted, setGameStarted] = useState(false)
  const [currentLevelIndex, setCurrentLevelIndex] = useState(0)
  const [levelStats, setLevelStats] = useState([])
  const [finished, setFinished] = useState(false)
  const [totalTime, setTotalTime] = useState(0)

  useEffect(() => {
    if (!gameId) {
      setGame(null)
      setProgress(null)
      return undefined
    }
    let cancelled = false
    setLoading(true)
    setError('')
    startGame(gameId)
      .then((data) => {
        if (cancelled) return
        setGame(data.game)
        setProgress(data.progress)
        const hasProgress = data.progress
          && (data.progress.elapsedSeconds > 0
            || data.progress.finishedSeconds != null
            || (data.progress.solved && Object.keys(data.progress.solved).length > 0))
        setGameStarted(Boolean(hasProgress))
        setCurrentLevelIndex(0)
        setLevelStats([])
        setFinished(false)
        setTotalTime(0)
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

  /** Завершение уровня: фиксируем статистику и переходим дальше или к итогам. */
  const handleLevelComplete = (level, { netTime, penaltyTime = 0 }) => {
    const net = Number(netTime) || 0
    const penalty = Number(penaltyTime) || 0
    const stat = { levelId: level.id, name: level.title, netTime: net, penaltyTime: penalty, totalTime: net + penalty }
    const nextStats = [...levelStats, stat]
    setLevelStats(nextStats)

    const levels = game?.levels || []
    const isLast = currentLevelIndex >= levels.length - 1
    if (isLast) {
      const grandTotal = nextStats.reduce((sum, s) => sum + s.totalTime, 0)
      setTotalTime(grandTotal)
      setFinished(true)
      setResultError('')
      submitResult(game.id, Math.max(1, Math.round(grandTotal)))
        .then(() => loadLeaderboard(game.id))
        .catch((err) => setResultError(`Не удалось сохранить результат: ${apiMessage(err)}`))
    } else {
      setCurrentLevelIndex((index) => index + 1)
    }
  }

  /** Начать заново: сбрасываем прогресс и статистику. */
  const playAgain = async () => {
    try {
      await restartGame(game.id)
    } catch {
      // не критично
    }
    setGameStarted(false)
    setCurrentLevelIndex(0)
    setLevelStats([])
    setFinished(false)
    setTotalTime(0)
    setResultError('')
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

  const levels = game.levels || []
  const currentLevel = levels[currentLevelIndex]

  return (
    <div className="assoc-player">
      <div className="assoc-player-top">
        <div className="assoc-player-heading">
          <h2 className="assoc-player-title">{game.title}</h2>
          <span className="assoc-player-author">
            автор: {game.creatorUsername || 'неизвестен'} · лимит: {formatMinutes(game.timeLimitMinutes)} мин.
          </span>
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

      {!gameStarted ? (
        <div className="assoc-start-screen">
          <div className="assoc-start-card">
            <h3>Готовы начать?</h3>
            <p>
              В игре {levels.length} {levels.length === 1 ? 'уровень' : 'уровней'}. Нажмите кнопку, чтобы запустить первый уровень.
            </p>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => setGameStarted(true)}
            >
              Начать игру
            </button>
          </div>
        </div>
      ) : finished ? (
        <StatisticsScreen
          stats={levelStats}
          totalTime={totalTime}
          resultError={resultError}
          leaderboard={leaderboard}
          leaderboardLoading={leaderboardLoading}
          currentUserId={currentUserId}
          onPlayAgain={playAgain}
          onHome={() => navigate('/boards')}
        />
      ) : (
        <>
          <div className="assoc-level-progress">
            Уровень {currentLevelIndex + 1} из {levels.length}
          </div>
          {currentLevel.type === 'NORMAL' ? (
            <NormalLevelBoard
              key={currentLevel.id}
              game={game}
              level={currentLevel}
              progress={progress}
              onLevelComplete={(payload) => handleLevelComplete(currentLevel, payload)}
            />
          ) : (
            <GameBoard
              key={currentLevel.id}
              game={game}
              level={currentLevel}
              progress={progress}
              onLevelComplete={(payload) => handleLevelComplete(currentLevel, payload)}
            />
          )}
        </>
      )}
    </div>
  )
}

/** Экран итоговой статистики после последнего уровня. */
function StatisticsScreen({ stats, totalTime, resultError, leaderboard, leaderboardLoading, currentUserId, onPlayAgain, onHome }) {
  return (
    <div className="assoc-statistics">
      <div className="assoc-statistics-card">
        <div className="assoc-win-emoji" aria-hidden="true">🏆</div>
        <h3 className="assoc-statistics-title">Игра завершена!</h3>

        <table className="assoc-statistics-table">
          <thead>
            <tr>
              <th>Уровень</th>
              <th>Чистое время</th>
              <th>Штраф за подсказки</th>
              <th>Итоговое время</th>
            </tr>
          </thead>
          <tbody>
            {stats.map((stat) => (
              <tr key={stat.levelId}>
                <td>{stat.name}</td>
                <td>{formatTime(stat.netTime)}</td>
                <td>{stat.penaltyTime > 0 ? `+${formatTime(stat.penaltyTime)}` : '—'}</td>
                <td>{formatTime(stat.totalTime)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan="3">Общее время игры</td>
              <td><strong>{formatTime(totalTime)}</strong></td>
            </tr>
          </tfoot>
        </table>

        {resultError && <p className="assoc-win-error">{resultError}</p>}

        <Leaderboard
          entries={leaderboard || []}
          loading={leaderboardLoading}
          currentUserId={currentUserId}
          limit={5}
          title="Лучшие результаты"
        />

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={onHome}>
            Вернуться на главную
          </button>
          <button type="button" className="btn btn-primary" onClick={onPlayAgain}>
            Играть снова
          </button>
        </div>
      </div>
    </div>
  )
}
