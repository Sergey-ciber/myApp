import { useEffect, useState } from 'react'
import Modal from './Modal'

export default function ConfirmDialog({
  title = 'Подтверждение',
  message,
  hint,
  confirmLabel = 'Удалить',
  cancelLabel = 'Отмена',
  busyLabel = 'Удаление…',
  errorMessage = 'Не удалось выполнить действие',
  onConfirm,
  onClose,
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [busy, onClose])

  const confirm = async () => {
    setBusy(true)
    setError('')
    try {
      await onConfirm()
    } catch (err) {
      setError(err.response?.data?.message || errorMessage)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={title} onClose={() => { if (!busy) onClose() }}>
      {error && <div className="alert alert-error">{error}</div>}
      <p className="confirm-text">{message}</p>
      {hint && <p className="confirm-hint">{hint}</p>}
      <div className="form-actions confirm-actions">
        <button type="button" className="btn btn-ghost"
                onClick={onClose} disabled={busy} autoFocus>
          {cancelLabel}
        </button>
        <button type="button" className="btn btn-danger"
                onClick={confirm} disabled={busy}>
          {busy ? busyLabel : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
