import { useRef, useState } from 'react'
import { apiMessage } from '../../api/client'
import { createTeam, uploadTeamAvatar } from '../../api/teams'
import Modal from '../Modal'
import TeamAvatar from './TeamAvatar'

const MAX_FILE_SIZE = 5 * 1024 * 1024

/** Создание команды: название + аватар ссылкой или файлом с превью. */
export default function CreateTeamModal({ onClose, onCreated }) {
  const [name, setName] = useState('')
  const [avatarUrl, setAvatarUrl] = useState('')
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const fileRef = useRef(null)

  const pickFile = () => fileRef.current?.click()

  const uploadFile = async (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setError('')
    if (!file.type.startsWith('image/')) {
      setError('Выберите файл изображения (PNG, JPEG, WEBP или GIF)')
      event.target.value = ''
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setError('Файл не должен превышать 5 МБ')
      event.target.value = ''
      return
    }
    setUploading(true)
    try {
      setAvatarUrl(await uploadTeamAvatar(file))
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!name.trim()) {
      setError('Введите название команды')
      return
    }
    setSaving(true)
    setError('')
    try {
      const team = await createTeam({ name: name.trim(), avatarUrl: avatarUrl.trim() || null })
      onCreated(team)
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Создать команду" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}

        <div className="team-avatar-editor">
          <TeamAvatar src={avatarUrl.trim()} name={name || 'Команда'} size="lg" />
          <div className="team-avatar-editor-controls">
            <button type="button" className="btn btn-secondary" onClick={pickFile} disabled={uploading}>
              {uploading ? 'Загрузка…' : 'Загрузить файл'}
            </button>
            {avatarUrl && (
              <button type="button" className="btn btn-ghost btn-small"
                      onClick={() => setAvatarUrl('')}>
                Убрать аватар
              </button>
            )}
            <p className="hint">PNG, JPEG, WEBP или GIF, до 5 МБ</p>
            <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp,image/gif"
                   style={{ display: 'none' }} onChange={uploadFile} />
          </div>
        </div>

        <label className="field">
          <span>Название команды *</span>
          <input value={name} onChange={(e) => setName(e.target.value)}
                 placeholder="Например, «Мозговой штурм»" maxLength={100} autoFocus />
        </label>

        <label className="field">
          <span>Ссылка на аватар</span>
          <input value={avatarUrl} onChange={(e) => setAvatarUrl(e.target.value)}
                 placeholder="https://example.com/logo.png" maxLength={500} />
          <span className="hint">Можно загрузить файл или вставить ссылку http(s).</span>
        </label>

        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={saving}>
            Отмена
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving || uploading}>
            {saving ? 'Создание…' : 'Создать команду'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
