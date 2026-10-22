import { useState } from 'react'
import Modal from './Modal'

const COLORS = ['#0079bf', '#d29034', '#519839', '#b04632', '#89609e', '#1d7f8c', '#cd5a91', '#6b778c']

export default function BoardFormModal({ board, onSave, onClose }) {
  const [title, setTitle] = useState(board?.title || '')
  const [description, setDescription] = useState(board?.description || '')
  const [color, setColor] = useState(board?.color || COLORS[0])
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (!title.trim()) {
      setError('Введите название доски')
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSave({ title: title.trim(), description, color })
    } catch (err) {
      setError(err.response?.data?.message || 'Не удалось сохранить доску')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={board ? 'Редактирование доски' : 'Новая доска'} onClose={onClose}>
      <form onSubmit={submit} className="form">
        {error && <div className="alert alert-error">{error}</div>}
        <label className="field">
          <span>Название</span>
          <input value={title} onChange={(e) => setTitle(e.target.value)}
                 placeholder="Например, Мой проект" autoFocus />
        </label>
        <label className="field">
          <span>Описание</span>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)}
                    placeholder="Описание доски (необязательно)" rows={3} />
        </label>
        <div className="field">
          <span>Цвет</span>
          <div className="color-picker">
            {COLORS.map((c) => (
              <button type="button" key={c}
                      className={`color-swatch ${color === c ? 'selected' : ''}`}
                      style={{ background: c }}
                      onClick={() => setColor(c)} />
            ))}
          </div>
        </div>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Отмена</button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Сохранение…' : 'Сохранить'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
