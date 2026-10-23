import { useEffect, useState } from 'react'
import Modal from '../Modal'
import { COLUMN_HEADERS, COLUMN_SIZES, normalizeWord, toPairs } from './constants'

const LAST_COLUMN = COLUMN_SIZES.length - 1

const emptyValues = () => COLUMN_SIZES.map((size) => Array(size).fill(''))

/** Сохранённый прогресс игрока (localStorage): значения полей и разгаданные слова. */
function readProgress(storageKey) {
  const fallback = { values: emptyValues(), solved: {} }
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
    return { values, solved }
  } catch {
    // битые данные прогресса просто игнорируем
    return fallback
  }
}

/**
 * Игровое поле: 5 колонок (16/8/4/2/1).
 * Подсказка карточки i в колонке N+1 — пара слов (2i-1, 2i) из колонки N.
 * В колонке 2 подсказки видны сразу, дальше открываются, когда разгаданы оба слова пары.
 */
export default function GameBoard({ game }) {
  const storageKey = `assoc:progress:${game.id}`
  const [initial] = useState(() => readProgress(storageKey))
  const [values, setValues] = useState(initial.values)
  const [solved, setSolved] = useState(initial.solved)
  const [wrong, setWrong] = useState({})
  const [win, setWin] = useState(false)

  // прогресс сохраняется, чтобы обновление страницы не сбрасывало игру
  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify({ values, solved }))
    } catch {
      // переполнение localStorage не должно ломать игру
    }
  }, [storageKey, values, solved])

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

  const markSolved = (columnIndex, position, key) => {
    setSolved((prev) => (prev[key] ? prev : { ...prev, [key]: true }))
    clearWrong(key)
    // верный ответ в последней колонке — победа, независимо от остальных слов
    if (columnIndex === LAST_COLUMN) setWin(true)
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
    setValues(emptyValues())
    setSolved({})
    setWrong({})
    setWin(false)
    try {
      localStorage.removeItem(storageKey)
    } catch {
      // нечего очищать
    }
  }

  return (
    <div className="assoc-board">
      <div className="assoc-board-head">
        <p className="assoc-board-tip">
          Разгадайте слово в колонке 5 — это победа. Подсказки следующих колонок
          открываются, когда разгаданы оба слова пары.
        </p>
        <button type="button" className="btn btn-small btn-secondary" onClick={restart}>
          Начать заново
        </button>
      </div>

      <div className="assoc-scroll">
        <div className="assoc-columns assoc-columns--player">
          {COLUMN_SIZES.map((size, columnIndex) => (
            <section className="assoc-column" key={columnIndex}>
              <header className="assoc-column-header">
                <h3>{COLUMN_HEADERS[columnIndex].title}</h3>
                <span>{COLUMN_HEADERS[columnIndex].subtitle}</span>
              </header>

              {columnIndex === 0 ? (
                <div className="assoc-pairs">
                  {toPairs(game.columns[0]).map((pair, pairIndex) => (
                    <div className="assoc-pair" key={pairIndex}>
                      <span className="assoc-pair-num">{pairIndex + 1}</span>
                      {pair.map((word, offset) => (
                        <span className="assoc-word" key={offset}>{word}</span>
                      ))}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="assoc-cards">
                  {Array.from({ length: size }, (_, position) => {
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
                        key={position}
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
                  })}
                </div>
              )}
            </section>
          ))}
        </div>
      </div>

      {win && (
        <Modal title="Победа!" onClose={() => setWin(false)}>
          <div className="assoc-win">
            <div className="assoc-win-emoji">🏆</div>
            <p className="assoc-win-text">Поздравляем! Вы победитель игры «{game.title}»!</p>
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
