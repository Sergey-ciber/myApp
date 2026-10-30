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
 * Раздел «Игры»: по умолчанию список игр (вкладки «Все игры», «Создать игру»,
 * «Мои игры», «Зарегистрирован на игры»). Игра открывается только по кнопке «Играть».
 */
export default function Associations() {
  const { user } = useAuth()
  const userId = user?.id
  const gameKey = userScopedKey('game', userId)

  const [mode, setMode] = useState('admin')
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

  // выбранная игра могла быть удалена — тогда открываем активную или первую;
  // пока список грузится, сохранённый выбор не трогаем
  useEffect(() => {
    if (loading) return
    if (games.length === 0) {
      setSelectedGameId(null)
      return
    }
    setSelectedGameId((current) => {
      if (games.some((game) => game.id === current)) return current
      const active = games.find((game) => game.active) || games[0]
      return active.id
    })
  }, [games, loading])

  // сменился пользователь — сбрасываем режим и выбранную игру предыдущей сессии
  useEffect(() => {
    if (previousUserId.current === userId) return
    previousUserId.current = userId
    setMode('admin')
    setSelectedGameId(null)
  }, [userId])

  useEffect(() => {
    if (!selectedGameId || !gameKey) return
    writeStored(gameKey, String(selectedGameId))
  }, [gameKey, selectedGameId])

  const openForPlayer = (id) => {
    setSelectedGameId(id)
    setMode('player')
  }

  return (
    <div className="page assoc-page">
      <div className="page-header">
        <h1>Игры</h1>
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
          onExit={() => setMode('admin')}
          currentUserId={user?.id}
        />
      )}
    </div>
  )
}
