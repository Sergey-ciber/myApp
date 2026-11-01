import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getTeamGameState, heartbeatTeamGame, joinTeamGame, leaveTeamGame, requestTeamHint, submitTeamAnswer } from '../../api/teamGames'
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

export default function TeamGamePlay({ sessionId, initialState, onExit, currentUserId }) {
  const [state, setState] = useState(initialState)
  const [loading, setLoading] = useState(!initialState)
  const [error, setError] = useState('')
  const [answer, setAnswer] = useState('')
  const [checking, setChecking] = useState(false)
  const [wrong, setWrong] = useState(false)
  const [flash, setFlash] = useState('')
  const [fullscreenImage, setFullscreenImage] = useState(null)
  const wrongTimer = useRef(null)
  const flashTimer = useRef(null)

  const applyState = useCallback((s) => {
    if (!s) return
    setState(s)
    setLoading(false)
  }, [])

  useEffect(() => {
    if (initialState) {
      applyState(initialState)
      joinTeamGame(sessionId).catch(() => {})
    } else {
      getTeamGameState(sessionId)
        .then((s) => applyState(s))
        .catch((err) => setError(apiMessage(err)))
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
  }, [sessionId, initialState, applyState])

  useEffect(() => {
    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient.subscribe('/topic/team_session_' + sessionId, (message) => {
          try {
            const payload = JSON.parse(message.body)
            if (payload.state) applyState(payload.state)
            if (payload.type === 'ANSWER_RESULT') {
              if (payload.correct) {
                setWrong(false)
                setAnswer('')
                setFlash('')
                if (payload.levelCompleted) {
                  setFlash('🎉 Уровень пройден!')
                  if (flashTimer.current) clearTimeout(flashTimer.current)
                  flashTimer.current = setTimeout(() => setFlash(''), 2000)
                }
              } else if (String(payload.userId) === String(currentUserId)) {
                setWrong(true)
                if (wrongTimer.current) clearTimeout(wrongTimer.current)
                wrongTimer.current = setTimeout(() => setWrong(false), 2000)
              }
            }
          } catch (e) {
            // ignore
          }
        })
      }
    })
    stompClient.activate()
    return () => {
      if (wrongTimer.current) clearTimeout(wrongTimer.current)
      if (flashTimer.current) clearTimeout(flashTimer.current)
      stompClient.deactivate()
    }
  }, [sessionId, currentUserId, applyState])

  const game = state?.game
  const levels = game?.levels || []
  const levelIndex = state?.currentLevelIndex || 0
  const level = levels[levelIndex]
  const solved = state?.solved || {}
  const penalty = state?.penaltyTime || 0
  const events = state?.events || []
  const memberNames = state?.memberNames || {}
  const onlineUserIds = state?.onlineUserIds || []

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
      applyState(res)
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
    try {
      const res = await requestTeamHint(sessionId, currentQuestion.id, 0)
      applyState(res)
    } catch (err) {
      setError(apiMessage(err))
    }
  }

  if (loading) return <div className="page-loader">Загрузка командной игры…</div>
  if (error && !state) return <div className="alert alert-error">{error}</div>
  if (!state || !game) return null

  if (state.status === 'FINISHED') {
    return (
      <div className="assoc-start-screen">
        <div className="assoc-info-card">
          <div className="assoc-win-emoji" aria-hidden="true">🏆</div>
          <h2 className="assoc-info-title">Команда прошла игру!</h2>
          <p className="assoc-info-description">Штрафное время команды: +{formatTime(Math.round(penalty * 60))}</p>
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
        </div>
      </div>

      {/* Индикаторы присутствия участников команды */}
      <div className="assoc-team-presence">
        {Object.entries(memberNames).map(([uid, name]) => {
          const online = onlineUserIds.includes(String(uid))
          return (
            <span key={uid} className={'assoc-presence-chip' + (online ? ' online' : '')}>
              {name}{online ? ' ●' : ''}
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
          <button type="button" className="btn btn-secondary" onClick={handleHint} disabled={!currentQuestion}>
            💡 Подсказка
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
    </div>
  )
}
