import { useEffect, useMemo, useRef, useState } from 'react'
import Modal from '../Modal'
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
  const passThreshold = Number(level.passThreshold) > 0 ? Number(level.passThreshold) : questions.length
  const limitSeconds = Math.max(1, Math.round((Number(level.timeLimitMinutes) || 10) * 60))

  const questionKey = (q) => `l${level.id}:q${q.id}`

  const [solved, setSolved] = useState(() => progress?.solved || {})
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0)
  const [answers, setAnswers] = useState({})
  const [checking, setChecking] = useState(false)
  const [actionError, setActionError] = useState('')
  const [penaltySeconds, setPenaltySeconds] = useState(0)
  const [timeUp, setTimeUp] = useState(false)
  const [hintModal, setHintModal] = useState(null)

  const levelStartedAt = useRef(Date.now())
  const [now, setNow] = useState(Date.now())
  const completedRef = useRef(false)
  // время старта таймеров подсказок для каждого вопроса (при первом открытии)
  const hintStartAt = useRef({})
  // использованные подсказки: { [questionId]: [индексы подсказок] }
  const [usedHints, setUsedHints] = useState({})

  const currentQuestion = questions[currentQuestionIndex]
  const solvedCount = questions.filter((q) => solved[questionKey(q)]).length
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

  // если порог уже достигнут (восстановление прогресса) — сразу завершаем уровень
  useEffect(() => {
    if (questions.length > 0 && solvedCount >= passThreshold) {
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
    try {
      const result = await checkAnswer(game.id, null, null, text, question.id)
      const nextSolved = result.progress?.solved || {}
      setSolved(nextSolved)
      if (result.correct) {
        setActionError('')
        const nextCount = questions.filter((q) => nextSolved[questionKey(q)]).length
        if (nextCount >= passThreshold) {
          completeLevel({ netTime: netElapsed, penaltyTime: penaltySeconds, timedOut: false })
        } else {
          // переходим к следующему нерешённому вопросу
          const nextIndex = questions.findIndex((q) => !nextSolved[questionKey(q)])
          if (nextIndex >= 0) {
            setCurrentQuestionIndex(nextIndex)
          }
        }
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

    setHintModal({ hintIndex, hint, content })
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
          Дайте {passThreshold} из {questions.length} правильных ответов.
        </p>
        <div className="assoc-timer" role="timer" aria-live={timerCritical ? 'polite' : 'off'}>
          <span className="assoc-timer-icon" aria-hidden="true">⏱</span>
          <span className="assoc-timer-value">{formatTime(remainingSeconds)}</span>
          <span className="assoc-timer-label">{timeUp ? 'время вышло' : 'осталось'}</span>
        </div>
      </div>

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
                  onChange={(event) => setCurrentQuestionIndex(Number(event.target.value))}
                >
                  {questions.map((q, index) => (
                    <option key={q.id} value={index}>
                      Вопрос {index + 1}{solved[questionKey(q)] ? ' ✓' : ''}
                    </option>
                  ))}
                </select>
              </label>
              <span className="assoc-normal-indicator">Вопрос {currentQuestionIndex + 1} из {questions.length}</span>
              <span className="assoc-normal-indicator">Правильно: {solvedCount} из {questions.length}</span>
            </div>
          )}

          {/* Блок вопроса */}
          {currentQuestion?.mediaType && currentQuestion?.mediaUrl ? (
            <div className="assoc-normal-media">
              {currentQuestion.mediaType === 'image' ? (
                <img src={currentQuestion.mediaUrl} alt="Изображение к вопросу" />
              ) : currentQuestion.mediaType === 'audio' ? (
                <audio controls src={currentQuestion.mediaUrl} />
              ) : currentQuestion.mediaType === 'video' ? (
                <video controls src={currentQuestion.mediaUrl} />
              ) : null}
            </div>
          ) : null}

          <h3 className="assoc-normal-question">{currentQuestion?.questionText}</h3>

          {/* Блок подсказок (всегда виден) */}
          <div className="assoc-normal-hints">
            <div className="assoc-normal-hints-title">Подсказки</div>
            {hints.length === 0 && <p className="assoc-normal-hints-empty">Подсказки не настроены</p>}
            {hints.map((hint, hintIndex) => {
              const state = hintState(hintIndex)
              return (
                <div key={hint.id ?? hintIndex} className="assoc-normal-hint">
                  {state.type === 'used' ? (
                    <span className="assoc-normal-hint-used">{HINT_NAMES[hintIndex] || `Подсказка ${hintIndex + 1}`} — использована</span>
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

      {hintModal && (
        <Modal title={hintModal.hint.type === 'FIRST_LETTER' ? 'Первая буква' : 'Подсказка'} onClose={() => setHintModal(null)}>
          <div className="assoc-normal-hint-modal">
            {hintModal.hint.type === 'FIRST_LETTER' && hintModal.content?.firstLetter ? (
              <p className="assoc-normal-hint-letter">
                Первая буква ответа: <strong>{hintModal.content.firstLetter}</strong>
              </p>
            ) : null}
            {hintModal.hint.type !== 'FIRST_LETTER' && hintModal.content?.text ? (
              <p>{hintModal.content.text}</p>
            ) : null}
            {hintModal.hint.type !== 'FIRST_LETTER' && hintModal.content?.imageUrl ? (
              <img src={hintModal.content.imageUrl} alt="Подсказка" className="assoc-normal-hint-img" />
            ) : null}
            <div className="form-actions">
              <button type="button" className="btn btn-primary" onClick={() => setHintModal(null)}>
                Закрыть
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
