import { useEffect, useState } from 'react'

function initials(name) {
  return (name || '?').trim().slice(0, 2).toUpperCase()
}

/** Аватар команды или пользователя с безопасным фолбэком на инициалы. */
export default function TeamAvatar({ src, name, size = 'md' }) {
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    setFailed(false)
  }, [src])

  const className = `team-avatar team-avatar-${size}`

  if (src && !failed) {
    return (
      <img
        src={src}
        alt={name || 'Аватар'}
        className={className}
        onError={() => setFailed(true)}
      />
    )
  }

  return <span className={`${className} team-avatar-fallback`}>{initials(name)}</span>
}
