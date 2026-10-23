import { useState } from 'react'
import Modal from './Modal'
import ConfirmDialog from './ConfirmDialog'

const STATUSES = [
  { value: 'TODO', label: 'К выполнению' },
  { value: 'IN_PROGRESS', label: 'В работе' },
  { value: 'DONE', label: 'Готово' },
]

export default function TaskModal({ task, defaultStatus, onSave, onDelete, onClose }) {
  const [title, setTitle] = useState(task?.title || '')
  const [description, setDescription] = useState(task?.description || '')
  const [status, setStatus] = useState(task?.status || defaultStatus || 'TODO')
  const [dueDate, setDueDate] = useState(task?.dueDate || '')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirming, setConfirming] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (!title.trim()) {
      setError('Введите название задания')
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSave({
        title: title.trim(),
        description,
        status,
        dueDate: dueDate || null,
        position: task?.position ?? null,
      })
    } catch (err) {
      setError(err.response?.data?.message || 'Не удалось сохранить задание')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <Modal title={task ? 'Карточка задания' : 'Новое задание'} onClose={onClose}>
        <form onSubmit={submit} className="form">
          {error && <div className="alert alert-error">{error}</div>}
          <label className="field">
            <span>Название</span>
            <input value={title} onChange={(e) => setTitle(e.target.value)}
                   placeholder="Что нужно сделать?" autoFocus />
          </label>
          <label className="field">
            <span>Описание</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)}
                      placeholder="Подробности задания" rows={4} />
          </label>
          <div className="field-row">
            <label className="field">
              <span>Статус</span>
              <select value={status} onChange={(e) => setStatus(e.target.value)}>
                {STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
              </select>
            </label>
            <label className="field">
              <span>Срок</span>
              <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </label>
          </div>
          <div className="form-actions">
            {task && (
              <button type="button" className="btn btn-danger"
                      onClick={() => setConfirming(true)}>Удалить</button>
            )}
            <span className="spacer" />
            <button type="button" className="btn btn-ghost" onClick={onClose}>Отмена</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Сохранение…' : 'Сохранить'}
            </button>
          </div>
        </form>
      </Modal>

      {confirming && (
        <ConfirmDialog
          title="Удаление задания"
          message={<>Удалить задание «<strong>{task.title}</strong>»?</>}
          hint="Действие необратимо."
          confirmLabel="Удалить задание"
          busyLabel="Удаление…"
          errorMessage="Не удалось удалить задание"
          onConfirm={onDelete}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  )
}
