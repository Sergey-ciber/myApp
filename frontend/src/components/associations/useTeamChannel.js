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
      // Heartbeat: иначе оборванное соединение выглядит рабочим и события
      // команды (STARTED) просто не доходят до игрока.
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
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
      // Отказ в подписке (например, истёк JWT) закрывает STOMP-сессию:
      // без этих обработчиков причина видна только в консоли браузера.
      onStompError: (frame) => {
        console.error('[team-channel] STOMP error:', frame.headers?.message, frame.body)
      },
      onWebSocketError: (event) => {
        console.error('[team-channel] WebSocket error:', event)
      },
      onWebSocketClose: (event) => {
        console.warn('[team-channel] WebSocket closed, reconnecting…', event?.code, event?.reason)
      },
    })
    stompClient.activate()
    return () => {
      stompClient.deactivate()
    }
  }, [key])
}
