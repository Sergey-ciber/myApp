import { useEffect, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getTeamGameLobby, startTeamGame } from '../../api/teamGames'
import { apiMessage } from '../../api/client'

const WS_URL = import.meta.env.VITE_WS_URL || '/ws'

export default function TeamGameLobby({ sessionId, onStarted, onError }) {
  const [lobby, setLobby] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)

  useEffect(() => {
    let mounted = true
    getTeamGameLobby(sessionId)
      .then((data) => {
        if (mounted) setLobby(data)
      })
      .catch((err) => setError(apiMessage(err)))
      .finally(() => setLoading(false))
    return () => { mounted = false }
  }, [sessionId])

  useEffect(() => {
    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient.subscribe('/topic/team_session_' + sessionId, (message) => {
          try {
            const payload = JSON.parse(message.body)
            if (payload.type === 'STARTED') {
              if (onStarted) onStarted(payload.state)
            }
          } catch (e) {
            // ignore
          }
        })
      }
    })
    stompClient.activate()
    return () => {
      stompClient.deactivate()
    }
  }, [sessionId, onStarted])

  const handleStart = async () => {
    if (starting) return
    setStarting(true)
    setError('')
    try {
      const state = await startTeamGame(sessionId)
      if (onStarted) onStarted(state)
    } catch (err) {
      setError(apiMessage(err))
      if (onError) onError(apiMessage(err))
    } finally {
      setStarting(false)
    }
  }

  if (loading) return <div className="page-loader">Загрузка лобби…</div>
  if (error) return <div className="alert alert-error">{error}</div>
  if (!lobby) return null

  return (
    <div className="assoc-start-screen">
      <div className="assoc-info-card">
        <h2 className="assoc-info-title">Командное лобби</h2>
        <p className="assoc-info-description">Игра: {lobby.gameTitle}</p>
        <div className="assoc-info-block">
          <h3 className="assoc-info-block-title">Участники команды ({lobby.members?.length || 0})</h3>
          <ul className="assoc-team-list">
            {lobby.members?.map((m) => (
              <li key={m.userId} className="assoc-team-member">
                <span>{m.username}</span>
                {m.role === 'ADMIN' && <span className="assoc-team-role">админ</span>}
              </li>
            ))}
          </ul>
        </div>
        <p className="assoc-info-description">Игра начнётся, когда любой участник нажмёт «Начать игру»</p>
        <div className="assoc-info-actions">
          <button type="button" className="btn btn-primary assoc-info-start" onClick={handleStart} disabled={starting}>
            {starting ? 'Запускаем…' : 'Начать игру'}
          </button>
        </div>
      </div>
    </div>
  )
}
