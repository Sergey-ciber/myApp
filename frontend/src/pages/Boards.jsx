import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { apiMessage } from '../api/client'
import BoardFormModal from '../components/BoardFormModal'
import ConfirmDialog from '../components/ConfirmDialog'

export default function Boards() {
  const [boards, setBoards] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState(null)
  const [deleting, setDeleting] = useState(null)

  const load = useCallback(async () => {
    try {
      const res = await api.get('/boards')
      setBoards(res.data)
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const createBoard = async (data) => {
    await api.post('/boards', data)
    setShowCreate(false)
    load()
  }

  const updateBoard = async (data) => {
    await api.put(`/boards/${editing.id}`, data)
    setEditing(null)
    load()
  }

  const deleteBoard = async (board) => {
    await api.delete(`/boards/${board.id}`)
    setDeleting(null)
    load()
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Мои доски</h1>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Новая доска
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading ? (
        <div className="page-loader">Загрузка досок…</div>
      ) : boards.length === 0 ? (
        <div className="empty-state">
          <p>У вас пока нет досок.</p>
          <p>Создайте первую доску, чтобы начать работу с заданиями.</p>
        </div>
      ) : (
        <div className="boards-grid">
          {boards.map((board) => (
            <div key={board.id} className="board-card" style={{ background: board.color }}>
              <Link to={`/boards/${board.id}`} className="board-card-link">
                <h3>{board.title}</h3>
                {board.description && <p>{board.description}</p>}
                <span className="board-card-meta">
                  {board.taskCount} {plural(board.taskCount, ['задание', 'задания', 'заданий'])}
                </span>
              </Link>
              <div className="board-card-actions">
                <button className="btn btn-small btn-ghost-light"
                        onClick={() => setEditing(board)}>Изменить</button>
                <button className="btn btn-small btn-ghost-light"
                        onClick={() => setDeleting(board)}>Удалить</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showCreate && (
        <BoardFormModal board={null} onSave={createBoard} onClose={() => setShowCreate(false)} />
      )}
      {editing && (
        <BoardFormModal board={editing} onSave={updateBoard} onClose={() => setEditing(null)} />
      )}
      {deleting && (
        <ConfirmDialog
          title="Удаление доски"
          message={<>Удалить доску «<strong>{deleting.title}</strong>»?</>}
          hint="Действие необратимо: вместе с доской будут удалены все её задания."
          confirmLabel="Удалить доску"
          busyLabel="Удаление…"
          errorMessage="Не удалось удалить доску"
          onConfirm={() => deleteBoard(deleting)}
          onClose={() => setDeleting(null)}
        />
      )}
    </div>
  )
}

function plural(n, forms) {
  const abs = Math.abs(n) % 100
  const n1 = abs % 10
  if (abs > 10 && abs < 20) return forms[2]
  if (n1 > 1 && n1 < 5) return forms[1]
  if (n1 === 1) return forms[0]
  return forms[2]
}
