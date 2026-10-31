import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiMessage } from '../../api/client'
import { fetchGame, fetchLeaderboard, startGame, submitResult, restartGame } from '../../api/games'
import GameBoard from './GameBoard'
import NormalLevelBoard from './NormalLevelBoard'
import Leaderboard from './Leaderboard'
import { formatMinutes, formatTime } from './constants'

/** Определить тип медиафайла по URL (расширение сохраняется бэкендом). */
function mediaKind(url) {
  const lower = (url || '').toLowerCase()
  if (/\.(mp4|webm|m4v|mov)(\?|$)/.test(lower)) return 'video'
  if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower)) return 'audio'
  if (/\.(png|jpg|jpeg|gif|webp)(\?|$)/.test(lower)) return 'image'
  return 'other'
}

/** Дата игры из ISO-строки (LocalDate) в человекочитаемый формат. */
function formatGameDate(dateStr) {
  if (!dateStr) return ''
  const parts = String(dateStr).split('-')
  if (parts.length === 3 && parts[0].length === 4) return `${parts[2]}.${parts[1]}.${parts[0]}`
  return String(dateStr)
}

/** Сложность в виде звёздочек (1..5). */
function difficultyStars(value) {
  const n = Math.max(0, Math.min(5, Number(value) || 0))
  return n > 0 ? '★'.repeat(n) : ''
}

/**
 * Режим игрока: последовательное прохождение уровней игры.
 *
 * - «Начать игру» запускает первый уровень.
 * - После прохождения уровня (или истечения времени уровня) загружается следующий.
 * - После последнего уровня — экран итоговой статистики.
 */
export default function PlayerPanel({ games, gameId, onSelectGame, onExit, currentUserId }) {
  const navigate = useNavigate()
  const [game, setGame] = useState(null)
  const [progress, setProgress] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [leaderboard, setLeaderboard] = useState([])
  const [leaderboardLoading, setLeaderboardLoading] = useState(false)
  const [resultError, setResultError] = useState('')

  const [gameStarted, setGameStarted] = useState(false)
  const [starting, setStarting] = useState(false)
  const [currentLevelIndex, setCurrentLevelIndex] = useState(0)
  const [levelStats, setLevelStats] = useState([])
  const [finished, setFinished] = useState(false)
  const [totalTime, setTotalTime] = useState(0)

  // Загружаем информацию об игре (без запуска таймера): игрок видит экран описания.
  useEffect(() => {
    if (!gameId) {
      setGame(null)
      setProgress(null)
      return undefined
    }
    let cancelled = false
    setLoading(true)
    setError('')
    fetchGame(gameId)
      .then((data) => {
        if (cancelled) return
        setGame(data.game)
        setProgress(data.progress)
        setGameStarted(false)
        setCurrentLevelIndex(0)
        setLevelStats([])
        setFinished(false)
        setTotalTime(0)
      })
      .catch((err) => { if (!cancelled) setError(apiMessage(err)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [gameId, currentUserId])

  /** Начать игру: запускаем серверный таймер и показываем первый уровень. */
  const startPlaying = async () => {
    if (!game || starting) return
    setStarting(true)
    setError('')
    try {
      const data = await startGame(game.id)
      setGame(data.game)
      setProgress(data.progress)
      setCurrentLevelIndex(0)
      setLevelStats([])
      setFinished(false)
      setTotalTime(0)
      setGameStarted(true)
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setStarting(false)
    }
  }

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
      {onExit && (
        <button type="button" className="btn btn-secondary assoc-back-btn" onClick={onExit}>
          ← К списку игр
        </button>
      )}

      <div className="assoc-player-top">
        <div className="assoc-player-heading">
          <h2 className="assoc-player-title">{game.title}</h2>
          <span className="assoc-player-author">
            автор: {game.creatorUsername || 'неизвестен'} · лимит: {formatMinutes(game.timeLimitMinutes)} мин.
          </span>
        </div>
      </div>

      {!gameStarted ? (
        <GameInfoScreen
          game={game}
          levelsCount={levels.length}
          starting={starting}
          onStart={startPlaying}
        />
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

/** Экран информации об игре перед стартом. */
function GameInfoScreen({ game, levelsCount, starting, onStart }) {
  const media = Array.isArray(game.mediaUrls) ? game.mediaUrls : []
  const description = (game.description || '').trim()
  const date = formatGameDate(game.gameDate)
  const stars = difficultyStars(game.difficulty)

  return (
    <div className="assoc-start-screen">
      <div className="assoc-info-card">
        <h2 className="assoc-info-title">{game.title}</h2>

        <div className="assoc-info-meta">
          <span className="assoc-info-badge">
            <span aria-hidden="true">👤</span> {game.creatorUsername || 'неизвестен'}
          </span>
          {date && (
            <span className="assoc-info-badge">
              <span aria-hidden="true">📅</span> {date}
            </span>
          )}
          <span className="assoc-info-badge">
            <span aria-hidden="true">🔥</span>
            {stars ? <span className="assoc-info-stars">{stars}</span> : 'Сложность не указана'}
          </span>
          <span className="assoc-info-badge">
            <span aria-hidden="true">⏱️</span> {formatMinutes(game.timeLimitMinutes)} минут
          </span>
          <span className="assoc-info-badge">
            <span aria-hidden="true">🎯</span> {levelsCount} {levelsCount === 1 ? 'уровень' : 'уровней'}
          </span>
        </div>

        {media.length > 0 && (
          <div className="assoc-info-block">
            <h3 className="assoc-info-block-title">Материалы игры</h3>
            <div className="assoc-info-media">
              {media.map((url, index) => {
                const kind = mediaKind(url)
                if (kind === 'image') {
                  return <img key={index} src={url} alt="Медиа к игре" className="assoc-info-media-img" />
                }
                if (kind === 'audio') {
                  return <audio key={index} src={url} controls className="assoc-info-media-audio" />
                }
                if (kind === 'video') {
                  return <video key={index} src={url} controls className="assoc-info-media-video" />
                }
                return (
                  <a key={index} href={url} target="_blank" rel="noreferrer" className="assoc-info-media-link">
                    📎 {url}
                  </a>
                )
              })}
            </div>
          </div>
        )}

        {description && (
          <div className="assoc-info-block">
            <h3 className="assoc-info-block-title">Описание</h3>
            <p className="assoc-info-description">{description}</p>
          </div>
        )}

        <div className="assoc-info-actions">
          <button type="button" className="btn btn-primary assoc-info-start" onClick={onStart} disabled={starting}>
            {starting ? 'Запускаем…' : 'Начать игру'}
          </button>
        </div>
      </div>
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
