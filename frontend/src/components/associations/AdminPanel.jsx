import { Fragment, useState } from 'react'
import { apiMessage } from '../../api/client'
import { activateGame, createGame, deleteGame, fetchGameForEdit, updateGame } from '../../api/games'
import ConfirmDialog from '../ConfirmDialog'
import PyramidHead from './PyramidHead'
import {
  COLUMN_SIZES,
  COLUMN_SPANS,
  cloneColumns,
  emptyColumns,
  formatMinutes,
  hintLabel,
  toPairs,
} from './constants'

/**
 * Режим администратора: форма из 5 колонок (16/8/4/2/1) + список сохранённых игр.
 * Форма выложена той же сеткой, что и игровое поле: ответ стоит напротив своей
 * пары подсказок, а карточки верхних колонок центрируются по двум нижним.
 */
export default function AdminPanel({ games, onReload, onPlay, currentUserId }) {
  const [title, setTitle] = useState('')
  const [timeLimit, setTimeLimit] = useState('10')
  const [columns, setColumns] = useState(emptyColumns)
  const [editingId, setEditingId] = useState(null)
  const [showErrors, setShowErrors] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const [loadingGame, setLoadingGame] = useState(false)

  const setWord = (columnIndex, position, value) => {
    setColumns((prev) =>
      prev.map((words, c) =>
        c === columnIndex ? words.map((word, p) => (p === position ? value : word)) : words))
  }

  const resetForm = () => {
    setTitle('')
    setTimeLimit('10')
    setColumns(emptyColumns())
    setEditingId(null)
    setShowErrors(false)
    setError('')
  }

  const applyGame = (game) => {
    setTitle(game.title)
    setTimeLimit(formatMinutes(game.timeLimitMinutes ?? 10))
    setColumns(cloneColumns(game.columns))
    setEditingId(game.id)
    setShowErrors(false)
    setError('')
  }

  /** Своя игра — только у неё есть «Редактировать» и «Удалить». */
  const isOwn = (game) => game.creatorId != null && game.creatorId === currentUserId

  const startEdit = async (game) => {
    if (!isOwn(game)) return
    setLoadingGame(true)
    setError('')
    setMessage('')
    try {
      // полные слова отдаёт только /edit и только автору игры
      applyGame(await fetchGameForEdit(game.id))
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoadingGame(false)
    }
  }

  const emptyCount = columns.flat().filter((word) => !word.trim()).length
  const isCellInvalid = (columnIndex, position) =>
    showErrors && !columns[columnIndex][position].trim()

  const timeLimitValue = Number(timeLimit)
  const timeLimitInvalid = !timeLimit.trim() || !Number.isFinite(timeLimitValue)
    || timeLimitValue <= 0 || timeLimitValue > 24 * 60

  const save = async (event) => {
    event.preventDefault()
    setError('')
    setMessage('')

    if (!title.trim() || emptyCount > 0 || timeLimitInvalid) {
      setShowErrors(true)
      if (!title.trim()) {
        setError('Укажите название игры')
      } else if (emptyCount > 0) {
        setError(`Заполните все 31 слово — пустых полей: ${emptyCount}`)
      } else {
        setError('Укажите время на прохождение в минутах (положительное число, например 2.5)')
      }
      return
    }

    setSaving(true)
    try {
      const payload = {
        title: title.trim(),
        timeLimitMinutes: timeLimitValue,
        columns: columns.map((words) => words.map((word) => word.trim())),
      }
      const saved = editingId
        ? await updateGame(editingId, payload)
        : await createGame(payload)
      applyGame(saved)
      setMessage('Игра сохранена')
      await onReload()
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const confirmDelete = async () => {
    await deleteGame(deleting.id)
    if (editingId === deleting.id) resetForm()
    setDeleting(null)
    setMessage('Игра удалена')
    await onReload()
  }

  const playGame = async (game) => {
    setError('')
    try {
      // своя игра становится игрой по умолчанию, чужая — просто открывается
      if (isOwn(game)) {
        await activateGame(game.id)
        await onReload()
      }
    } catch {
      // активация не критична: игру всё равно можно открыть
    }
    onPlay(game.id)
  }

  /** Поле ответа с подписью, какая пара слов предыдущей колонки к нему относится. */
  const renderEditor = (columnIndex, position) => (
    <div className={`assoc-edit-card ${isCellInvalid(columnIndex, position) ? 'invalid' : ''}`}>
      <div className="assoc-edit-card-head">
        <span className="assoc-edit-label">Ответ {position + 1}</span>
        <span className="assoc-hint">{hintLabel(columnIndex, position)}</span>
      </div>
      <input
        value={columns[columnIndex][position]}
        onChange={(event) => setWord(columnIndex, position, event.target.value)}
        className={`assoc-input ${isCellInvalid(columnIndex, position) ? 'input-invalid' : ''}`}
        aria-label={`Ответ ${position + 1} в колонке ${columnIndex + 1}`}
        placeholder="Введите слово"
      />
    </div>
  )

  return (
    <div className="assoc-admin">
      <form className="assoc-form" onSubmit={save} noValidate>
        <div className="assoc-panel">
          <div className="field-row">
            <label className="field">
              <span>Название игры</span>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="Например, Животный мир"
                className={showErrors && !title.trim() ? 'input-invalid' : ''}
              />
            </label>
            <label className="field">
              <span>Время на прохождение (в минутах)</span>
              <input
                type="number"
                min="0.1"
                max="1440"
                step="any"
                inputMode="decimal"
                value={timeLimit}
                onChange={(event) => setTimeLimit(event.target.value)}
                placeholder="Например, 10 или 2.5"
                className={showErrors && timeLimitInvalid ? 'input-invalid' : ''}
              />
            </label>
          </div>
          <p className="hint">
            Заполните 31 слово: колонка 1 — 16 стартовых слов, дальше 8 + 4 + 2 + 1 ответ.
            Ответ в колонке N+1 загадывается по паре слов (2i−1, 2i) из колонки N.
            Время на прохождение — положительное число минут, можно дробное (например, 2.5).
          </p>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert alert-success">{message}</div>}

        <div className="assoc-scroll">
          <div className="assoc-pyramid-wrap">
            <PyramidHead />

            <div className="assoc-pyramid assoc-pyramid--admin">
              {/* Колонка 1: пара слов и её ответ из колонки 2 — на одной строке */}
              {toPairs(columns[0]).map((pair, row) => (
                <Fragment key={`row-${row}`}>
                  <div
                    className="assoc-cell"
                    data-column="1"
                    style={{ gridColumn: 1, gridRow: row + 1 }}
                  >
                    <div className="assoc-pair">
                      <span className="assoc-pair-num">Пара {row + 1}</span>
                      {pair.map((word, offset) => {
                        const position = row * 2 + offset
                        return (
                          <label className="assoc-word-input" key={position}>
                            <span>Слово {position + 1}</span>
                            <input
                              value={columns[0][position]}
                              onChange={(event) => setWord(0, position, event.target.value)}
                              className={`assoc-input ${isCellInvalid(0, position) ? 'input-invalid' : ''}`}
                              aria-label={`Слово ${position + 1}`}
                              placeholder={`Слово ${position + 1}`}
                            />
                          </label>
                        )
                      })}
                    </div>
                  </div>

                  <div
                    className="assoc-cell"
                    data-column="2"
                    style={{ gridColumn: 2, gridRow: row + 1 }}
                  >
                    {renderEditor(1, row)}
                  </div>
                </Fragment>
              ))}

              {/* Колонки 3-5: карточка занимает 2/4/8 строк и центрируется по своим подсказкам */}
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
                    {renderEditor(columnIndex, position)}
                  </div>
                ))
              })}
            </div>
          </div>
        </div>

        <div className="assoc-form-actions">
          <button type="button" className="btn btn-secondary" onClick={resetForm} disabled={saving}>
            Сбросить игру
          </button>
          <span className="spacer" />
          <span className="assoc-counter">{31 - emptyCount}/31 слов</span>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Сохранение…' : 'Сохранить игру'}
          </button>
        </div>
      </form>

      <section className="assoc-saved">
        <h2>Игры на платформе</h2>
        <p className="hint assoc-saved-hint">
          Видны все игры. Редактировать и удалять можно только свои — для чужих игр
          доступна кнопка «Играть».
        </p>
        {games.length === 0 ? (
          <div className="empty-state">
            Пока нет ни одной игры. Заполните форму выше и нажмите «Сохранить игру».
          </div>
        ) : (
          <ul className="assoc-games">
            {games.map((game) => (
              <li key={game.id} className={`assoc-game ${game.active ? 'active' : ''}`}>
                <div className="assoc-game-info">
                  <span className="assoc-game-title">
                    {game.title}
                    {isOwn(game) && <span className="assoc-badge own">моя игра</span>}
                  </span>
                  <span className="assoc-game-meta">
                    <span className="assoc-game-author">автор: {game.creatorUsername || 'неизвестен'}</span>
                    <span className="assoc-game-limit">лимит: {formatMinutes(game.timeLimitMinutes)} мин.</span>
                    <span className="assoc-game-date">{new Date(game.createdAt).toLocaleString('ru-RU')}</span>
                    {game.active && <span className="assoc-badge">по умолчанию</span>}
                    {editingId === game.id && <span className="assoc-badge editing">редактируется</span>}
                  </span>
                </div>
                <div className="assoc-game-actions">
                  <button
                    type="button"
                    className="btn btn-small btn-primary"
                    onClick={() => playGame(game)}
                  >
                    Играть
                  </button>
                  {isOwn(game) && (
                    <>
                      <button
                        type="button"
                        className="btn btn-small btn-secondary"
                        onClick={() => startEdit(game)}
                        disabled={loadingGame}
                      >
                        Редактировать
                      </button>
                      <button
                        type="button"
                        className="btn btn-small btn-danger"
                        onClick={() => setDeleting(game)}
                      >
                        Удалить
                      </button>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {deleting && (
        <ConfirmDialog
          title="Удаление игры"
          message={<>Удалить игру «<strong>{deleting.title}</strong>»?</>}
          hint="Вместе с игрой будут удалены все её слова и результаты прохождений."
          confirmLabel="Удалить игру"
          busyLabel="Удаление…"
          errorMessage="Не удалось удалить игру"
          onConfirm={confirmDelete}
          onClose={() => setDeleting(null)}
        />
      )}
    </div>
  )
}
