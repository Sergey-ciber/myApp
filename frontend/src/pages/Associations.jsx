import { useCallback, useEffect, useRef, useState } from 'react'
import { apiMessage } from '../api/client'
import { fetchGames } from '../api/games'
import AdminPanel from '../components/associations/AdminPanel'
import PlayerPanel from '../components/associations/PlayerPanel'
import { useAuth } from '../context/AuthContext'
import { userScopedKey } from '../utils/assocStorage'

function readStored(key) {
  if (!key) return null
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function writeStored(key, value) {
  if (!key) return
  try {
    localStorage.setItem(key, value)
  } catch {
    // localStorage может быть недоступен — не критично
  }
}

/**
 * Раздел «Игра в ассоциации»: переключатель режима «Администратор» / «Игрок».
 * Администратор создаёт игру из 31 слова, игрок разгадывает пирамиду.
 *
 * Настройки раздела хранятся под ключами с id пользователя, а прогресс игры
 * живёт только на сервере: при смене пользователя состояние сбрасывается.
 */
export default function Associations() {
  const { user } = useAuth()
  const userId = user?.id
  const modeKey = userScopedKey('mode', userId)
  const gameKey = userScopedKey('game', userId)

  const [mode, setMode] = useState(() => (readStored(modeKey) === 'player' ? 'player' : 'admin'))
  const [games, setGames] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedGameId, setSelectedGameId] = useState(() => {
    const stored = Number(readStored(gameKey))
    return Number.isFinite(stored) && stored > 0 ? stored : null
  })
  const previousUserId = useRef(userId)

  const loadGames = useCallback(async () => {
    try {
      const data = await fetchGames()
      setGames(data)
      setError('')
      return data
    } catch (err) {
      setError(apiMessage(err))
      return []
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadGames().then((data) => {
      if (data.length === 0) return
      // сохраняем выбранную игру, если она ещё существует, иначе — активную
      setSelectedGameId((current) => {
        if (data.some((game) => game.id === current)) return current
        const active = data.find((game) => game.active) || data[0]
        return active.id
      })
    })
  }, [loadGames])

  // выбранная игра могла быть удалена — тогда открываем активную или первую
  useEffect(() => {
    if (games.length === 0) {
      setSelectedGameId(null)
      return
    }
    setSelectedGameId((current) => {
      if (games.some((game) => game.id === current)) return current
      const active = games.find((game) => game.active) || games[0]
      return active.id
    })
  }, [games])

  // сменился пользователь — сбрасываем режим и выбранную игру предыдущей сессии
  useEffect(() => {
    if (previousUserId.current === userId) return
    previousUserId.current = userId
    setMode('admin')
    setSelectedGameId(null)
  }, [userId])

  useEffect(() => {
    writeStored(modeKey, mode)
  }, [modeKey, mode])

  useEffect(() => {
    if (!selectedGameId || !gameKey) return
    writeStored(gameKey, String(selectedGameId))
  }, [gameKey, selectedGameId])

  const openForPlayer = (id) => {
    setSelectedGameId(id)
    setMode('player')
  }

  /** В режиме игрока всегда нужна существующая игра: активная или первая в списке. */
  const switchMode = (nextMode) => {
    if (nextMode === 'player' && games.length > 0) {
      const exists = games.some((game) => game.id === selectedGameId)
      if (!exists) {
        const active = games.find((game) => game.active) || games[0]
        setSelectedGameId(active.id)
      }
    }
    setMode(nextMode)
  }

  return (
    <div className="page assoc-page">
      <div className="page-header">
        <h1>Игра в ассоциации</h1>
        <div className="mode-switch" role="tablist" aria-label="Режим игры">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'admin'}
            className={`mode-btn ${mode === 'admin' ? 'active' : ''}`}
            onClick={() => switchMode('admin')}
          >
            Администратор
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'player'}
            className={`mode-btn ${mode === 'player' ? 'active' : ''}`}
            onClick={() => switchMode('player')}
          >
            Игрок
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="page-loader">Загрузка игр…</div>
      ) : mode === 'admin' ? (
        <AdminPanel
          games={games}
          onReload={loadGames}
          onPlay={openForPlayer}
          currentUserId={user?.id}
        />
      ) : (
        <PlayerPanel
          games={games}
          gameId={selectedGameId}
          onSelectGame={setSelectedGameId}
          currentUserId={user?.id}
        />
      )}
    </div>
  )
}
