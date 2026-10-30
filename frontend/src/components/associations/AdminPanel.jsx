import { Fragment, useMemo, useRef, useState } from 'react'
import { apiMessage } from '../../api/client'
import { activateGame, createGame, deleteGame, fetchGameForEdit, updateGame, uploadGameMedia } from '../../api/games'
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

const LEVEL_TYPES = [
  { value: 'NORMAL', label: 'Обычный' },
  { value: 'ASSOCIATIONS', label: 'Игра в ассоциации' },
]

const HINT_TYPES = [
  { value: 'FIRST_LETTER', label: 'Первая буква' },
  { value: 'GENERAL', label: 'Общая подсказка' },
]

const DIFFICULTY_MAX = 5

const VISIBILITIES = [
  { value: 'PUBLIC', label: 'Для всех' },
  { value: 'TEAM', label: 'Для моей команды' },
]

/** Сложность в виде звёздочек: 1..5. */
const difficultyLabel = (value) => '★'.repeat(Math.max(0, Math.min(DIFFICULTY_MAX, Number(value) || 0)))

function createEmptyLevel(index) {
  return {
    title: `Уровень ${index + 1}`,
    timeLimitMinutes: '10',
    type: 'ASSOCIATIONS',
    hintsCount: '0',
    hints: [],
    mediaUrls: [],
    associationColumns: emptyColumns(),
    questions: [],
  }
}

function createEmptyHint(index) {
  return {
    type: 'FIRST_LETTER',
    text: '',
    imageUrl: '',
    triggerTime: '',
    penaltyTime: '',
  }
}

function createEmptyQuestion() {
  return {
    question: '',
    answers: [''],
  }
}

/** Область добавления медиафайлов (максимум 10). */
function MediaUploader({ urls, onChange, max = 10 }) {
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef(null)
  const list = urls || []

  const addFiles = async (event) => {
    const files = Array.from(event.target.files || [])
    event.target.value = ''
    if (!files.length) return
    const remaining = max - list.length
    if (remaining <= 0) return
    setUploading(true)
    try {
      const uploaded = []
      for (const file of files.slice(0, remaining)) {
        uploaded.push(await uploadGameMedia(file))
      }
      onChange([...list, ...uploaded].slice(0, max))
    } catch {
      // ошибка загрузки файла
    } finally {
      setUploading(false)
    }
  }

  const remove = (index) => onChange(list.filter((_, i) => i !== index))

  return (
    <div className="assoc-media-field">
      <div className="assoc-media-field-head">
        <span className="assoc-media-field-label">Медиа файлы ({list.length}/{max})</span>
        <button
          type="button"
          className="btn btn-small btn-secondary"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading || list.length >= max}
        >
          {uploading ? 'Загрузка…' : 'Загрузить медиа'}
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          multiple
          style={{ display: 'none' }}
          onChange={addFiles}
        />
      </div>
      {list.length > 0 && (
        <div className="assoc-media-thumbs">
          {list.map((url, index) => (
            <div key={index} className="assoc-media-thumb">
              <img src={url} alt="" />
              <button type="button" className="assoc-media-remove" onClick={() => remove(index)} aria-label="Удалить">×</button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function AdminPanel({ games, onReload, onPlay, currentUserId }) {
  const [view, setView] = useState('all')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [gameDate, setGameDate] = useState('')
  const [timeLimit, setTimeLimit] = useState('10')
  const [difficulty, setDifficulty] = useState(3)
  const [visibility, setVisibility] = useState('PUBLIC')
  const [mediaUrls, setMediaUrls] = useState([])
  const [levelsCount, setLevelsCount] = useState(1)
  const [columns, setColumns] = useState(emptyColumns())
  const [levels, setLevels] = useState([])
  const [editingId, setEditingId] = useState(null)
  const [showErrors, setShowErrors] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const [loadingGame, setLoadingGame] = useState(false)
  const [difficultyFilter, setDifficultyFilter] = useState('all')
  const [sortBy, setSortBy] = useState('date')
  const [searchQuery, setSearchQuery] = useState('')

  const isOwn = (game) => game.creatorId != null && game.creatorId === currentUserId
  const myGames = useMemo(() => games.filter(isOwn), [games, currentUserId])

  const visibleGames = useMemo(() => {
    const source = view === 'mine' ? myGames : games
    let result = [...source]
    if (difficultyFilter !== 'all') {
      const target = Number(difficultyFilter)
      result = result.filter((game) => (game.difficulty || 3) === target)
    }
    const query = searchQuery.trim().toLowerCase()
    if (query) {
      result = result.filter((game) =>
        (game.title || '').toLowerCase().includes(query)
        || (game.creatorUsername || '').toLowerCase().includes(query))
    }
    const difficultyOrder = (game) => Number(game.difficulty) || 3
    if (sortBy === 'title') {
      result.sort((a, b) => (a.title || '').localeCompare(b.title || '', 'ru'))
    } else if (sortBy === 'difficulty') {
      result.sort((a, b) => difficultyOrder(a) - difficultyOrder(b)
        || (a.title || '').localeCompare(b.title || '', 'ru'))
    } else {
      result.sort((a, b) => {
        const da = a.gameDate ? new Date(a.gameDate).getTime() : -Infinity
        const db = b.gameDate ? new Date(b.gameDate).getTime() : -Infinity
        return db - da
      })
    }
    return result
  }, [view, games, myGames, difficultyFilter, sortBy, searchQuery])

  const resetForm = () => {
    setTitle('')
    setDescription('')
    setGameDate('')
    setTimeLimit('10')
    setDifficulty(3)
    setVisibility('PUBLIC')
    setMediaUrls([])
    setLevelsCount(1)
    setColumns(emptyColumns())
    setLevels([])
    setEditingId(null)
    setShowErrors(false)
    setError('')
    setMessage('')
    setView('all')
  }

  const startCreate = () => {
    resetForm()
    setView('create')
  }

  const applyGame = (game) => {
    setTitle(game.title || '')
    setDescription(game.description || '')
    setGameDate(game.gameDate || '')
    setTimeLimit(formatMinutes(game.timeLimitMinutes ?? 10))
    setDifficulty(game.difficulty || 3)
    setVisibility(game.visibility || 'PUBLIC')
    setMediaUrls(Array.isArray(game.mediaUrls) ? game.mediaUrls : [])
    setLevelsCount(game.levels && game.levels.length > 0 ? game.levels.length : 1)
    setColumns(game.columns ? cloneColumns(game.columns) : emptyColumns())
    if (game.levels && game.levels.length > 0) {
      setLevels(game.levels.map((level, idx) => ({
        id: level.id,
        title: level.title || `Уровень ${idx + 1}`,
        timeLimitMinutes: formatMinutes(level.timeLimitMinutes ?? 10),
        type: level.type || 'ASSOCIATIONS',
        hintsCount: level.hintsCount != null ? String(level.hintsCount) : '0',
        mediaUrls: Array.isArray(level.mediaUrls) ? level.mediaUrls : [],
        hints: (level.hints || []).map((hint) => ({
          id: hint.id,
          type: hint.type || 'FIRST_LETTER',
          text: hint.text || '',
          imageUrl: hint.imageUrl || '',
          triggerTime: hint.triggerTime != null ? String(hint.triggerTime) : '',
          penaltyTime: hint.penaltyTime != null ? String(hint.penaltyTime) : '',
        })),
        associationColumns: level.associationColumns ? cloneColumns(level.associationColumns) : emptyColumns(),
        questions: (level.questions || []).map((q) => ({
          id: q.id,
          question: q.question || '',
          answers: Array.isArray(q.answers) ? q.answers : (q.answer ? [q.answer] : ['']),
        })),
      })))
    } else {
      setLevels([])
    }
    setEditingId(game.id)
    setShowErrors(false)
    setError('')
    setView('create')
  }

  const startEdit = async (game) => {
    if (!isOwn(game)) return
    setLoadingGame(true)
    setError('')
    setMessage('')
    try {
      applyGame(await fetchGameForEdit(game.id))
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoadingGame(false)
    }
  }

  const addLevel = () => {
    const count = Math.max(1, Math.min(50, Number(levelsCount) || 1))
    setLevels((prev) => {
      const next = [...prev]
      for (let i = 0; i < count; i++) {
        next.push(createEmptyLevel(next.length))
      }
      return next
    })
  }

  /** Обновить список медиафайлов уровня. */
  const updateLevelMedia = (levelIndex, urls) => {
    setLevels((prev) => prev.map((level, i) => (i === levelIndex ? { ...level, mediaUrls: urls } : level)))
  }

  const removeLevel = (index) => {
    setLevels((prev) => prev.filter((_, i) => i !== index))
  }

  const updateLevel = (index, patch) => {
    setLevels((prev) => prev.map((level, i) => (i === index ? { ...level, ...patch } : level)))
  }

  const addHint = (levelIndex) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      return { ...level, hints: [...(level.hints || []), createEmptyHint((level.hints || []).length)] }
    }))
  }

  const removeHint = (levelIndex, hintIndex) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      return { ...level, hints: (level.hints || []).filter((_, h) => h !== hintIndex) }
    }))
  }

  const updateHint = (levelIndex, hintIndex, patch) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      const hints = (level.hints || []).map((hint, h) => (h === hintIndex ? { ...hint, ...patch } : hint))
      return { ...level, hints }
    }))
  }

  const addQuestion = (levelIndex) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      return { ...level, questions: [...(level.questions || []), createEmptyQuestion()] }
    }))
  }

  const removeQuestion = (levelIndex, qIndex) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      return { ...level, questions: (level.questions || []).filter((_, q) => q !== qIndex) }
    }))
  }

  const updateQuestion = (levelIndex, qIndex, patch) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      const questions = (level.questions || []).map((q, idx) => (idx === qIndex ? { ...q, ...patch } : q))
      return { ...level, questions }
    }))
  }

  /** Обновить отдельный вариант ответа вопроса (массив answers). */
  const updateAnswer = (levelIndex, qIndex, answerIndex, value) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      const questions = (level.questions || []).map((q, idx) => {
        if (idx !== qIndex) return q
        const answers = Array.isArray(q.answers) ? [...q.answers] : [q.answer || '']
        answers[answerIndex] = value
        return { ...q, answers }
      })
      return { ...level, questions }
    }))
  }

  /** Добавить новый вариант ответа к вопросу. */
  const addAnswer = (levelIndex, qIndex) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      const questions = (level.questions || []).map((q, idx) => {
        if (idx !== qIndex) return q
        const answers = Array.isArray(q.answers) ? [...q.answers] : [q.answer || '']
        answers.push('')
        return { ...q, answers }
      })
      return { ...level, questions }
    }))
  }

  /** Удалить лишний вариант ответа (первый вариант всегда остаётся). */
  const removeAnswer = (levelIndex, qIndex, answerIndex) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      const questions = (level.questions || []).map((q, idx) => {
        if (idx !== qIndex) return q
        const answers = Array.isArray(q.answers) ? [...q.answers] : [q.answer || '']
        if (answers.length > 1) {
          answers.splice(answerIndex, 1)
        }
        return { ...q, answers }
      })
      return { ...level, questions }
    }))
  }

  const setLevelWord = (levelIndex, columnIndex, position, value) => {
    setLevels((prev) => prev.map((level, i) => {
      if (i !== levelIndex) return level
      const associationColumns = (level.associationColumns || emptyColumns()).map((words, c) =>
        c === columnIndex ? words.map((word, p) => (p === position ? value : word)) : words)
      return { ...level, associationColumns }
    }))
  }

  const isCellInvalidLevel = (levelIndex, columnIndex, position) => {
    if (!showErrors) return false
    const level = levels[levelIndex]
    if (!level || level.type !== 'ASSOCIATIONS') return false
    const cols = level.associationColumns || []
    return !cols[columnIndex] || !cols[columnIndex][position] || !cols[columnIndex][position].trim()
  }

  const save = async (event) => {
    event.preventDefault()
    setError('')
    setMessage('')

    if (!title.trim()) {
      setShowErrors(true)
      setError('Укажите название игры')
      return
    }

    if (!levels.length) {
      setShowErrors(true)
      setError('Добавьте хотя бы один уровень')
      return
    }

    for (let i = 0; i < levels.length; i++) {
      const level = levels[i]
      if (!level.title?.trim()) {
        setShowErrors(true)
        setError(`Уровень ${i + 1}: укажите название уровня`)
        return
      }
      if (!level.type) {
        setShowErrors(true)
        setError(`Уровень ${i + 1}: укажите тип уровня`)
        return
      }
      const lt = Number(level.timeLimitMinutes)
      if (!level.timeLimitMinutes?.toString().trim() || !Number.isFinite(lt) || lt <= 0) {
        setShowErrors(true)
        setError(`Уровень ${i + 1}: укажите время уровня больше 0`)
        return
      }
      const hc = Number(level.hintsCount)
      if (!Number.isFinite(hc) || hc < 0) {
        setShowErrors(true)
        setError(`Уровень ${i + 1}: укажите количество подсказок`)
        return
      }
      if (level.type === 'ASSOCIATIONS') {
        const cols = level.associationColumns || []
        let empty = 0
        for (let c = 0; c < cols.length; c++) {
          for (let p = 0; p < (cols[c] || []).length; p++) {
            if (!(cols[c][p] || '').trim()) empty++
          }
        }
        if (empty > 0) {
          setShowErrors(true)
          setError(`Заполните все 31 слово для уровня-ассоциации (Уровень ${i + 1})`)
          return
        }
      }
      if (level.type === 'NORMAL') {
        const questions = level.questions || []
        const levelErrors = []
        const passThreshold = Number(level.passThreshold)
        if (level.passThreshold === undefined || level.passThreshold === null || level.passThreshold?.toString().trim() === '' || !Number.isFinite(passThreshold) || passThreshold <= 0) {
          levelErrors.push(`Уровень ${i + 1}: укажите порог прохождения`)
        } else if (questions.length > 0 && passThreshold > questions.length) {
          levelErrors.push(`Уровень ${i + 1}: порог прохождения не может быть больше количества вопросов`)
        }
        if (questions.length === 0) {
          levelErrors.push(`Уровень ${i + 1}: добавьте хотя бы один вопрос`)
        }
        for (let q = 0; q < questions.length; q++) {
          const question = questions[q]
          if (!(question.question || '').trim()) {
            levelErrors.push(`Уровень ${i + 1}, Вопрос ${q + 1}: заполните текст вопроса`)
          }
          const answers = (question.answers || []).map((a) => (a || '').trim()).filter((a) => a)
          if (answers.length === 0) {
            levelErrors.push(`Уровень ${i + 1}, Вопрос ${q + 1}: добавьте хотя бы один правильный ответ`)
          }
        }
        if (levelErrors.length > 0) {
          setShowErrors(true)
          setError(`Ошибка сохранения. Проверьте следующие поля:\n- ${levelErrors.join('\n- ')}`)
          setTimeout(() => {
            const panels = document.querySelectorAll('.assoc-panel')
            if (panels[i + 1]) panels[i + 1].scrollIntoView({ behavior: 'smooth', block: 'center' })
          }, 50)
          return
        }
      }
      if (level.hints) {
        for (let h = 0; h < level.hints.length; h++) {
          const hint = level.hints[h]
          if (!hint.type) {
            setShowErrors(true)
            setError(`Уровень ${i + 1}, подсказка ${h + 1}: укажите тип подсказки`)
            return
          }
          if (hint.type === 'GENERAL') {
            if (!hint.text?.trim() && !hint.imageUrl?.trim()) {
              setShowErrors(true)
              setError(`Уровень ${i + 1}, подсказка ${h + 1}: для общей подсказки нужен текст или изображение`)
              return
            }
          }
        }
      }
    }

    const timeLimitValue = Number(timeLimit)
    if (timeLimit && (!Number.isFinite(timeLimitValue) || timeLimitValue <= 0)) {
      setShowErrors(true)
      setError('Укажите время игры в минутах (положительное число)')
      return
    }

    setSaving(true)
    try {
      const payload = {
        title: title.trim(),
        description: description.trim(),
        gameDate: gameDate || null,
        timeLimitMinutes: timeLimitValue || 10,
        difficulty: Number(difficulty) || 3,
        visibility,
        mediaUrls: mediaUrls.slice(0, 10),
        columns: columns.map((words) => words.map((word) => word.trim())),
        levels: levels.map((level, idx) => ({
          title: level.title.trim(),
          timeLimitMinutes: Number(level.timeLimitMinutes),
          type: level.type,
          hintsCount: Number(level.hintsCount) || 0,
          passThreshold: level.type === 'NORMAL' ? Number(level.passThreshold) : null,
          mediaUrls: (level.mediaUrls || []).slice(0, 10),
          hints: (level.hints || []).map((hint) => ({
            type: hint.type,
            text: hint.text?.trim() || null,
            imageUrl: hint.imageUrl?.trim() || null,
            triggerTime: hint.triggerTime?.toString().trim() ? Number(hint.triggerTime) : null,
            penaltyTime: hint.penaltyTime?.toString().trim() ? Number(hint.penaltyTime) : null,
          })),
          associationColumns: level.type === 'ASSOCIATIONS' ? (level.associationColumns || []).map((words) => words.map((word) => word.trim())) : null,
          questions: level.type === 'NORMAL' ? (level.questions || []).map((q) => ({
            question: q.question?.trim(),
            answers: (q.answers || []).map((a) => (a || '').trim()).filter((a) => a),
          })) : null,
          standardQuestions: level.type === 'NORMAL' ? (level.questions || []).map((q) => ({
            questionText: q.question?.trim(),
            mediaType: null,
            mediaUrl: null,
            answers: (q.answers || []).map((a) => (a || '').trim()).filter((a) => a),
          })) : null,
        })),
      }
      const saved = editingId ? await updateGame(editingId, payload) : await createGame(payload)
      setMessage('Игра сохранена')
      await onReload()
      applyGame(saved)
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
      if (isOwn(game)) {
        await activateGame(game.id)
        await onReload()
      }
    } catch {
      // ignore
    }
    onPlay(game.id)
  }

  const renderLevelPyramid = (levelIndex) => {
    const level = levels[levelIndex]
    if (!level || level.type !== 'ASSOCIATIONS') return null
    const cols = level.associationColumns || emptyColumns()
    return (
      <div className="assoc-scroll">
        <div className="assoc-pyramid-wrap">
          <PyramidHead />
          <div className="assoc-pyramid assoc-pyramid--admin">
            {toPairs(cols[0]).map((pair, row) => (
              <Fragment key={`row-${row}`}>
                <div className="assoc-cell" data-column="1" style={{ gridColumn: 1, gridRow: row + 1 }}>
                  <div className="assoc-pair">
                    <span className="assoc-pair-num">Пара {row + 1}</span>
                    {pair.map((word, offset) => {
                      const position = row * 2 + offset
                      return (
                        <label className="assoc-word-input" key={position}>
                          <span>Слово {position + 1}</span>
                          <input
                            value={cols[0][position]}
                            onChange={(event) => setLevelWord(levelIndex, 0, position, event.target.value)}
                            className={`assoc-input ${isCellInvalidLevel(levelIndex, 0, position) ? 'input-invalid' : ''}`}
                            aria-label={`Слово ${position + 1}`}
                            placeholder={`Слово ${position + 1}`}
                          />
                        </label>
                      )
                    })}
                  </div>
                </div>
                <div className="assoc-cell" data-column="2" style={{ gridColumn: 2, gridRow: row + 1 }}>
                  <div className={`assoc-edit-card ${isCellInvalidLevel(levelIndex, 1, row) ? 'invalid' : ''}`}>
                    <div className="assoc-edit-card-head">
                      <span className="assoc-edit-label">Ответ {row + 1}</span>
                      <span className="assoc-hint">{hintLabel(1, row)}</span>
                    </div>
                    <input
                      value={cols[1][row]}
                      onChange={(event) => setLevelWord(levelIndex, 1, row, event.target.value)}
                      className={`assoc-input ${isCellInvalidLevel(levelIndex, 1, row) ? 'input-invalid' : ''}`}
                      aria-label={`Ответ ${row + 1} в колонке 2`}
                      placeholder="Введите слово"
                    />
                  </div>
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
                  style={{ gridColumn: columnIndex + 1, gridRow: `${position * span + 1} / span ${span}` }}
                >
                  <div className={`assoc-edit-card ${isCellInvalidLevel(levelIndex, columnIndex, position) ? 'invalid' : ''}`}>
                    <div className="assoc-edit-card-head">
                      <span className="assoc-edit-label">Ответ {position + 1}</span>
                      <span className="assoc-hint">{hintLabel(columnIndex, position)}</span>
                    </div>
                    <input
                      value={cols[columnIndex][position]}
                      onChange={(event) => setLevelWord(levelIndex, columnIndex, position, event.target.value)}
                      className={`assoc-input ${isCellInvalidLevel(levelIndex, columnIndex, position) ? 'input-invalid' : ''}`}
                      aria-label={`Ответ ${position + 1} в колонке ${columnIndex + 1}`}
                      placeholder="Введите слово"
                    />
                  </div>
                </div>
              ))
            })}
          </div>
        </div>
      </div>
    )
  }

  const renderTabs = () => (
    <div className="assoc-tabs">
      <button type="button" className={`assoc-tab ${view === 'all' ? 'active' : ''}`} onClick={() => { setError(''); setMessage(''); setView('all') }}>Все игры</button>
      <button type="button" className={`assoc-tab ${view === 'create' ? 'active' : ''}`} onClick={startCreate}>Создать игру</button>
      <button type="button" className={`assoc-tab ${view === 'mine' ? 'active' : ''}`} onClick={() => { setError(''); setMessage(''); setView('mine') }}>Мои игры</button>
      <button type="button" className={`assoc-tab ${view === 'registered' ? 'active' : ''}`} onClick={() => { setError(''); setMessage(''); setView('registered') }}>Зарегистрирован на игры</button>
    </div>
  )

  if (view === 'all' || view === 'mine') {
    return (
      <div className="assoc-admin">
        {renderTabs()}

        <div className="assoc-panel" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h2 style={{ margin: 0, color: '#ffffff' }}>{view === 'mine' ? 'Мои игры' : 'Все игры'}</h2>
            <p className="hint" style={{ margin: '4px 0 0' }}>
              {view === 'mine' ? 'Игры, созданные под вашей учётной записью' : 'Список всех созданных игр'}
            </p>
          </div>
          <div className="assoc-list-controls">
            <label className="field assoc-search-field">
              <span>Поиск</span>
              <input
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Название или автор"
              />
            </label>
            <label className="field">
              <span>Сложность</span>
              <select value={difficultyFilter} onChange={(event) => setDifficultyFilter(event.target.value)}>
                <option value="all">Все</option>
                {Array.from({ length: DIFFICULTY_MAX }, (_, i) => i + 1).map((n) => (
                  <option key={n} value={n}>{'★'.repeat(n)}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Сортировка</span>
              <select value={sortBy} onChange={(event) => setSortBy(event.target.value)}>
                <option value="date">По дате</option>
                <option value="title">По названию</option>
                <option value="difficulty">По сложности</option>
              </select>
            </label>
          </div>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert alert-success">{message}</div>}

        <section className="assoc-saved">
          {visibleGames.length === 0 ? (
            <div className="empty-state">Игр не найдено.</div>
          ) : (
            <ul className="assoc-games">
              {visibleGames.map((game) => (
                <li key={game.id} className={`assoc-game ${game.active ? 'active' : ''}`}>
                  <div className="assoc-game-info">
                    <span className="assoc-game-title">
                      {game.title}
                      {isOwn(game) && <span className="assoc-badge own">моя игра</span>}
                    </span>
                    <span className="assoc-game-meta">
                      <span>Дата: {game.gameDate || 'не указана'}</span>
                      <span>Автор: {game.creatorUsername || 'неизвестен'}</span>
                      <span>Сложность: <span className="assoc-badge difficulty">{difficultyLabel(game.difficulty) || '—'}</span></span>
                      {game.active && <span className="assoc-badge">по умолчанию</span>}
                    </span>
                  </div>
                  <div className="assoc-game-actions">
                    <button type="button" className="btn btn-small btn-primary" onClick={() => playGame(game)}>Играть</button>
                    {isOwn(game) && (
                      <>
                        <button type="button" className="btn btn-small btn-secondary" onClick={() => startEdit(game)} disabled={loadingGame}>Редактировать</button>
                        <button type="button" className="btn btn-small btn-danger" onClick={() => setDeleting(game)}>Удалить</button>
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
            hint="Вместе с игрой будут удалены все уровни, подсказки и результаты."
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

  if (view === 'registered') {
    return (
      <div className="assoc-admin">
        {renderTabs()}
        <div className="empty-state">Раздел «Зарегистрирован на игры» находится в разработке.</div>
      </div>
    )
  }

  return (
    <div className="assoc-admin">
      {renderTabs()}

      <div className="assoc-panel" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, color: '#ffffff' }}>{editingId ? 'Редактирование игры' : 'Создание новой игры'}</h2>
          <p className="hint" style={{ margin: '4px 0 0' }}>Игра состоит из нескольких уровней</p>
        </div>
        <button type="button" className="btn btn-secondary" onClick={resetForm}>К списку игр</button>
      </div>

      <form className="assoc-form" onSubmit={save} noValidate>
        {/* Блок настроек игры */}
        <div className="assoc-panel">
          <h3 style={{ margin: '0 0 16px', color: '#ffffff' }}>Создание новой игры</h3>

          <div className="field-row">
            <label className="field">
              <span>Название игры *</span>
              <input value={title} maxLength={250} onChange={(event) => setTitle(event.target.value)} placeholder="Например, Интеллектуальный марафон" className={showErrors && !title.trim() ? 'input-invalid' : ''} />
            </label>
          </div>

          <div className="field-row">
            <label className="field">
              <span>Описание игры</span>
              <textarea value={description} maxLength={5000} rows={4} onChange={(event) => setDescription(event.target.value)} placeholder="Краткое описание игры" />
            </label>
          </div>

          <MediaUploader urls={mediaUrls} onChange={setMediaUrls} />

          <div className="field-row">
            <label className="field field--compact">
              <span>Дата проведения</span>
              <input type="date" value={gameDate} onChange={(event) => setGameDate(event.target.value)} />
            </label>
            <label className="field field--compact">
              <span>Время на прохождение (мин.)</span>
              <input type="number" min="0.1" step="any" maxLength={10} value={timeLimit} onChange={(event) => setTimeLimit(event.target.value)} placeholder="10" />
            </label>
            <label className="field">
              <span>Сложность</span>
              <div className="assoc-stars">
                {Array.from({ length: DIFFICULTY_MAX }, (_, i) => i + 1).map((n) => (
                  <button
                    key={n}
                    type="button"
                    className={`assoc-star ${n <= difficulty ? 'active' : ''}`}
                    onClick={() => setDifficulty(n)}
                    aria-label={`${n} из ${DIFFICULTY_MAX}`}
                  >
                    ★
                  </button>
                ))}
              </div>
            </label>
          </div>

          <div className="field-row">
            <label className="field">
              <span>Видимость</span>
              <select value={visibility} onChange={(event) => setVisibility(event.target.value)}>
                {VISIBILITIES.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
              </select>
            </label>
            <label className="field field--compact">
              <span>Количество уровней</span>
              <select value={levelsCount} onChange={(event) => setLevelsCount(Number(event.target.value))}>
                {Array.from({ length: 50 }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <label className="field" style={{ alignSelf: 'flex-end' }}>
              <button type="button" className="btn btn-secondary" onClick={addLevel}>Добавить уровень</button>
            </label>
          </div>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert alert-success">{message}</div>}

        {levels.length === 0 && <div className="empty-state">Уровни не добавлены</div>}
          {levels.map((level, levelIndex) => (
            <div key={levelIndex} className="assoc-panel" style={{ borderLeft: '3px solid #ff8c00', marginBottom: 18 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h4 style={{ margin: 0, color: '#ffffff' }}>Уровень {levelIndex + 1}</h4>
                <button type="button" className="btn btn-small btn-danger" onClick={() => removeLevel(levelIndex)}>Удалить уровень</button>
              </div>
              <div className="field-row">
                <label className="field">
                  <span>Название уровня *</span>
                  <input value={level.title} maxLength={250} onChange={(event) => updateLevel(levelIndex, { title: event.target.value })} />
                </label>
                <label className="field">
                  <span>Время уровня (мин.) *</span>
                  <input type="number" min="0.1" step="any" value={level.timeLimitMinutes} onChange={(event) => updateLevel(levelIndex, { timeLimitMinutes: event.target.value })} />
                </label>
                <label className="field">
                  <span>Тип уровня *</span>
                  <select value={level.type} onChange={(event) => updateLevel(levelIndex, { type: event.target.value })}>
                    {LEVEL_TYPES.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
                  </select>
                </label>
                <label className="field">
                  <span>Количество подсказок *</span>
                  <input type="number" min="0" value={level.hintsCount} onChange={(event) => updateLevel(levelIndex, { hintsCount: event.target.value })} />
                </label>
              </div>
              <MediaUploader urls={level.mediaUrls || []} onChange={(urls) => updateLevelMedia(levelIndex, urls)} />
              <div style={{ marginTop: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                  <span style={{ color: '#e5e7eb', fontWeight: 600 }}>Подсказки</span>
                  <button type="button" className="btn btn-small btn-secondary" onClick={() => addHint(levelIndex)}>Добавить подсказку</button>
                </div>
                {(level.hints || []).map((hint, hintIndex) => (
                  <div key={hintIndex} className="assoc-panel" style={{ background: '#1f1f2b', marginBottom: 10 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                      <span style={{ color: '#ffffff' }}>Подсказка {hintIndex + 1}</span>
                      <button type="button" className="btn btn-small btn-danger" onClick={() => removeHint(levelIndex, hintIndex)}>Удалить</button>
                    </div>
                    <div className="field-row">
                      <label className="field">
                        <span>Тип подсказки *</span>
                        <select value={hint.type} onChange={(event) => updateHint(levelIndex, hintIndex, { type: event.target.value })}>
                          {HINT_TYPES.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
                        </select>
                      </label>
                      <label className="field">
                        <span>Время появления (мин. от начала уровня)</span>
                        <input type="number" min="0" step="any" value={hint.triggerTime} onChange={(event) => updateHint(levelIndex, hintIndex, { triggerTime: event.target.value })} placeholder="например, 3" />
                      </label>
                      <label className="field">
                        <span>Штрафное время (мин.)</span>
                        <input type="number" min="0" step="any" value={hint.penaltyTime} onChange={(event) => updateHint(levelIndex, hintIndex, { penaltyTime: event.target.value })} placeholder="например, 1" />
                      </label>
                    </div>
                    {hint.type === 'GENERAL' && (
                      <div className="field-row">
                        <label className="field">
                          <span>Текст подсказки</span>
                          <input value={hint.text} onChange={(event) => updateHint(levelIndex, hintIndex, { text: event.target.value })} placeholder="Текст общей подсказки" />
                        </label>
                        <label className="field">
                          <span>Изображение (URL)</span>
                          <input value={hint.imageUrl} onChange={(event) => updateHint(levelIndex, hintIndex, { imageUrl: event.target.value })} placeholder="https://..." />
                        </label>
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {level.type === 'ASSOCIATIONS' && (
                <div style={{ marginTop: 18 }}>
                  <h4 style={{ margin: '0 0 12px', color: '#ffffff' }}>Пирамида ассоциаций (31 слово)</h4>
                  {renderLevelPyramid(levelIndex)}
                </div>
              )}

              {level.type === 'NORMAL' && (
                <div style={{ marginTop: 18 }}>
                  <div className="field-row" style={{ marginBottom: 12 }}>
                    <label className="field">
                      <span>Порог прохождения (кол-во верных ответов) *</span>
                      <input
                        type="number"
                        min="0"
                        value={level.passThreshold ?? ''}
                        onChange={(event) => updateLevel(levelIndex, { passThreshold: event.target.value })}
                        className={showErrors && (level.passThreshold === undefined || level.passThreshold === null || level.passThreshold.toString().trim() === '') ? 'input-invalid' : ''}
                      />
                    </label>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <span style={{ color: '#e5e7eb', fontWeight: 600 }}>Вопросы и ответы</span>
                    <button type="button" className="btn btn-small btn-secondary" onClick={() => addQuestion(levelIndex)}>Добавить вопрос</button>
                  </div>
                  {(level.questions || []).map((q, qIndex) => (
                    <div key={qIndex} className="assoc-panel" style={{ background: '#1f1f2b', marginBottom: 10 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                        <span style={{ color: '#ffffff' }}>Вопрос {qIndex + 1}</span>
                        <button type="button" className="btn btn-small btn-danger" onClick={() => removeQuestion(levelIndex, qIndex)}>Удалить</button>
                      </div>
                      <label className="field" style={{ marginBottom: 12 }}>
                        <span>Вопрос *</span>
                        <input
                          value={q.question || ''}
                          onChange={(event) => updateQuestion(levelIndex, qIndex, { question: event.target.value })}
                          className={showErrors && !(q.question || '').trim() ? 'input-invalid' : ''}
                        />
                      </label>
                      <div style={{ marginBottom: 8 }}>
                        <span style={{ color: '#e5e7eb', fontWeight: 600 }}>Варианты ответов</span>
                      </div>
                      <div className="field-row" style={{ alignItems: 'flex-start', marginBottom: (q.answers || []).length > 1 ? 8 : 0 }}>
                        <label className="field">
                          <span>Вариант ответа 1 *</span>
                          <input
                            value={q.answers?.[0] || ''}
                            onChange={(event) => updateAnswer(levelIndex, qIndex, 0, event.target.value)}
                            className={showErrors && !(q.answers?.[0] || '').trim() ? 'input-invalid' : ''}
                          />
                        </label>
                      </div>
                      {(q.answers || []).map((answer, answerIndex) => answerIndex > 0 ? (
                        <div key={answerIndex} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
                          <label className="field" style={{ flex: 1 }}>
                            <span>Вариант ответа {answerIndex + 1}</span>
                            <input
                              value={answer || ''}
                              onChange={(event) => updateAnswer(levelIndex, qIndex, answerIndex, event.target.value)}
                              className={showErrors && !(answer || '').trim() ? 'input-invalid' : ''}
                            />
                          </label>
                          <button type="button" className="btn btn-small btn-danger" onClick={() => removeAnswer(levelIndex, qIndex, answerIndex)}>Удалить</button>
                        </div>
                      ) : null)}
                      <button type="button" className="btn btn-small btn-secondary" onClick={() => addAnswer(levelIndex, qIndex)}>+ Добавить вариант ответа</button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}

        <div className="assoc-form-actions" style={{ position: 'sticky', bottom: 10, background: 'rgba(18,18,18,0.9)', padding: '12px 16px', borderRadius: 12, border: '1px solid #3a3a4a' }}>
          <button type="button" className="btn btn-secondary" onClick={resetForm} disabled={saving}>Отмена</button>
          <span className="spacer" />
          <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Сохранение…' : 'Сохранить игру'}</button>
        </div>
      </form>

      {deleting && (
        <ConfirmDialog
          title="Удаление игры"
          message={<>Удалить игру «<strong>{deleting.title}</strong>»?</>}
          hint="Вместе с игрой будут удалены все уровни, подсказки и результаты."
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
