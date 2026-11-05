import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import {
  disconnectTeamGame, endTeamGame, getTeamGameState, heartbeatTeamGame,
  joinTeamGame, leaveTeamGame, requestTeamHint, submitTeamAnswer,
} from '../../api/teamGames'
import { apiMessage } from '../../api/client'
import { formatTime } from './constants'

const WS_URL = import.meta.env.VITE_WS_URL || '/ws'

function mediaKind(url) {
  const lower = (url || '').toLowerCase()
  if (/\.(mp4|webm|m4v|mov)(\?|$)/.test(lower)) return 'video'
  if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower)) return 'audio'
  if (/\.(png|jpg|jpeg|gif|webp)(\?|$)/.test(lower)) return 'image'
  return 'other'
}

function renderMedia(urls, onImageClick) {
  if (!Array.isArray(urls) || urls.length === 0) return null
  return (
    <div className="assoc-normal-media" style={{ flexDirection: 'column' }}>
      {urls.map((url, idx) => {
        const kind = mediaKind(url)
        if (kind === 'image') return <img key={idx} src={url} alt="Медиа" style={{ cursor: 'zoom-in' }} onClick={() => onImageClick?.(url)} />
        if (kind === 'audio') return <audio key={idx} src={url} controls />
        if (kind === 'video') return <video key={idx} src={url} controls />
        return null
      })}
    </div>
  )
}

function finishReasonText(reason) {
  switch ((reason || '').toLowerCase()) {
    case 'host_ended':
      return 'Игра завершена ведущим'
    case 'time_expired':
      return 'Время игры истекло'
    case 'completed':
      return 'Команда прошла игру!'
    default:
      return 'Игра завершена'
  }
}

/**
 * Игровой экран командной партии (co-op).
 *
 * Все действия уходят на сервер, а сервер рассылает события комнате
 * /topic/team_session_{sessionId}: sync_correct_answer, sync_hint_used,
 * sync_level_up, PRESENCE, end_team_game.
 *
 * Права:
 *  - хост (тот, кто запустил игру) видит «Завершить игру»;
 *  - остальные участники видят «Покинуть игру» — партия при этом продолжается.
 */
export default function TeamGamePlay({
  sessionId, initialState, onExit, currentUserId, onMissing, onEnded,
}) {
  const [state, setState] = useState(initialState)
  const [loading, setLoading] = useState(!initialState)
  const [error, setError] = useState('')
  const [answer, setAnswer] = useState('')
  const [checking, setChecking] = useState(false)
  const [wrong, setWrong] = useState(false)
  const [flash, setFlash] = useState('')
  const [hintNotice, setHintNotice] = useState(null)
  const [ending, setEnding] = useState(false)
  const [showEndConfirm, setShowEndConfirm] = useState(false)
  const [finishedFromServer, setFinishedFromServer] = useState(false)
  const [fullscreenImage, setFullscreenImage] = useState(null)
  const expiryFetchRef = useRef(null)
  const wrongTimer = useRef(null)
  const flashTimer = useRef(null)
  const hintTimer = useRef(null)
  const [now, setNow] = useState(Date.now())
  const [hintCooldownUntil, setHintCooldownUntil] = useState(0)

  const applyState = useCallback((s) => {
    if (!s) return
    setState(s)
    setLoading(false)
  }, [])

  const showFlash = useCallback((text, ms = 2500) => {
    setFlash(text)
    if (flashTimer.current) clearTimeout(flashTimer.current)
    flashTimer.current = setTimeout(() => setFlash(''), ms)
  }, [])

  const showHint = useCallback((payload) => {
    const text = payload.text || payload.firstLetter
    if (!text && !payload.answer) return
    setHintNotice({ text: text || payload.answer, imageUrl: payload.imageUrl, penaltySeconds: payload.penaltySeconds })
    if (hintTimer.current) clearTimeout(hintTimer.current)
    hintTimer.current = setTimeout(() => setHintNotice(null), 8000)
  }, [])

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  /**
   * Клиентский отсчёт — только подсказка. Когда ends_at прошёл, перезапрашиваем
   * состояние у сервера. Финальный статус выставляет только серверный таймер.
   */
  useEffect(() => {
    const status = String(state?.status || '').toLowerCase()
    const endsAtMs = state?.endsAt ? new Date(state.endsAt).getTime() : null
    if (status !== 'active' || !endsAtMs || now < endsAtMs) return
    const lastAttempt = expiryFetchRef.current || 0
    if (now - lastAttempt < 5000) return
    expiryFetchRef.current = now
    getTeamGameState(sessionId).then(applyState).catch(() => {})
  }, [now, state?.status, state?.endsAt, sessionId, applyState])

  // Подключение к партии: получаем полный слепок состояния.
  useEffect(() => {
    if (initialState) {
      applyState(initialState)
      joinTeamGame(sessionId)
        .then((s) => applyState(s))
        .catch((err) => {
          // Сессия исчезла — возвращаем игрока к списку игр.
          // Завершённая сессия не ошибка: join вернёт её актуальное состояние.
          if (err?.response?.status === 404 || err?.response?.status === 409) {
            if (onMissing) onMissing()
          }
        })
    } else {
      getTeamGameState(sessionId)
        .then((s) => applyState(s))
        .catch((err) => {
          // Сессия исчезла — возвращаем игрока к экрану игры без ошибки.
          if (err?.response?.status === 404) {
            if (onMissing) onMissing()
            return
          }
          setError(apiMessage(err))
        })
        .finally(() => setLoading(false))
    }
    // Периодическое обновление времени присутствия, чтобы статус «онлайн» не устаревал.
    const heartbeat = setInterval(() => {
      heartbeatTeamGame(sessionId).catch(() => {})
    }, 30000)
    return () => {
      clearInterval(heartbeat)
      leaveTeamGame(sessionId).catch(() => {})
    }
  }, [sessionId, initialState, applyState, onMissing])

  // Подписка на комнату партии: все изменения приходят сюда.
  useEffect(() => {
    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      connectHeaders: {
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
      onConnect: () => {
        stompClient.subscribe('/topic/team_session_' + sessionId, (message) => {
          try {
            const payload = JSON.parse(message.body)
            if (payload.state) applyState(payload.state)

            switch (payload.type) {
              case 'sync_correct_answer':
                // Слово угадано — показываем его всем участникам.
                setWrong(false)
                setAnswer('')
                showFlash(`✅ ${payload.username || 'Игрок'} разгадал: ${payload.answer}`)
                break
              case 'sync_hint_used':
                showHint(payload)
                showFlash(`💡 ${payload.username || 'Команда'} взяла подсказку`
                  + (payload.penaltySeconds ? ` (+${payload.penaltySeconds} сек.)` : ''))
                break
              case 'sync_level_up':
                setAnswer('')
                showFlash('🎉 Уровень пройден! Открыт следующий уровень')
                break
              case 'sync_state':
                if (payload.state) applyState(payload.state)
                break
              case 'ANSWER_WRONG':
                if (String(payload.userId) === String(currentUserId)) {
                  setWrong(true)
                  setAnswer('')
                  if (wrongTimer.current) clearTimeout(wrongTimer.current)
                  wrongTimer.current = setTimeout(() => setWrong(false), 2000)
                }
                break
              case 'end_team_game':
                setFinishedFromServer(true)
                if (!payload.state) {
                  // Событие без слепка (старый формат) — добираем актуальное состояние.
                  getTeamGameState(sessionId).then(applyState).catch(() => {})
                }
                showFlash(finishReasonText(payload.endReason || payload.reason))
                if (onEnded) onEnded(payload.state)
                break
              case 'DISCONNECTED':
                if (String(payload.userId) === String(currentUserId)) {
                  showFlash('Вы отключились от партии')
                }
                break
              default:
                break
            }
          } catch (e) {
            // некорректное сообщение игнорируем
          }
        })
        // Клиенты, присоединившиеся позже, запрашивают полный слепок состояния
        stompClient.publish({ destination: '/app/team-game/request-sync', body: JSON.stringify({ sessionId }) })
      },
    })
    stompClient.activate()
    return () => {
      if (wrongTimer.current) clearTimeout(wrongTimer.current)
      if (flashTimer.current) clearTimeout(flashTimer.current)
      if (hintTimer.current) clearTimeout(hintTimer.current)
      stompClient.deactivate()
    }
  }, [sessionId, currentUserId, applyState, showFlash, showHint, onEnded])

  const game = state?.game
  const levels = game?.levels || []
  const levelIndex = state?.currentLevelIndex || 0
  const level = levels[levelIndex]
  const solved = state?.solved || {}
  const penalty = state?.penaltyTime || 0
  const events = state?.events || []
  const memberNames = state?.memberNames || {}
  const onlineUserIds = state?.onlineUserIds || []
  const isHost = state?.currentUserHost === true
  const hostUsername = state?.hostUsername
  const status = String(state?.status || '').toLowerCase()
  const isFinished = status === 'finished' || finishedFromServer
  const endReason = state?.endReason || ''
  const finishTitle = finishReasonText(endReason)

  // Таймеры (на сервере - независимы от подключений)
  const startTimeMs = state?.startTime
    ? new Date(state.startedAt || state.startTime).getTime()
    : Date.now()
  const gameElapsedSec = Math.max(0, Math.floor((now - startTimeMs) / 1000))
  const gameEndsAtMs = state?.endsAt ? new Date(state.endsAt).getTime() : null
  const gameRemainingSec = gameEndsAtMs ? Math.max(0, Math.floor((gameEndsAtMs - now) / 1000)) : null
  const levelEndTimeMs = state?.levelEndTime ? new Date(state.levelEndTime).getTime() : null
  const levelTimeLimitSec = level?.timeLimitMinutes ? Math.round(level.timeLimitMinutes * 60) : (levelEndTimeMs ? Math.max(0, Math.floor((levelEndTimeMs - startTimeMs) / 1000)) : null)
  const levelRemainingSec = levelEndTimeMs ? Math.max(0, Math.floor((levelEndTimeMs - now) / 1000)) : levelTimeLimitSec
  const hintCooldownSec = hintCooldownUntil > now ? Math.max(0, Math.ceil((hintCooldownUntil - now) / 1000)) : 0

  const questions = level?.questions || []
  const currentQuestion = questions[0]

  const questionSolvedCount = currentQuestion
    ? Object.keys(solved).filter((k) => k && k.startsWith('l' + level.id + ':q' + currentQuestion.id + ':')).length
    : 0
  const passThreshold = currentQuestion?.passThreshold || 0

  const solvedWords = Object.values(solved || {}).filter(Boolean)

  const handleSubmit = async () => {
    if (!currentQuestion || checking) return
    const trimmed = (answer || '').trim()
    if (!trimmed) return
    setChecking(true)
    setWrong(false)
    try {
      const res = await submitTeamAnswer(sessionId, currentQuestion.id, trimmed)
      if (res) applyState(res)
      setAnswer('')
    } catch (err) {
      setWrong(true)
      setError(apiMessage(err))
      setTimeout(() => setWrong(false), 2000)
    } finally {
      setChecking(false)
    }
  }

  const handleHint = async () => {
    if (!currentQuestion) return
    if (hintCooldownSec > 0) return
    try {
      const res = await requestTeamHint(sessionId, currentQuestion.id, 0)
      if (res) applyState(res)
      setHintCooldownUntil(Date.now() + 60000)
    } catch (err) {
      setError(apiMessage(err))
    }
  }

  /** Завершить партию — только хост. Локальный статус меняем лишь после ответа сервера. */
  const handleEnd = async () => {
    if (ending) return
    setShowEndConfirm(false)
    setEnding(true)
    setError('')
    try {
      const res = await endTeamGame(sessionId)
      if (res) {
        applyState(res)
        if (onEnded) onEnded(res)
      }
    } catch (err) {
      // Сервер не подтвердил завершение — остаёмся на активном экране.
      setError(apiMessage(err))
    } finally {
      setEnding(false)
    }
  }

  /** Покинуть партию — партия продолжается без нас. */
  const handleLeave = async () => {
    try {
      await leaveTeamGame(sessionId)
    } catch {
      // не критично
    }
    if (onExit) onExit()
  }

  if (loading) return <div className="page-loader">Загрузка командной игры…</div>
  if (error && !state) return <div className="alert alert-error">{error}</div>
  if (!state) return null
  if (!game) {
    return (
      <div className="assoc-player">
        {onExit && (
          <button type="button" className="btn btn-secondary assoc-back-btn" onClick={onExit}>
            ← К списку игр
          </button>
        )}
        <div className="alert alert-error">Не удалось загрузить данные игры. Попробуйте обновить или переподключиться.</div>
        <button type="button" className="btn btn-primary" onClick={() => {
          getTeamGameState(sessionId).then(applyState).catch((e) => setError(apiMessage(e)))
        }}>Переподключиться</button>
      </div>
    )
  }

  if (isFinished) {
    const completed = String(endReason).toLowerCase() === 'completed'
    return (
      <div className="assoc-start-screen">
        <div className="assoc-info-card">
          <div className="assoc-win-emoji" aria-hidden="true">{completed ? '🏆' : '🏁'}</div>
          <h2 className="assoc-info-title">{finishTitle}</h2>
          <p className="assoc-info-description">
            Штрафное время команды: +{formatTime(Math.round(penalty * 60))}
          </p>
          <p className="assoc-info-description">
            Пройдено уровней: {Math.min(levelIndex + (completed ? 1 : 0), levels.length)} из {levels.length}
          </p>
          <div className="assoc-info-actions">
            {onExit && (
              <button type="button" className="btn btn-secondary" onClick={onExit}>
                К списку игр
              </button>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="assoc-player">
      {onExit && (
        <button type="button" className="btn btn-secondary assoc-back-btn" onClick={onExit}>
          ← К списку игр
        </button>
      )}

      <div className="assoc-player-top">
        <div className="assoc-player-heading">
          <h2 className="assoc-player-title">{game.title} · Командный режим</h2>
          <span className="assoc-player-author">
            Хост: {hostUsername || '—'}{isHost ? ' (вы)' : ''}
          </span>
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <span className="badge" title="Время игры">⏱ Игра: {formatTime(gameElapsedSec)}</span>
          {gameRemainingSec != null && (
            <span
              className="badge"
              style={{ color: gameRemainingSec <= 30 ? '#ff4d4f' : undefined, fontWeight: 700 }}
              title="Осталось до автоматического завершения игры"
            >
              ⏳ До конца: {formatTime(gameRemainingSec)}
            </span>
          )}
          {levelEndTimeMs ? (
            <span className="badge" style={{ color: levelRemainingSec <= 10 ? '#ff4d4f' : '#ff8c00', fontWeight: 700 }} title="Обратный отсчёт до окончания уровня">⏲ Уровень: {formatTime(levelRemainingSec)}</span>
          ) : levelTimeLimitSec ? (
            <span className="badge" title="Таймер уровня">⏲ Уровень: {formatTime(levelRemainingSec || levelTimeLimitSec)}</span>
          ) : null}
          <span className="badge" title="Таймер подсказки">💡 Подсказка: {hintCooldownSec > 0 ? `${hintCooldownSec}с` : 'доступна'}</span>
        </div>
        {/* Завершить игру может только хост, остальные — покинуть. */}
        {isHost ? (
          <button
            type="button"
            className="btn btn-danger"
            onClick={() => setShowEndConfirm(true)}
            disabled={ending}
          >
            {ending ? 'Завершаем…' : 'Завершить игру'}
          </button>
        ) : (
          <button type="button" className="btn btn-secondary" onClick={handleLeave}>
            Покинуть игру
          </button>
        )}
      </div>

      {showEndConfirm && (
        <div className="modal-overlay" onClick={() => !ending && setShowEndConfirm(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '520px', width: '90%' }}>
            <h3 className="modal-title">Завершить игру для всех участников?</h3>
            <p className="modal-text">
              Партия будет остановлена для всей команды. Продолжить её после завершения нельзя.
            </p>
            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setShowEndConfirm(false)}
                disabled={ending}
              >
                Отмена
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={handleEnd}
                disabled={ending}
              >
                {ending ? 'Завершаем…' : 'Да, завершить'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Индикаторы присутствия участников команды */}
      <div className="assoc-team-presence">
        {Object.entries(memberNames).map(([uid, name]) => {
          const online = onlineUserIds.includes(String(uid))
          const isSessionHost = String(uid) === String(state.hostId)
          return (
            <span key={uid} className={'assoc-presence-chip' + (online ? ' online' : '')}>
              {name}{online ? ' ●' : ''}{isSessionHost ? ' 👑' : ''}
            </span>
          )
        })}
      </div>

      <div className="assoc-level-progress">
        Уровень {levelIndex + 1} из {levels.length} · Штраф: +{formatTime(Math.round(penalty * 60))}
      </div>

      {flash && <div className="alert alert-success assoc-team-flash">{flash}</div>}
      {error && <div className="alert alert-error">{error}</div>}

      <div className="assoc-normal-card">
        <h3 className="assoc-normal-question">{level?.title}</h3>
        {renderMedia(level?.mediaUrls, setFullscreenImage)}

        {currentQuestion ? (
          <>
            <p className="assoc-normal-question">{currentQuestion.questionText}</p>
            {renderMedia(currentQuestion.mediaUrls, setFullscreenImage)}
            <p className="assoc-normal-progress">
              Правильно: <strong>{questionSolvedCount}</strong> из <strong>{passThreshold}</strong>
            </p>
          </>
        ) : (
          <p className="assoc-normal-hints-empty">В этом уровне нет вопросов</p>
        )}

        {/* Подсказка, полученная командой */}
        {hintNotice && (
          <div className="assoc-team-hint-notice">
            {hintNotice.imageUrl && (
              <img src={hintNotice.imageUrl} alt="Подсказка" onClick={() => setFullscreenImage(hintNotice.imageUrl)} />
            )}
            <span>{hintNotice.text}</span>
            {hintNotice.penaltySeconds ? (
              <span className="assoc-team-hint-penalty">штраф: +{hintNotice.penaltySeconds} сек.</span>
            ) : null}
          </div>
        )}

        {solvedWords.length > 0 && (
          <div className="assoc-normal-guessed">
            <div className="assoc-normal-guessed-title">Угаданные слова</div>
            <ul className="assoc-normal-guessed-list">
              {solvedWords.map((w, idx) => (
                <li key={idx} className="assoc-normal-guessed-item">{w}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="assoc-normal-answer" style={{ border: wrong ? '1px solid #ff4d4f' : undefined }}>
          <input
            className="assoc-input"
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSubmit() }}
            placeholder="Командный ответ"
            disabled={checking || !currentQuestion}
          />
          <button type="button" className="btn btn-primary" onClick={handleSubmit} disabled={checking || !currentQuestion}>
            Отправить
          </button>
          <button type="button" className="btn btn-secondary" onClick={handleHint} disabled={!currentQuestion || hintCooldownSec > 0}>
            💡 Подсказка{hintCooldownSec > 0 ? ` (${hintCooldownSec}с)` : ''}
          </button>
        </div>
      </div>

      {/* Лента событий команды */}
      <div className="assoc-info-block" style={{ marginTop: 16 }}>
        <h3 className="assoc-info-block-title">Лента событий</h3>
        <ul className="assoc-event-list">
          {(events || []).slice().reverse().map((ev, idx) => (
            <li key={idx} className="assoc-event-item">
              <span>{ev.text}</span>
            </li>
          ))}
        </ul>
      </div>

      {fullscreenImage && (
        <div
          onClick={() => setFullscreenImage(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.9)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            cursor: 'zoom-out',
            padding: '20px',
          }}
        >
          <img
            src={fullscreenImage}
            alt="Медиа"
            style={{ maxWidth: '90%', maxHeight: '90%' }}
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}
    </div>
  )
}
