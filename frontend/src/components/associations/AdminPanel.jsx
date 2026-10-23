import { useState } from 'react'
import { apiMessage } from '../../api/client'
import { activateGame, createGame, deleteGame, fetchGame, updateGame } from '../../api/games'
import ConfirmDialog from '../ConfirmDialog'
import {
  COLUMN_HEADERS,
  COLUMN_SIZES,
  cloneColumns,
  emptyColumns,
  hintLabel,
  toPairs,
} from './constants'

/**
 * Режим администратора: форма из 5 колонок (16/8/4/2/1) + список сохранённых игр.
 */
export default function AdminPanel({ games, onReload, onOpenForPlayer }) {
  const [title, setTitle] = useState('')
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
    setColumns(emptyColumns())
    setEditingId(null)
    setShowErrors(false)
    setError('')
  }

  const applyGame = (game) => {
    setTitle(game.title)
    setColumns(cloneColumns(game.columns))
    setEditingId(game.id)
    setShowErrors(false)
    setError('')
  }

  const startEdit = async (game) => {
    setLoadingGame(true)
    setError('')
    setMessage('')
    try {
      applyGame(await fetchGame(game.id))
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

  const save = async (event) => {
    event.preventDefault()
    setError('')
    setMessage('')

    if (!title.trim() || emptyCount > 0) {
      setShowErrors(true)
      setError(!title.trim()
        ? 'Укажите название игры'
        : `Заполните все 31 слово — пустых полей: ${emptyCount}`)
      return
    }

    setSaving(true)
    try {
      const payload = {
        title: title.trim(),
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

  const openForPlayer = async (game) => {
    setError('')
    try {
      // активная игра открывается игроку по умолчанию
      await activateGame(game.id)
      await onReload()
      onOpenForPlayer(game.id)
    } catch (err) {
      setError(apiMessage(err))
    }
  }

  return (
    <div className="assoc-admin">
      <form className="assoc-form" onSubmit={save} noValidate>
        <div className="assoc-panel">
          <label className="field">
            <span>Название игры</span>
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Например, Животный мир"
              className={showErrors && !title.trim() ? 'input-invalid' : ''}
            />
          </label>
          <p className="hint">
            Заполните 31 слово: колонка 1 — 16 стартовых слов, дальше 8 + 4 + 2 + 1 ответ.
            Ответ в колонке N+1 загадывается по паре слов (2i−1, 2i) из колонки N.
          </p>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert alert-success">{message}</div>}

        <div className="assoc-scroll">
          <div className="assoc-columns">
            {COLUMN_SIZES.map((size, columnIndex) => (
              <section className="assoc-column" key={columnIndex}>
                <header className="assoc-column-header">
                  <h3>{COLUMN_HEADERS[columnIndex].title}</h3>
                  <span>{COLUMN_HEADERS[columnIndex].subtitle}</span>
                </header>

                {columnIndex === 0 ? (
                  <div className="assoc-pairs">
                    {toPairs(columns[0]).map((pair, pairIndex) => (
                      <div className="assoc-pair" key={pairIndex}>
                        <span className="assoc-pair-num">Пара {pairIndex + 1}</span>
                        {pair.map((word, offset) => {
                          const position = pairIndex * 2 + offset
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
                    ))}
                  </div>
                ) : (
                  <div className="assoc-cards">
                    {Array.from({ length: size }, (_, position) => (
                      <div
                        key={position}
                        className={`assoc-edit-card ${isCellInvalid(columnIndex, position) ? 'invalid' : ''}`}
                      >
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
                    ))}
                  </div>
                )}
              </section>
            ))}
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
        <h2>Сохранённые игры</h2>
        {games.length === 0 ? (
          <div className="empty-state">
            Пока нет ни одной игры. Заполните форму выше и нажмите «Сохранить игру».
          </div>
        ) : (
          <ul className="assoc-games">
            {games.map((game) => (
              <li key={game.id} className={`assoc-game ${game.active ? 'active' : ''}`}>
                <div className="assoc-game-info">
                  <span className="assoc-game-title">{game.title}</span>
                  <span className="assoc-game-meta">
                    {new Date(game.createdAt).toLocaleString('ru-RU')}
                    {game.active && <span className="assoc-badge">активная</span>}
                    {editingId === game.id && <span className="assoc-badge editing">редактируется</span>}
                  </span>
                </div>
                <div className="assoc-game-actions">
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
                    className="btn btn-small btn-primary"
                    onClick={() => openForPlayer(game)}
                  >
                    Открыть для игрока
                  </button>
                  <button
                    type="button"
                    className="btn btn-small btn-danger"
                    onClick={() => setDeleting(game)}
                  >
                    Удалить
                  </button>
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
          hint="Вместе с игрой будут удалены все её слова."
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
