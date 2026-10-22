import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api, { apiMessage } from '../api/client'
import TaskModal from '../components/TaskModal'

const COLUMNS = [
  { status: 'TODO', title: 'К выполнению' },
  { status: 'IN_PROGRESS', title: 'В работе' },
  { status: 'DONE', title: 'Готово' },
]

const STATUS_LABEL = Object.fromEntries(COLUMNS.map((c) => [c.status, c.title]))

export default function BoardDetail() {
  const { id } = useParams()
  const [board, setBoard] = useState(null)
  const [tasks, setTasks] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showCreate, setShowCreate] = useState(false)
  const [createIn, setCreateIn] = useState('TODO')
  const [editing, setEditing] = useState(null)

  const load = useCallback(async () => {
    try {
      const [boardRes, tasksRes] = await Promise.all([
        api.get(`/boards/${id}`),
        api.get(`/boards/${id}/tasks`),
      ])
      setBoard(boardRes.data)
      setTasks(tasksRes.data)
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { load() }, [load])

  const openCreate = (status) => {
    setCreateIn(status)
    setShowCreate(true)
  }

  const createTask = async (data) => {
    await api.post(`/boards/${id}/tasks`, { ...data, status: createIn })
    setShowCreate(false)
    load()
  }

  const updateTask = async (data) => {
    await api.put(`/boards/tasks/${editing.id}`, data)
    setEditing(null)
    load()
  }

  const deleteTask = async () => {
    await api.delete(`/boards/tasks/${editing.id}`)
    setEditing(null)
    load()
  }

  const changeStatus = async (task, status) => {
    try {
      const inColumn = tasks.filter((t) => t.status === status)
      await api.patch(`/boards/tasks/${task.id}/move`, {
        status,
        position: inColumn.length,
      })
      load()
    } catch (err) {
      setError(apiMessage(err))
    }
  }

  if (loading) return <div className="page-loader">Загрузка доски…</div>
  if (!board) {
    return (
      <div className="page">
        <div className="alert alert-error">{error || 'Доска не найдена'}</div>
        <Link to="/boards" className="btn btn-ghost">← К списку досок</Link>
      </div>
    )
  }

  return (
    <div className="page page-board">
      <div className="board-header" style={{ background: board.color }}>
        <div>
          <Link to="/boards" className="board-back">← Все доски</Link>
          <h1>{board.title}</h1>
          {board.description && <p className="board-desc">{board.description}</p>}
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="board-columns">
        {COLUMNS.map((col) => {
          const columnTasks = tasks
            .filter((t) => t.status === col.status)
            .sort((a, b) => a.position - b.position)
          return (
            <div key={col.status} className="column">
              <div className="column-header">
                <h2>{col.title}</h2>
                <span className="column-count">{columnTasks.length}</span>
              </div>
              <div className="column-cards">
                {columnTasks.map((task) => (
                  <div key={task.id} className="task-card" onClick={() => setEditing(task)}>
                    <div className="task-card-title">{task.title}</div>
                    {task.description && (
                      <div className="task-card-desc">{task.description}</div>
                    )}
                    <div className="task-card-footer">
                      {task.dueDate && (
                        <span className={`task-due ${isOverdue(task) ? 'overdue' : ''}`}>
                          📅 {formatDate(task.dueDate)}
                        </span>
                      )}
                      {col.status !== 'TODO' && (
                        <button className="btn-icon" title="Вернуть в «К выполнению»"
                                onClick={(e) => { e.stopPropagation(); changeStatus(task, 'TODO') }}>
                          ↩
                        </button>
                      )}
                      {col.status === 'TODO' && (
                        <button className="btn-icon" title="Передать в работу"
                                onClick={(e) => { e.stopPropagation(); changeStatus(task, 'IN_PROGRESS') }}>
                          →
                        </button>
                      )}
                      {col.status === 'IN_PROGRESS' && (
                        <button className="btn-icon" title="Отметить готовым"
                                onClick={(e) => { e.stopPropagation(); changeStatus(task, 'DONE') }}>
                          ✓
                        </button>
                      )}
                    </div>
                  </div>
                ))}
                <button className="btn btn-add-task" onClick={() => openCreate(col.status)}>
                  + Добавить задание
                </button>
              </div>
            </div>
          )
        })}
      </div>

      {showCreate && (
        <TaskModal task={null} defaultStatus={createIn}
                   onSave={createTask} onClose={() => setShowCreate(false)} />
      )}
      {editing && (
        <TaskModal task={editing} onSave={updateTask}
                   onDelete={deleteTask} onClose={() => setEditing(null)} />
      )}
    </div>
  )
}

function formatDate(iso) {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function isOverdue(task) {
  if (!task.dueDate || task.status === 'DONE') return false
  return task.dueDate < new Date().toISOString().slice(0, 10)
}

export { STATUS_LABEL }
