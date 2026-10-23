import { useCallback, useEffect, useState } from 'react'
import { apiMessage } from '../api/client'
import { fetchGames } from '../api/games'
import AdminPanel from '../components/associations/AdminPanel'
import PlayerPanel from '../components/associations/PlayerPanel'

// режим и выбранная игра переживают перезагрузку страницы
const MODE_KEY = 'assoc:mode'
const GAME_KEY = 'assoc:game'

function readStored(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/**
 * Раздел «Игра в ассоциации»: переключатель режима «Администратор» / «Игрок».
 * Администратор создаёт игру из 31 слова, игрок разгадывает пирамиду.
 */
export default function Associations() {
  const [mode, setMode] = useState(() => (readStored(MODE_KEY) === 'player' ? 'player' : 'admin'))
  const [games, setGames] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedGameId, setSelectedGameId] = useState(() => {
    const stored = Number(readStored(GAME_KEY))
    return Number.isFinite(stored) && stored > 0 ? stored : null
  })

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

  useEffect(() => {
    try {
      localStorage.setItem(MODE_KEY, mode)
    } catch {
      // localStorage может быть недоступен — не критично
    }
  }, [mode])

  useEffect(() => {
    if (!selectedGameId) return
    try {
      localStorage.setItem(GAME_KEY, String(selectedGameId))
    } catch {
      // localStorage может быть недоступен — не критично
    }
  }, [selectedGameId])

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
        <AdminPanel games={games} onReload={loadGames} onOpenForPlayer={openForPlayer} />
      ) : (
        <PlayerPanel games={games} gameId={selectedGameId} onSelectGame={setSelectedGameId} />
      )}
    </div>
  )
}
