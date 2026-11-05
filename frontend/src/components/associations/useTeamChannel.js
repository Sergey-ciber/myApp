import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WS_URL = import.meta.env.VITE_WS_URL || '/ws'

/**
 * Подписка на события команд: /topic/team_{teamId}.
 *
 * Когда любой участник команды запускает игру, приходит событие STARTED —
 * все остальные участники автоматически переходят в командный режим
 * и открывают ту же партию.
 *
 * @param {number[]} teamIds — id команд, в которых состоит пользователь
 * @param {(state: object, teamId: number, type: string) => void} onEvent — обработчик событий
 */
export default function useTeamChannel(teamIds, onEvent) {
  const handlerRef = useRef(onEvent)
  handlerRef.current = onEvent

  const key = (teamIds || []).join(',')
  const ids = key ? key.split(',').map(Number).filter((id) => Number.isFinite(id) && id > 0) : []

  useEffect(() => {
    if (ids.length === 0) return undefined

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      // JWT передаём в заголовке CONNECT — сервер проверяет права на канал
      connectHeaders: {
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
      onConnect: () => {
        ids.forEach((teamId) => {
          stompClient.subscribe(`/topic/team_${teamId}`, (message) => {
            try {
              const payload = JSON.parse(message.body)
              handlerRef.current?.(payload.state, teamId, payload.type)
            } catch {
              // некорректное сообщение игнорируем
            }
          })
        })
      },
    })
    stompClient.activate()
    return () => {
      stompClient.deactivate()
    }
  }, [key])
}
