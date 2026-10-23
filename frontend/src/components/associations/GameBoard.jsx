import { Fragment, useEffect, useRef, useState } from 'react'
import Modal from '../Modal'
import Leaderboard from './Leaderboard'
import PyramidHead from './PyramidHead'
import {
  COLUMN_SIZES,
  COLUMN_SPANS,
  formatTime,
  normalizeWord,
  toPairs,
} from './constants'

const LAST_COLUMN = COLUMN_SIZES.length - 1

const emptyValues = () => COLUMN_SIZES.map((size) => Array(size).fill(''))

/** Сохранённый прогресс игрока: значения полей, разгаданные слова, старт и время победы. */
function readProgress(storageKey) {
  const fallback = { values: emptyValues(), solved: {}, startedAt: null, time: null }
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return fallback
    const saved = JSON.parse(raw)
    const values = COLUMN_SIZES.map((size, columnIndex) =>
      Array.from({ length: size }, (_, position) => {
        const value = saved?.values?.[columnIndex]?.[position]
        return typeof value === 'string' ? value : ''
      }))
    const solved = saved?.solved && typeof saved.solved === 'object' ? saved.solved : {}
    const startedAt = Number(saved?.startedAt) > 0 ? Number(saved.startedAt) : null
    const time = Number(saved?.time) > 0 ? Number(saved.time) : null
    return { values, solved, startedAt, time }
  } catch {
    // битые данные прогресса просто игнорируем
    return fallback
  }
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
 * Таймер стартует при открытии игры (момент сохраняется в localStorage, чтобы
 * перезагрузка страницы не обнуляла время), останавливается на верном слове
 * колонки 5 и передаётся наверх для сохранения результата.
 */
export default function GameBoard({
  game, onWin, leaderboard, leaderboardLoading, currentUserId, resultError,
}) {
  const storageKey = `assoc:progress:${game.id}`
  const [initial] = useState(() => readProgress(storageKey))
  const [values, setValues] = useState(initial.values)
  const [solved, setSolved] = useState(initial.solved)
  const [startedAt, setStartedAt] = useState(() => initial.startedAt || Date.now())
  const [finishedTime, setFinishedTime] = useState(initial.time)
  const [elapsed, setElapsed] = useState(initial.time ?? 0)
  const [wrong, setWrong] = useState({})
  const [win, setWin] = useState(false)
  // защита от двойной отправки результата (change + blur на одном ответе)
  const finishedRef = useRef(initial.time != null)

  // прогресс и время сохраняются, чтобы обновление страницы не сбрасывало игру
  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify({
        values,
        solved,
        startedAt,
        time: finishedTime,
      }))
    } catch {
      // переполнение localStorage не должно ломать игру
    }
  }, [storageKey, values, solved, startedAt, finishedTime])

  // тикающий таймер; после победы он замирает на сохранённом времени
  useEffect(() => {
    if (finishedTime != null) {
      setElapsed(finishedTime)
      return undefined
    }
    const tick = () => setElapsed(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)))
    tick()
    const timer = setInterval(tick, 250)
    return () => clearInterval(timer)
  }, [startedAt, finishedTime])

  // слова первой колонки даны изначально, поэтому считаем их разгаданными
  const isSolved = (columnIndex, position) =>
    columnIndex === 0 || Boolean(solved[`${columnIndex}:${position}`])

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

  /** Победа: останавливаем таймер и отдаём время наверх для сохранения. */
  const finishGame = () => {
    if (finishedRef.current) return
    finishedRef.current = true
    const seconds = Math.max(1, Math.round((Date.now() - startedAt) / 1000))
    setFinishedTime(seconds)
    setElapsed(seconds)
    setWin(true)
    if (onWin) {
      Promise.resolve(onWin(seconds)).catch(() => {})
    }
  }

  const markSolved = (columnIndex, position, key) => {
    setSolved((prev) => (prev[key] ? prev : { ...prev, [key]: true }))
    clearWrong(key)
    // верный ответ в последней колонке — победа, независимо от остальных слов
    if (columnIndex === LAST_COLUMN) finishGame()
  }

  const handleChange = (columnIndex, position, text) => {
    const key = `${columnIndex}:${position}`
    setValues((prev) =>
      prev.map((words, c) =>
        c === columnIndex ? words.map((word, p) => (p === position ? text : word)) : words))
    if (isSolved(columnIndex, position)) return

    // при новом вводе подсветка ошибки сбрасывается
    clearWrong(key)
    if (normalizeWord(text) === normalizeWord(game.columns[columnIndex][position])) {
      markSolved(columnIndex, position, key)
    }
  }

  /** Проверка по Enter/уходу из поля: неверный ответ подсвечиваем красным. */
  const handleCommit = (columnIndex, position) => {
    const key = `${columnIndex}:${position}`
    if (isSolved(columnIndex, position)) return
    const value = values[columnIndex][position] || ''
    if (!value.trim()) {
      clearWrong(key)
      return
    }
    if (normalizeWord(value) === normalizeWord(game.columns[columnIndex][position])) {
      markSolved(columnIndex, position, key)
    } else {
      setWrong((prev) => ({ ...prev, [key]: true }))
    }
  }

  const restart = () => {
    finishedRef.current = false
    setValues(emptyValues())
    setSolved({})
    setWrong({})
    setWin(false)
    setFinishedTime(null)
    setElapsed(0)
    setStartedAt(Date.now())
    try {
      localStorage.removeItem(storageKey)
    } catch {
      // нечего очищать
    }
  }

  /** Карточка ответа: подсказка пары + поле ввода. */
  const renderCard = (columnIndex, position) => {
    const key = `${columnIndex}:${position}`
    const cardSolved = isSolved(columnIndex, position)
    const cardWrong = !cardSolved && Boolean(wrong[key])
    const hintOpen = isHintOpen(columnIndex, position)
    const hintWords = hintOpen
      ? [
        game.columns[columnIndex - 1][position * 2],
        game.columns[columnIndex - 1][position * 2 + 1],
      ]
      : null

    return (
      <div
        className={[
          'assoc-card',
          cardSolved ? 'is-solved' : '',
          cardWrong ? 'is-wrong' : '',
          hintOpen ? 'hint-open' : 'hint-locked',
        ].join(' ').trim()}
      >
        <div className="assoc-card-hint">
          {hintOpen ? (
            <>
              <span className="assoc-hint-label">Подсказка</span>{' '}
              <strong>{hintWords[0]}</strong> + <strong>{hintWords[1]}</strong>
            </>
          ) : (
            'Подсказки откроются позже'
          )}
        </div>

        <div className="assoc-answer">
          <input
            className="assoc-input"
            value={values[columnIndex][position]}
            onChange={(event) => handleChange(columnIndex, position, event.target.value)}
            onBlur={() => handleCommit(columnIndex, position)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                handleCommit(columnIndex, position)
              }
            }}
            disabled={cardSolved}
            placeholder="Ваш ответ"
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
          открываются, когда разгаданы оба слова пары.
        </p>

        <div className="assoc-board-tools">
          <div className={`assoc-timer ${finishedTime != null ? 'is-finished' : ''}`}>
            <span className="assoc-timer-icon" aria-hidden="true">⏱</span>
            <span className="assoc-timer-value">{formatTime(timerValue)}</span>
            <span className="assoc-timer-label">{finishedTime != null ? 'финиш' : 'время'}</span>
          </div>
          <button type="button" className="btn btn-small btn-secondary" onClick={restart}>
            Начать заново
          </button>
        </div>
      </div>

      <div className="assoc-scroll">
        <div className="assoc-pyramid-wrap">
          <PyramidHead />

          <div className="assoc-pyramid">
            {/* Колонка 1: пара слов и её ответ из колонки 2 — на одной строке */}
            {toPairs(game.columns[0]).map((pair, row) => (
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
              <button type="button" className="btn btn-primary" onClick={restart}>
                Начать заново
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
