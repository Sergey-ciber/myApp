import { Fragment, useEffect, useRef, useState } from 'react'
import { apiMessage } from '../../api/client'
import { checkAnswer, requestHint } from '../../api/games'
import { COLUMN_SIZES, COLUMN_SPANS, formatTime, toPairs } from './constants'

const LAST_COLUMN = COLUMN_SIZES.length - 1

const emptyValues = () => COLUMN_SIZES.map((size) => Array(size).fill(''))

/** Ключ ячейки на сервере: колонка и позиция нумеруются с 1. */
const cellKey = (columnIndex, position) => `${columnIndex + 1}:${position + 1}`

/** Защита букв подсказки: префикс всегда остаётся в начале значения. */
const protectValue = (text, prefix) => {
  if (!prefix) return text
  const value = text || ''
  const lowerValue = value.toLowerCase()
  const lowerPrefix = prefix.toLowerCase()
  if (lowerValue.startsWith(lowerPrefix)) {
    return prefix + value.slice(prefix.length)
  }
  if (lowerPrefix.includes(lowerValue)) return prefix
  return prefix + value
}

/** Добавляет новые буквы подсказки, сохраняя уже введённый игроком текст. */
const withPrefix = (value, prefix, oldPrefix = '') => {
  if (!prefix) return value || ''
  const text = value || ''
  const lowerText = text.toLowerCase()
  const lowerPrefix = prefix.toLowerCase()
  if (lowerText.startsWith(lowerPrefix)) {
    return prefix + text.slice(prefix.length)
  }
  if (oldPrefix && lowerText.startsWith(oldPrefix.toLowerCase())) {
    const rest = text.slice(oldPrefix.length)
    if (rest.toLowerCase().startsWith(lowerPrefix)) {
      return prefix + rest.slice(prefix.length)
    }
    return prefix + rest
  }
  return prefix + text
}

/**
 * Игровое поле-пирамида для уровня типа «Игра в ассоциации».
 * Логика без изменений: пирамида 16/8/4/2/1, ответы проверяет сервер,
 * подсказки открывают первые буквы доступных неразгаданных ячеек.
 */
export default function GameBoard({
  game, level, progress, onLevelComplete,
}) {
  const [values, setValues] = useState(() => {
    const cells = emptyValues()
    COLUMN_SIZES.forEach((size, columnIndex) => {
      if (columnIndex === 0) return
      for (let position = 0; position < size; position++) {
        const key = cellKey(columnIndex, position)
        cells[columnIndex][position] = progress?.solved?.[key] || progress?.hintPrefixes?.[key] || ''
      }
    })
    return cells
  })
  const [solved, setSolved] = useState(() => progress?.solved || {})
  const [hintLevel, setHintLevel] = useState(progress?.hintLevel || 0)
  const [hintPrefixes, setHintPrefixes] = useState(() => progress?.hintPrefixes || {})
  const [takingHint, setTakingHint] = useState(false)
  const [hintNotice, setHintNotice] = useState(false)
  const hintWasAvailable = useRef(false)
  const [clock, setClock] = useState(() => ({
    baseElapsed: 0,
    loadedAt: Date.now(),
  }))
  const [elapsed, setElapsed] = useState(0)
  const [wrong, setWrong] = useState({})
  const [checking, setChecking] = useState({})
  const [timeUp, setTimeUp] = useState(false)
  const [actionError, setActionError] = useState('')
  const completedRef = useRef(false)

  const limitSeconds = Math.max(1, Math.round((Number(level.timeLimitMinutes) || 10) * 60))
  const remainingSeconds = Math.max(0, limitSeconds - elapsed)

  const elapsedSeconds = () =>
    clock.baseElapsed + Math.max(0, Math.floor((Date.now() - clock.loadedAt) / 1000))

  // тикающий таймер
  useEffect(() => {
    if (timeUp) return undefined
    const tick = () => setElapsed(elapsedSeconds())
    tick()
    const timer = setInterval(tick, 250)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clock, timeUp])

  const completeLevel = (payload) => {
    if (completedRef.current) return
    completedRef.current = true
    setTimeUp(true)
    if (onLevelComplete) onLevelComplete(payload)
  }

  // истечение времени уровня — переходим к следующему уровню
  useEffect(() => {
    if (completedRef.current || elapsed < limitSeconds) return
    completeLevel({ netTime: limitSeconds, timedOut: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elapsed, limitSeconds])

  // если пирамида уже разгадана (восстановление прогресса) — сразу завершаем уровень
  useEffect(() => {
    if (solved[cellKey(LAST_COLUMN, 0)]) {
      completeLevel({ netTime: 0, timedOut: false })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const isSolved = (columnIndex, position) =>
    columnIndex === 0 || Boolean(solved[cellKey(columnIndex, position)])

  const prefixFor = (columnIndex, position) => {
    if (columnIndex === 0 || isSolved(columnIndex, position)) return ''
    return hintPrefixes[cellKey(columnIndex, position)] || ''
  }

  // время до каждой подсказки — из настроек уровня
  const hintTriggerMinutes = (level.hints || []).map((hint) => hint.triggerTime)
  const maxHintLevel = hintTriggerMinutes.length
  const nextHintAt = hintLevel >= maxHintLevel
    ? null
    : Math.round(Number(hintTriggerMinutes[hintLevel]) * 60)
  const hintAvailable = !timeUp && nextHintAt != null && nextHintAt < limitSeconds && elapsed >= nextHintAt
  const hintSecondsLeft = nextHintAt == null ? 0 : Math.max(0, nextHintAt - elapsed)
  const hintScheduled = !timeUp && nextHintAt != null && nextHintAt < limitSeconds

  useEffect(() => {
    if (hintAvailable && !hintWasAvailable.current) {
      setHintNotice(true)
      hintWasAvailable.current = true
      const timer = setTimeout(() => setHintNotice(false), 8000)
      return () => clearTimeout(timer)
    }
    hintWasAvailable.current = hintAvailable
    return undefined
  }, [hintAvailable])

  const wordAt = (columnIndex, position) => (columnIndex === 0
    ? (game.startWords[position] || '')
    : solved[cellKey(columnIndex, position)] || '')

  const isHintOpen = (columnIndex, position) =>
    columnIndex === 1 ||
    (isSolved(columnIndex - 1, position * 2) && isSolved(columnIndex - 1, position * 2 + 1))

  const clearWrong = (key) =>
    setWrong((prev) => {
      if (!prev[key]) return prev
      const next = { ...prev }
      delete next[key]
      return next
    })

  const check = async (columnIndex, position) => {
    const key = cellKey(columnIndex, position)
    if (timeUp || isSolved(columnIndex, position) || checking[key]) return
    const text = (values[columnIndex][position] || '').trim()
    if (!text) {
      clearWrong(key)
      return
    }
    const prefix = prefixFor(columnIndex, position)
    const onlyPrefix = Boolean(prefix) && text.toLowerCase() === prefix.toLowerCase()

    setChecking((prev) => ({ ...prev, [key]: true }))
    setActionError('')
    try {
      const result = await checkAnswer(game.id, columnIndex + 1, position + 1, text)
      const nextSolved = result.progress?.solved || {}
      setSolved(nextSolved)
      if (result.progress?.hintLevel != null) setHintLevel(result.progress.hintLevel)
      if (result.progress?.hintPrefixes) setHintPrefixes(result.progress.hintPrefixes)
      if (result.correct) {
        clearWrong(key)
        setValues((prev) => prev.map((words, c) => (c === columnIndex
          ? words.map((word, p) => (p === position ? (nextSolved[key] || text) : word))
          : words)))
        if (columnIndex === LAST_COLUMN) {
          completeLevel({ netTime: elapsedSeconds() })
        }
      } else if (!onlyPrefix) {
        setWrong((prev) => ({ ...prev, [key]: true }))
      } else {
        clearWrong(key)
      }
    } catch (err) {
      setActionError(apiMessage(err))
    } finally {
      setChecking((prev) => {
        const next = { ...prev }
        delete next[key]
        return next
      })
    }
  }

  const handleChange = (columnIndex, position, text) => {
    if (timeUp || isSolved(columnIndex, position)) return
    const next = protectValue(text, prefixFor(columnIndex, position))
    setValues((prev) => prev.map((words, c) => (c === columnIndex
      ? words.map((word, p) => (p === position ? next : word))
      : words)))
    clearWrong(cellKey(columnIndex, position))
  }

  const handleInputKeyDown = (event, columnIndex, position) => {
    const prefix = prefixFor(columnIndex, position)
    if (prefix && (event.key === 'Backspace' || event.key === 'Delete')) {
      const start = event.currentTarget.selectionStart ?? 0
      const end = event.currentTarget.selectionEnd ?? 0
      const collapsed = start === end
      const crossesPrefix = collapsed
        ? (event.key === 'Backspace' ? start <= prefix.length : start < prefix.length)
        : start < prefix.length
      if (crossesPrefix) {
        event.preventDefault()
        event.currentTarget.setSelectionRange(prefix.length, prefix.length)
        return
      }
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      check(columnIndex, position)
    }
  }

  const guardCaret = (event, columnIndex, position) => {
    const prefix = prefixFor(columnIndex, position)
    if (!prefix) return
    const input = event.currentTarget
    const start = input.selectionStart
    if (start == null || start >= prefix.length) return
    const end = input.selectionEnd
    input.setSelectionRange(prefix.length, end != null && end > prefix.length ? end : prefix.length)
  }

  const getHint = async () => {
    if (!hintAvailable || takingHint) return
    setTakingHint(true)
    setActionError('')
    try {
      const fresh = await requestHint(game.id)
      const nextPrefixes = fresh?.hintPrefixes || {}
      const nextSolved = fresh?.solved || {}
      setSolved(nextSolved)
      setHintLevel(fresh?.hintLevel ?? Math.min(maxHintLevel, hintLevel + 1))
      setHintPrefixes(nextPrefixes)
      setValues((prev) => prev.map((words, c) => words.map((word, p) => {
        if (c === 0 || nextSolved[cellKey(c, p)]) return word
        return withPrefix(word, nextPrefixes[cellKey(c, p)] || '',
          hintPrefixes[cellKey(c, p)] || '')
      })))
      setWrong({})
      setHintNotice(false)
    } catch (err) {
      setActionError(apiMessage(err))
    } finally {
      setTakingHint(false)
    }
  }

  const renderCard = (columnIndex, position) => {
    const key = cellKey(columnIndex, position)
    const cardSolved = isSolved(columnIndex, position)
    const cardWrong = !cardSolved && Boolean(wrong[key])
    const hintOpen = isHintOpen(columnIndex, position)
    const prefix = prefixFor(columnIndex, position)
    const value = cardSolved ? wordAt(columnIndex, position) : values[columnIndex][position]
    const showPrefix = Boolean(prefix) && !cardSolved
    const cardDisabled = cardSolved || Boolean(checking[key]) || timeUp

    return (
      <div
        className={[
          'assoc-card',
          cardSolved ? 'is-solved' : '',
          cardWrong ? 'is-wrong' : '',
          checking[key] ? 'is-checking' : '',
          hintOpen ? 'hint-open' : 'hint-locked',
        ].join(' ').trim()}
      >
        <div className="assoc-card-hint">
          {hintOpen ? (
            <>
              <span className="assoc-hint-label">Подсказка</span>{' '}
              <strong>{wordAt(columnIndex - 1, position * 2)}</strong> +{' '}
              <strong>{wordAt(columnIndex - 1, position * 2 + 1)}</strong>
            </>
          ) : (
            'Подсказки откроются позже'
          )}
        </div>

        <div className="assoc-answer">
          <div className={`assoc-input-wrap ${showPrefix ? 'has-hint-prefix' : ''}`}>
            <input
              className="assoc-input"
              value={value}
              onChange={(event) => handleChange(columnIndex, position, event.target.value)}
              onBlur={() => check(columnIndex, position)}
              onKeyDown={(event) => handleInputKeyDown(event, columnIndex, position)}
              onKeyUp={(event) => guardCaret(event, columnIndex, position)}
              onClick={(event) => guardCaret(event, columnIndex, position)}
              onFocus={(event) => guardCaret(event, columnIndex, position)}
              onSelect={(event) => guardCaret(event, columnIndex, position)}
              disabled={cardDisabled}
              placeholder={checking[key] ? 'Проверяем…' : 'Ваш ответ'}
              aria-label={`Ответ ${position + 1} в колонке ${columnIndex + 1}`}
            />
            {showPrefix && (
              <span className="assoc-input-live" aria-hidden="true">
                <span className="assoc-hint-prefix">{value.slice(0, prefix.length)}</span>
                <span>{value.slice(prefix.length)}</span>
              </span>
            )}
          </div>
          {cardSolved && <span className="assoc-check" aria-label="Верно">✓</span>}
        </div>
      </div>
    )
  }

  const timerCritical = !timeUp && remainingSeconds > 0 && remainingSeconds <= 60

  return (
    <div className="assoc-board">
      <div className="assoc-board-head">
        <p className="assoc-board-tip">
          Уровень «{level.title}». Время: <strong>{formatTime(remainingSeconds)}</strong>{' '}
          Разгадайте слово в колонке 5 до конца таймера. Подсказки следующих колонок
          открываются, когда разгаданы оба слова пары. Ответы проверяет сервер.
        </p>

        <div className="assoc-board-tools">
          <div
            className={[
              'assoc-timer',
              timeUp ? 'is-timeup' : '',
              timerCritical ? 'is-critical' : '',
            ].join(' ').trim()}
            role="timer"
            aria-live={timerCritical ? 'polite' : 'off'}
          >
            <span className="assoc-timer-icon" aria-hidden="true">⏱</span>
            <span className="assoc-timer-value">{formatTime(remainingSeconds)}</span>
            <span className="assoc-timer-label">{timeUp ? 'время вышло' : 'осталось'}</span>
          </div>
          {hintScheduled && (
            hintAvailable ? (
              <button
                type="button"
                className="assoc-hint-btn"
                onClick={getHint}
                disabled={takingHint || timeUp}
              >
                <span aria-hidden="true">💡</span>{' '}
                {takingHint ? 'Открываем…' : 'Получить подсказку'}
              </button>
            ) : (
              <span className="assoc-hint-countdown">
                <span aria-hidden="true">💡</span> Подсказка через {formatTime(hintSecondsLeft)}
              </span>
            )
          )}
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

      {hintNotice && hintAvailable && (
        <div className="assoc-hint-notice" role="status">
          🔔 Доступна новая подсказка!
        </div>
      )}

      {actionError && <div className="alert alert-error">{actionError}</div>}

      <div className="assoc-scroll">
        <div className="assoc-pyramid-wrap">
          <div className="assoc-pyramid">
            {toPairs(game.startWords).map((pair, row) => (
              <Fragment key={`row-${row}`}>
                <div
                  className="assoc-cell"
                  data-column="1"
                  style={{ gridColumn: 1, gridRow: row + 1 }}
                >
                  <div className="assoc-pair">
                    <span className="assoc-pair-num">{row + 1}</span>
                    {pair.map((word, offset) => (
                      <span className="assoc-word" key={offset}>
                        {word}
                      </span>
                    ))}
                  </div>
                </div>

                <div
                  className="assoc-cell"
                  data-column="2"
                  style={{ gridColumn: 2, gridRow: row + 1 }}
                >
                  {renderCard(1, row)}
                </div>
              </Fragment>
            ))}

            {[2, 3, 4].map((columnIndex) => {
              const span = COLUMN_SPANS[columnIndex]
              return Array.from({ length: COLUMN_SIZES[columnIndex] }, (_, position) => (
                <div
                  key={`card-${columnIndex}-${position}`}
                  className="assoc-cell"
                  data-column={columnIndex + 1}
                  style={{
                    gridColumn: columnIndex + 1,
                    gridRow: `${position * span + 1} / span ${span}`,
                  }}
                >
                  {renderCard(columnIndex, position)}
                </div>
              ))
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
