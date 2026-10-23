import { Fragment, useEffect, useRef, useState } from 'react'
import Modal from '../Modal'
import Leaderboard from './Leaderboard'
import PyramidHead from './PyramidHead'
import { apiMessage } from '../../api/client'
import { checkAnswer, restartGame } from '../../api/games'
import { COLUMN_SIZES, COLUMN_SPANS, formatTime, toPairs } from './constants'

const LAST_COLUMN = COLUMN_SIZES.length - 1

const emptyValues = () => COLUMN_SIZES.map((size) => Array(size).fill(''))

/** Ключ ячейки на сервере: колонка и позиция нумеруются с 1. */
const cellKey = (columnIndex, position) => `${columnIndex + 1}:${position + 1}`

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
        const word = progress?.solved?.[cellKey(columnIndex, position)]
        if (word) cells[columnIndex][position] = word
      }
    })
    return cells
  })
  // разгаданные ячейки этого пользователя: { "2:1": "слон" }
  const [solved, setSolved] = useState(() => progress?.solved || {})
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
  const [actionError, setActionError] = useState('')
  const [restarting, setRestarting] = useState(false)
  // защита от двойной отправки результата при победе
  const finishedRef = useRef(progress?.finishedSeconds != null)

  const elapsedSeconds = () =>
    clock.baseElapsed + Math.max(0, Math.floor((Date.now() - clock.loadedAt) / 1000))

  // тикающий таймер; после победы он замирает на серверном времени
  useEffect(() => {
    if (finishedTime != null) {
      setElapsed(finishedTime)
      return undefined
    }
    const tick = () => setElapsed(elapsedSeconds())
    tick()
    const timer = setInterval(tick, 250)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clock, finishedTime])

  const isSolved = (columnIndex, position) =>
    columnIndex === 0 || Boolean(solved[cellKey(columnIndex, position)])

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
    if (finishedRef.current) return
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
    if (isSolved(columnIndex, position) || checking[key]) return
    const text = (values[columnIndex][position] || '').trim()
    if (!text) {
      clearWrong(key)
      return
    }

    setChecking((prev) => ({ ...prev, [key]: true }))
    setActionError('')
    try {
      const result = await checkAnswer(game.id, columnIndex + 1, position + 1, text)
      const nextSolved = result.progress?.solved || {}
      setSolved(nextSolved)
      if (result.correct) {
        clearWrong(key)
        // показываем слово в том виде, в каком оно сохранено в прогрессе
        setValues((prev) => prev.map((words, c) => (c === columnIndex
          ? words.map((word, p) => (p === position ? (nextSolved[key] || text) : word))
          : words)))
        if (columnIndex === LAST_COLUMN) finish(result.progress?.finishedSeconds)
      } else {
        setWrong((prev) => ({ ...prev, [key]: true }))
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
    setValues((prev) => prev.map((words, c) => (c === columnIndex
      ? words.map((word, p) => (p === position ? text : word))
      : words)))
    if (!isSolved(columnIndex, position)) clearWrong(cellKey(columnIndex, position))
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
      setClock({ baseElapsed: 0, loadedAt: Date.now() })
      setElapsed(0)
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
          <input
            className="assoc-input"
            value={cardSolved ? wordAt(columnIndex, position) : values[columnIndex][position]}
            onChange={(event) => handleChange(columnIndex, position, event.target.value)}
            onBlur={() => check(columnIndex, position)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                check(columnIndex, position)
              }
            }}
            disabled={cardSolved || Boolean(checking[key])}
            placeholder={checking[key] ? 'Проверяем…' : 'Ваш ответ'}
            aria-label={`Ответ ${position + 1} в колонке ${columnIndex + 1}`}
          />
          {cardSolved && <span className="assoc-check" aria-label="Верно">✓</span>}
        </div>
      </div>
    )
  }

  const timerValue = finishedTime ?? elapsed

  return (
    <div className="assoc-board">
      <div className="assoc-board-head">
        <p className="assoc-board-tip">
          Разгадайте слово в колонке 5 — это победа. Подсказки следующих колонок
          открываются, когда разгаданы оба слова пары. Ответы проверяет сервер.
        </p>

        <div className="assoc-board-tools">
          <div className={`assoc-timer ${finishedTime != null ? 'is-finished' : ''}`}>
            <span className="assoc-timer-icon" aria-hidden="true">⏱</span>
            <span className="assoc-timer-value">{formatTime(timerValue)}</span>
            <span className="assoc-timer-label">{finishedTime != null ? 'финиш' : 'время'}</span>
          </div>
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
    </div>
  )
}
