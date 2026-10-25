import { Fragment, useEffect, useRef, useState } from 'react'
import Modal from '../Modal'
import Leaderboard from './Leaderboard'
import PyramidHead from './PyramidHead'
import { apiMessage } from '../../api/client'
import { checkAnswer, requestHint, requestTimeout, restartGame } from '../../api/games'
import { COLUMN_SIZES, COLUMN_SPANS, formatMinutes, formatTime, toPairs } from './constants'

const LAST_COLUMN = COLUMN_SIZES.length - 1
/** Подсказки открываются по серверному таймеру: 3, 6, 9, 12 минут после старта. */
const HINT_ONE_SECONDS = 180
const HINT_TWO_SECONDS = 360
const HINT_THREE_SECONDS = 540
const HINT_FOUR_SECONDS = 720
const MAX_HINT_LEVEL = 4

const emptyValues = () => COLUMN_SIZES.map((size) => Array(size).fill(''))

/** Ключ ячейки на сервере: колонка и позиция нумеруются с 1. */
const cellKey = (columnIndex, position) => `${columnIndex + 1}:${position + 1}`

/**
 * Защита букв подсказки: префикс всегда остаётся в начале значения.
 * Если игрок пытается стереть подсказанные буквы, возвращаем их обратно.
 */
const protectValue = (text, prefix) => {
  if (!prefix) return text
  const value = text || ''
  const lowerValue = value.toLowerCase()
  const lowerPrefix = prefix.toLowerCase()
  if (lowerValue.startsWith(lowerPrefix)) {
    return prefix + value.slice(prefix.length)
  }
  // текст стал короче подсказки или это её часть — восстанавливаем префикс
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
 * Игровое поле-пирамида.
 *
 * Раскладка: CSS Grid из 8 строк (по числу пар слов в первой колонке).
 * - Колонка 1: 8 пар, каждая занимает одну строку.
 * - Колонка 2: 8 ответов, каждый стоит на одной строке со своей парой.
 * - Колонки 3-5: ответ занимает 2, 4 и 8 строк и центрируется по двум
 *   подсказкам предыдущей колонки (2^(N-1) строк).
 *
 * Ответы колонок 2-5 на клиент не приходят: каждое слово проверяется
 * запросом POST /api/games/{id}/check. В прогрессе (progress) лежат только
 * слова, которые угадал текущий пользователь; чужие ответы недоступны.
 */
export default function GameBoard({
  game, progress, onWin, leaderboard, leaderboardLoading, currentUserId, resultError,
}) {
  const [values, setValues] = useState(() => {
    const cells = emptyValues()
    COLUMN_SIZES.forEach((size, columnIndex) => {
      if (columnIndex === 0) return
      for (let position = 0; position < size; position++) {
        const key = cellKey(columnIndex, position)
        const word = progress?.solved?.[key]
        // сохранённые подсказки возвращаются в поля после перезагрузки
        cells[columnIndex][position] = word || progress?.hintPrefixes?.[key] || ''
      }
    })
    return cells
  })
  // разгаданные ячейки этого пользователя: { "2:1": "слон" }
  const [solved, setSolved] = useState(() => progress?.solved || {})
  // уровень подсказок (0..2) и открытые буквы неразгаданных ячеек
  const [hintLevel, setHintLevel] = useState(progress?.hintLevel || 0)
  const [hintPrefixes, setHintPrefixes] = useState(() => progress?.hintPrefixes || {})
  const [takingHint, setTakingHint] = useState(false)
  const [hintNotice, setHintNotice] = useState(false)
  const hintWasAvailable = useRef(false)
  // отсчёт таймера: базовое время из прогресса + время с момента загрузки
  const [clock, setClock] = useState(() => ({
    baseElapsed: progress?.elapsedSeconds || 0,
    loadedAt: Date.now(),
  }))
  const [finishedTime, setFinishedTime] = useState(progress?.finishedSeconds ?? null)
  const [elapsed, setElapsed] = useState(progress?.finishedSeconds ?? progress?.elapsedSeconds ?? 0)
  const [wrong, setWrong] = useState({})
  const [checking, setChecking] = useState({})
  const [win, setWin] = useState(false)
  // время вышло: игра остановлена, поля заблокированы, результат не отправляется
  const [timeUp, setTimeUp] = useState(false)
  const [showTimeout, setShowTimeout] = useState(false)
  const [timeoutAnswer, setTimeoutAnswer] = useState('')
  const [timeoutError, setTimeoutError] = useState('')
  const [actionError, setActionError] = useState('')
  const [restarting, setRestarting] = useState(false)
  // защита от двойной отправки результата при победе
  const finishedRef = useRef(progress?.finishedSeconds != null)

  // лимит времени задаёт администратор; сервер прислал его в минутах
  const limitSeconds = Math.max(1, Math.round((Number(game.timeLimitMinutes) || 10) * 60))
  const remainingSeconds = Math.max(0, limitSeconds - elapsed)

  const elapsedSeconds = () =>
    clock.baseElapsed + Math.max(0, Math.floor((Date.now() - clock.loadedAt) / 1000))

  // тикающий таймер; после победы или истечения времени он замирает
  useEffect(() => {
    if (finishedTime != null || timeUp) {
      if (finishedTime != null) setElapsed(finishedTime)
      return undefined
    }
    const tick = () => setElapsed(elapsedSeconds())
    tick()
    const timer = setInterval(tick, 250)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clock, finishedTime, timeUp])

  const isSolved = (columnIndex, position) =>
    columnIndex === 0 || Boolean(solved[cellKey(columnIndex, position)])

  /** Префикс, открытый подсказкой; разгаданным ячейкам подсказки не нужны. */
  const prefixFor = (columnIndex, position) => {
    if (columnIndex === 0 || isSolved(columnIndex, position)) return ''
    return hintPrefixes[cellKey(columnIndex, position)] || ''
  }

  // ближайшая подсказка: первая на 180-й секунде, вторая — на 360-й, третья — на 540-й, четвёртая — на 720-й
  const nextHintAt = hintLevel >= MAX_HINT_LEVEL
    ? null
    : (hintLevel === 0
      ? HINT_ONE_SECONDS
      : hintLevel === 1
        ? HINT_TWO_SECONDS
        : hintLevel === 2
          ? HINT_THREE_SECONDS
          : HINT_FOUR_SECONDS)
  const hintAvailable = !timeUp && finishedTime == null && nextHintAt != null
    && nextHintAt < limitSeconds && elapsed >= nextHintAt
  const hintSecondsLeft = nextHintAt == null ? 0 : Math.max(0, nextHintAt - elapsed)
  // подсказка, которая не успеет появиться до конца игры, не показывается вовсе
  const hintScheduled = !timeUp && finishedTime == null && nextHintAt != null
    && nextHintAt < limitSeconds

  // уведомление в момент, когда подсказка становится доступна
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

  // лимит времени истёк: останавливаем игру и не отправляем результат
  useEffect(() => {
    if (finishedTime != null || timeUp || elapsed < limitSeconds) return
    setTimeUp(true)
    setShowTimeout(true)
    setWin(false)
    setActionError('')
    setWrong({})
    setHintNotice(false)
  }, [elapsed, finishedTime, limitSeconds, timeUp])

  // после таймаута сервер раскрывает правильный ответ колонки 5 (без записи рекорда)
  useEffect(() => {
    if (!timeUp || timeoutAnswer) return undefined
    let cancelled = false
    let attempts = 0
    let retryTimer
    const load = async () => {
      attempts += 1
      try {
        const result = await requestTimeout(game.id)
        if (cancelled) return
        setTimeoutAnswer(result?.answer || '')
        setTimeoutError('')
        if (result?.answer) {
          setValues((prev) => prev.map((words, c) => (c === LAST_COLUMN
            ? words.map((word, p) => (p === 0 ? result.answer : word))
            : words)))
        }
      } catch (err) {
        if (cancelled) return
        if (attempts < 4) {
          retryTimer = setTimeout(load, 1200)
        } else {
          setTimeoutError(apiMessage(err))
        }
      }
    }
    load()
    return () => {
      cancelled = true
      clearTimeout(retryTimer)
    }
  }, [game.id, timeoutAnswer, timeUp])

  /** Слово, которое известно клиенту: стартовое (колонка 1) или отгаданное. */
  const wordAt = (columnIndex, position) => (columnIndex === 0
    ? game.startWords[position] || ''
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

  const finish = (serverSeconds) => {
    if (finishedRef.current || timeUp) return
    finishedRef.current = true
    const seconds = Number.isFinite(serverSeconds) && serverSeconds > 0
      ? serverSeconds
      : Math.max(1, elapsedSeconds())
    setFinishedTime(seconds)
    setElapsed(seconds)
    setWin(true)
    setActionError('')
    if (onWin) Promise.resolve(onWin(seconds)).catch(() => {})
  }

  /** Проверка ответа на сервере: правильное слово клиенту неизвестно. */
  const check = async (columnIndex, position) => {
    const key = cellKey(columnIndex, position)
    if (timeUp || isSolved(columnIndex, position) || checking[key]) return
    const text = (values[columnIndex][position] || '').trim()
    if (!text) {
      clearWrong(key)
      return
    }
    const prefix = prefixFor(columnIndex, position)
    // поле содержит только подсказку — это ещё не ответ, красным не подсвечиваем
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
        // показываем слово в том виде, в каком оно сохранено в прогрессе
        setValues((prev) => prev.map((words, c) => (c === columnIndex
          ? words.map((word, p) => (p === position ? (nextSolved[key] || text) : word))
          : words)))
        if (columnIndex === LAST_COLUMN) finish(result.progress?.finishedSeconds)
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

  /** Не даём стереть подсказку и увести курсор перед ней. */
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

  /** Взять следующую подсказку: сервер сам проверяет, прошло ли нужное время. */
  const getHint = async () => {
    if (!hintAvailable || takingHint) return
    setTakingHint(true)
    setActionError('')
    try {
      const fresh = await requestHint(game.id)
      const nextPrefixes = fresh?.hintPrefixes || {}
      const nextSolved = fresh?.solved || {}
      setSolved(nextSolved)
      setHintLevel(fresh?.hintLevel ?? Math.min(MAX_HINT_LEVEL, hintLevel + 1))
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

  /** Начать заново: сервер обнуляет прогресс только этого пользователя. */
  const restart = async () => {
    setRestarting(true)
    setActionError('')
    try {
      const fresh = await restartGame(game.id)
      finishedRef.current = false
      setSolved(fresh.solved || {})
      setValues(emptyValues())
      setWrong({})
      setWin(false)
      setFinishedTime(null)
      setTimeUp(false)
      setShowTimeout(false)
      setTimeoutAnswer('')
      setTimeoutError('')
      setClock({ baseElapsed: 0, loadedAt: Date.now() })
      setElapsed(0)
      setHintLevel(fresh.hintLevel || 0)
      setHintPrefixes(fresh.hintPrefixes || {})
      setHintNotice(false)
      hintWasAvailable.current = false
      setTakingHint(false)
    } catch (err) {
      setActionError(apiMessage(err))
    } finally {
      setRestarting(false)
    }
  }

  /** Карточка ответа: подсказка пары + поле ввода. */
  const renderCard = (columnIndex, position) => {
    const key = cellKey(columnIndex, position)
    const cardSolved = isSolved(columnIndex, position)
    const cardWrong = !cardSolved && Boolean(wrong[key])
    const hintOpen = isHintOpen(columnIndex, position)
    const prefix = prefixFor(columnIndex, position)
    const value = cardSolved ? wordAt(columnIndex, position) : values[columnIndex][position]
    const showPrefix = Boolean(prefix) && !cardSolved
    const expiredAnswer = timeUp && columnIndex === LAST_COLUMN

    return (
      <div
        className={[
          'assoc-card',
          cardSolved ? 'is-solved' : '',
          cardWrong ? 'is-wrong' : '',
          expiredAnswer ? 'is-timeup-answer' : '',
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
              disabled={cardSolved || Boolean(checking[key]) || timeUp}
              placeholder={checking[key] ? 'Проверяем…' : (expiredAnswer ? 'Время вышло' : 'Ваш ответ')}
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

  const timerValue = remainingSeconds
  const timerCritical = finishedTime == null && !timeUp
    && remainingSeconds > 0 && remainingSeconds <= 60

  return (
    <div className="assoc-board">
      <div className="assoc-board-head">
        <p className="assoc-board-tip">
          Время на игру: <strong>{formatMinutes(game.timeLimitMinutes)} мин.</strong>{' '}
          Разгадайте слово в колонке 5 до конца таймера — это победа. Подсказки следующих
          колонок открываются, когда разгаданы оба слова пары. Ответы проверяет сервер.
        </p>

        <div className="assoc-board-tools">
          <div
            className={[
              'assoc-timer',
              finishedTime != null ? 'is-finished' : '',
              timeUp ? 'is-timeup' : '',
              timerCritical ? 'is-critical' : '',
            ].join(' ').trim()}
            role="timer"
            aria-live={timerCritical ? 'polite' : 'off'}
          >
            <span className="assoc-timer-icon" aria-hidden="true">⏱</span>
            <span className="assoc-timer-value">{formatTime(timerValue)}</span>
            <span className="assoc-timer-label">
              {finishedTime != null ? 'финиш' : (timeUp ? 'время вышло' : 'осталось')}
            </span>
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
          <button
            type="button"
            className="btn btn-small btn-secondary"
            onClick={restart}
            disabled={restarting}
          >
            {restarting ? 'Сброс…' : 'Начать заново'}
          </button>
        </div>
      </div>

      {hintNotice && hintAvailable && (
        <div className="assoc-hint-notice" role="status">
          🔔 Доступна новая подсказка!
        </div>
      )}

      {actionError && <div className="alert alert-error">{actionError}</div>}

      <div className="assoc-scroll">
        <div className="assoc-pyramid-wrap">
          <PyramidHead />

          <div className="assoc-pyramid">
            {/* Колонка 1: пара слов и её ответ из колонки 2 — на одной строке */}
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
                      <span className="assoc-word" key={offset}>{word}</span>
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

            {/* Колонки 3-5: карточка занимает 2/4/8 строк и стоит по центру своих подсказок */}
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

      {win && (
        <Modal title="Победа!" onClose={() => setWin(false)}>
          <div className="assoc-win">
            <div className="assoc-win-emoji">🏆</div>
            <p className="assoc-win-text">
              Поздравляем! Вы победитель! Ваше время: <strong>{finishedTime} сек.</strong>
            </p>
            {resultError && <p className="assoc-win-error">{resultError}</p>}

            <Leaderboard
              entries={leaderboard || []}
              loading={leaderboardLoading}
              currentUserId={currentUserId}
              limit={5}
              title="Лучшие результаты"
            />

            <div className="form-actions">
              <button type="button" className="btn btn-primary" onClick={restart} disabled={restarting}>
                Начать заново
              </button>
            </div>
          </div>
        </Modal>
      )}

      {timeUp && showTimeout && (
        <Modal title="Время вышло!" onClose={() => setShowTimeout(false)}>
          <div className="assoc-timeup">
            <div className="assoc-win-emoji" aria-hidden="true">⏳</div>
            <p className="assoc-win-text">
              Время вышло! Вы не успели угадать загаданное слово. Игра окончена.
            </p>
            <p className="assoc-timeup-text">
              Результат не засчитан в рейтинге.
            </p>
            {timeoutAnswer ? (
              <p className="assoc-timeup-answer">
                Правильный ответ в колонке 5: <strong>{timeoutAnswer}</strong>
              </p>
            ) : (
              <p className="assoc-timeup-text">
                {timeoutError || 'Загружаем правильный ответ…'}
              </p>
            )}
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setShowTimeout(false)}>
                Закрыть
              </button>
              <button type="button" className="btn btn-primary" onClick={restart} disabled={restarting}>
                Начать заново
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
