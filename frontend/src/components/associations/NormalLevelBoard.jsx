import { useEffect, useMemo, useRef, useState } from 'react'
import { apiMessage } from '../../api/client'
import { checkAnswer, revealQuestionHint } from '../../api/games'
import { formatTime } from './constants'

const HINT_NAMES = ['Первая подсказка', 'Вторая подсказка', 'Третья подсказка', 'Четвёртая подсказка', 'Пятая подсказка']

/**
 * Игровое поле уровня типа «Обычный»: вопросы с медиа, навигация,
 * блок подсказок с таймерами и поле ввода ответа.
 */
export default function NormalLevelBoard({
  game, level, progress, onLevelComplete,
}) {
  const questions = useMemo(() => level.questions || [], [level.questions])
  const hints = useMemo(() => level.hints || [], [level.hints])
  const limitSeconds = Math.max(1, Math.round((Number(level.timeLimitMinutes) || 10) * 60))

  const questionKey = (q) => `l${level.id}:q${q.id}`
  const questionPrefix = (q) => `${questionKey(q)}:`
  const matchedCount = (solvedMap, q) =>
    Object.keys(solvedMap || {}).filter((k) => k.startsWith(questionPrefix(q))).length
  const effectiveThreshold = (q) => {
    const t = Number(q.passThreshold)
    return t > 0 ? t : 1
  }
  const isQuestionSolved = (solvedMap, q) => matchedCount(solvedMap, q) >= effectiveThreshold(q)

  const [solved, setSolved] = useState(() => progress?.solved || {})
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0)
  const [answers, setAnswers] = useState({})
  const [checking, setChecking] = useState(false)
  const [actionError, setActionError] = useState('')
  const [acceptedAnswer, setAcceptedAnswer] = useState(null)
  const [alreadyGuessed, setAlreadyGuessed] = useState(false)
  const [penaltySeconds, setPenaltySeconds] = useState(0)
  const [timeUp, setTimeUp] = useState(false)
  // содержимое использованных подсказок: { [questionId]: { [hintIndex]: content } }
  const [hintContents, setHintContents] = useState({})

  const levelStartedAt = useRef(Date.now())
  const [now, setNow] = useState(Date.now())
  const completedRef = useRef(false)
  // время старта таймеров подсказок для каждого вопроса (при первом открытии)
  const hintStartAt = useRef({})
  // использованные подсказки: { [questionId]: [индексы подсказок] }
  const [usedHints, setUsedHints] = useState({})

  const currentQuestion = questions[currentQuestionIndex]
  const solvedCount = questions.filter((q) => isQuestionSolved(solved, q)).length
  const netElapsed = Math.max(0, Math.floor((now - levelStartedAt.current) / 1000))
  const remainingSeconds = Math.max(0, limitSeconds - netElapsed)

  // тикающие часы
  useEffect(() => {
    if (timeUp) return undefined
    const timer = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(timer)
  }, [timeUp])

  // фиксируем старт таймеров подсказок при первом показе вопроса
  useEffect(() => {
    if (currentQuestion && hintStartAt.current[currentQuestion.id] == null) {
      hintStartAt.current[currentQuestion.id] = Date.now()
    }
  }, [currentQuestion])

  // быстрое уведомление об уже угаданном слове — исчезает сам
  useEffect(() => {
    if (!alreadyGuessed) return undefined
    const timer = setTimeout(() => setAlreadyGuessed(false), 3000)
    return () => clearTimeout(timer)
  }, [alreadyGuessed])

  const completeLevel = (payload) => {
    if (completedRef.current) return
    completedRef.current = true
    setTimeUp(true)
    if (onLevelComplete) onLevelComplete(payload)
  }

  // истечение времени уровня
  useEffect(() => {
    if (completedRef.current || netElapsed < limitSeconds) return
    completeLevel({ netTime: limitSeconds, penaltyTime: penaltySeconds, timedOut: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [netElapsed, limitSeconds])

  // если все вопросы уже разгаданы (восстановление прогресса) — сразу завершаем уровень
  useEffect(() => {
    if (questions.length > 0 && questions.every((q) => isQuestionSolved(solved, q))) {
      completeLevel({ netTime: 0, penaltyTime: 0, timedOut: false })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submitAnswer = async () => {
    const question = currentQuestion
    if (!question || timeUp) return
    const text = (answers[question.id] || '').trim()
    if (!text) return
    setChecking(true)
    setActionError('')
    setAcceptedAnswer(null)
    setAlreadyGuessed(false)
    // повторный ввод уже угаданного слова — не отправляем на проверку, показываем уведомление
    const alreadyGuessedNow = Object.keys(solved || {}).some(
      (k) => k.startsWith(questionPrefix(question)) && (solved[k] || '').trim().toLowerCase() === text.trim().toLowerCase()
    )
    if (alreadyGuessedNow) {
      setAlreadyGuessed(true)
      return
    }
    try {
      const result = await checkAnswer(game.id, null, null, text, question.id)
      const nextSolved = result.progress?.solved || {}
      setSolved(nextSolved)
      if (result.correct) {
        setActionError('')
        // очищаем ввод для следующего варианта ответа
        setAnswers((prev) => ({ ...prev, [question.id]: '' }))
        // показываем пользователю принятый правильный ответ (зелёным)
        setAcceptedAnswer(result.answer || null)
        if (isQuestionSolved(nextSolved, question)) {
          // вопрос разгадан — переходим к следующему неразгаданному или завершаем уровень
          const nextIndex = questions.findIndex((q) => !isQuestionSolved(nextSolved, q))
          if (nextIndex >= 0) {
            setCurrentQuestionIndex(nextIndex)
          } else {
            completeLevel({ netTime: netElapsed, penaltyTime: penaltySeconds, timedOut: false })
          }
        }
        // иначе остаёмся на том же вопросе: нужно дать ещё варианты ответа
      } else {
        setActionError('Неверный ответ. Попробуйте ещё раз.')
      }
    } catch (err) {
      setActionError(apiMessage(err))
    } finally {
      setChecking(false)
    }
  }

  const useHint = async (hintIndex) => {
    const question = currentQuestion
    if (!question || timeUp) return
    if ((usedHints[question.id] || []).includes(hintIndex)) return
    const hint = hints[hintIndex]
    if (!hint) return

    let content = null
    if (hint.type === 'FIRST_LETTER') {
      try {
        const revealed = await revealQuestionHint(game.id, question.id, hintIndex)
        if (revealed?.firstLetter) {
          setAnswers((prev) => {
            const current = (prev[question.id] || '').trim()
            if (current.toLowerCase().startsWith(revealed.firstLetter.toLowerCase())) return prev
            return { ...prev, [question.id]: revealed.firstLetter + current }
          })
          content = { firstLetter: revealed.firstLetter }
        }
      } catch (err) {
        setActionError(apiMessage(err))
        return
      }
    } else {
      content = { text: hint.text, imageUrl: hint.imageUrl }
    }

    setHintContents((prev) => ({
      ...prev,
      [question.id]: {
        ...(prev[question.id] || {}),
        [hintIndex]: content,
      },
    }))
    setUsedHints((prev) => ({
      ...prev,
      [question.id]: [...(prev[question.id] || []), hintIndex],
    }))
    if (hint.penaltyTime != null) {
      setPenaltySeconds((prev) => prev + Math.round(Number(hint.penaltyTime) * 60))
    }
  }

  const secondsSinceHintStart = currentQuestion
    ? Math.max(0, Math.floor((now - (hintStartAt.current[currentQuestion.id] ?? now)) / 1000))
    : 0

  const hintState = (hintIndex) => {
    const hint = hints[hintIndex]
    if (!hint) return { type: 'none' }
    if ((usedHints[currentQuestion?.id] || []).includes(hintIndex)) return { type: 'used' }
    const trigger = Number(hint.triggerTime)
    if (!Number.isFinite(trigger) || trigger <= 0) return { type: 'available' }
    const at = Math.round(trigger * 60)
    if (secondsSinceHintStart >= at) return { type: 'available' }
    return { type: 'countdown', secondsLeft: at - secondsSinceHintStart }
  }

  const timerCritical = remainingSeconds > 0 && remainingSeconds <= 60

  return (
    <div className="assoc-board">
      <div className="assoc-board-head">
        <p className="assoc-board-tip">
          Уровень «{level.title}». Время: <strong>{formatTime(remainingSeconds)}</strong>{' '}
          Разгадайте все {questions.length} {questions.length === 1 ? 'вопрос' : 'вопросов'}.
        </p>
        <div className="assoc-timer" role="timer" aria-live={timerCritical ? 'polite' : 'off'}>
          <span className="assoc-timer-icon" aria-hidden="true">⏱</span>
          <span className="assoc-timer-value">{formatTime(remainingSeconds)}</span>
          <span className="assoc-timer-label">{timeUp ? 'время вышло' : 'осталось'}</span>
        </div>
      </div>

      {level.mediaUrls?.length > 0 && (
        <div className="assoc-level-media">
          {level.mediaUrls.map((url, idx) => {
            const lower = (url || '').toLowerCase()
            if (/\.(mp4|webm|m4v|mov)(\?|$)/.test(lower)) {
              return <video key={idx} controls src={url} />
            }
            if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower)) {
              return <audio key={idx} controls src={url} />
            }
            if (/\.(png|jpg|jpeg|gif|webp)(\?|$)/.test(lower)) {
              return <img key={idx} src={url} alt="Медиа к уровню" />
            }
            return null
          })}
        </div>
      )}

      {actionError && <div className="alert alert-error">{actionError}</div>}

      <div className="assoc-normal">
        <div className="assoc-normal-card">
          {/* Навигация — выпадающий список вопроса в верхней левой части */}
          {questions.length > 1 && (
            <div className="assoc-normal-nav">
              <label className="field assoc-normal-select">
                <span>Вопрос</span>
                <select
                  value={currentQuestionIndex}
                  onChange={(event) => { setAcceptedAnswer(null); setCurrentQuestionIndex(Number(event.target.value)) }}
                >
                  {questions.map((q, index) => (
                    <option key={q.id} value={index}>
                      Вопрос {index + 1}{isQuestionSolved(solved, q) ? ' ✓' : ''}
                    </option>
                  ))}
                </select>
              </label>
              <span className="assoc-normal-indicator">Вопрос {currentQuestionIndex + 1} из {questions.length}</span>
              <span className="assoc-normal-indicator">Правильно: {solvedCount} из {questions.length}</span>
              {currentQuestion && (
                <span className="assoc-normal-indicator">
                  Текущий: {matchedCount(solved, currentQuestion)} из {effectiveThreshold(currentQuestion)} ответов
                </span>
              )}
            </div>
          )}

          {/* Блок вопроса */}
          {(currentQuestion?.mediaUrls?.length > 0 || (currentQuestion?.mediaType && currentQuestion?.mediaUrl)) && (
            <div className="assoc-normal-media">
              {(currentQuestion.mediaUrls?.length > 0 ? currentQuestion.mediaUrls : [currentQuestion.mediaUrl]).map((url, idx) => {
                const lower = (url || '').toLowerCase()
                if (lower.match(/\.(mp4|webm|ogg)(\?|$)/)) {
                  return <video key={idx} controls src={url} />
                }
                if (lower.match(/\.(mp3|wav|ogg)(\?|$)/)) {
                  return <audio key={idx} controls src={url} />
                }
                if (lower.match(/\.(png|jpg|jpeg|gif|webp)(\?|$)/) || currentQuestion.mediaType === 'image') {
                  return <img key={idx} src={url} alt="Изображение к вопросу" />
                }
                return null
              })}
            </div>
          )}

          <h3 className="assoc-normal-question">{currentQuestion?.questionText}</h3>

          {/* Блок подсказок (всегда виден) */}
          <div className="assoc-normal-hints">
            <div className="assoc-normal-hints-title">Подсказки</div>
            {hints.length === 0 && <p className="assoc-normal-hints-empty">Подсказки не настроены</p>}
            {hints.map((hint, hintIndex) => {
              const state = hintState(hintIndex)
              const content = hintContents[currentQuestion?.id]?.[hintIndex]
              return (
                <div key={hint.id ?? hintIndex} className="assoc-normal-hint">
                  {state.type === 'used' ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <span className="assoc-normal-hint-used">{HINT_NAMES[hintIndex] || `Подсказка ${hintIndex + 1}`} — использована</span>
                      {content?.firstLetter && (
                        <span className="assoc-normal-hint-letter">Первая буква ответа: <strong>{content.firstLetter}</strong></span>
                      )}
                      {hint.type !== 'FIRST_LETTER' && content?.text && <p style={{ margin: 0 }}>{content.text}</p>}
                      {hint.type !== 'FIRST_LETTER' && content?.imageUrl && (
                        <img src={content.imageUrl} alt="Подсказка" className="assoc-normal-hint-img" />
                      )}
                      {(hint.mediaUrls || []).map((url, i) => {
                        const lower = (url || '').toLowerCase()
                        if (/\.(mp4|webm|m4v|mov)(\?|$)/.test(lower)) {
                          return <video key={`m${i}`} src={url} controls className="assoc-normal-hint-img" />
                        }
                        if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower)) {
                          return <audio key={`m${i}`} src={url} controls style={{ width: '100%', maxWidth: 420 }} />
                        }
                        if (/\.(png|jpg|jpeg|gif|webp)(\?|$)/.test(lower)) {
                          return <img key={`m${i}`} src={url} alt="Подсказка" className="assoc-normal-hint-img" />
                        }
                        return null
                      })}
                    </div>
                  ) : state.type === 'available' ? (
                    <button type="button" className="assoc-hint-btn" onClick={() => useHint(hintIndex)}>
                      💡 {HINT_NAMES[hintIndex] || `Подсказка ${hintIndex + 1}`}
                    </button>
                  ) : (
                    <span className="assoc-hint-countdown">
                      💡 {HINT_NAMES[hintIndex] || `Подсказка ${hintIndex + 1}`} через{' '}
                      <strong>{formatTime(state.secondsLeft)}</strong>
                    </span>
                  )}
                  {hint.penaltyTime != null && (
                    <span className="assoc-normal-hint-penalty">штраф: +{Math.round(Number(hint.penaltyTime) * 60)} сек.</span>
                  )}
                </div>
              )
            })}
          </div>

          {/* Быстрое уведомление об уже угаданном слове */}
          {alreadyGuessed && (
            <div className="alert alert-warning">Слово уже угадано</div>
          )}

          {/* Принятый правильный ответ */}
          {acceptedAnswer && (
            <div className="alert alert-success">
              Правильно! Ответ принят:{' '}
              <span style={{ color: 'var(--success)', fontWeight: 700 }}>{acceptedAnswer}</span>
            </div>
          )}

          {/* Блок ввода ответа */}
          <div className="assoc-normal-answer">
            <input
              className="assoc-input"
              value={answers[currentQuestion?.id] || ''}
              onChange={(event) => setAnswers((prev) => ({ ...prev, [currentQuestion?.id]: event.target.value }))}
              disabled={checking || timeUp}
              onKeyDown={(event) => {
                if (event.key === 'Enter') submitAnswer()
              }}
              placeholder="Ваш ответ"
            />
            <button
              type="button"
              className="btn btn-primary"
              onClick={submitAnswer}
              disabled={checking || timeUp}
            >
              {checking ? 'Проверяем…' : 'Ответить'}
            </button>
          </div>
        </div>
      </div>


    </div>
  )
}
