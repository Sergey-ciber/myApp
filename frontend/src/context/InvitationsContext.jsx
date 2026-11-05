import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { acceptInvitation, declineInvitation, fetchInvitations } from '../api/teams'
import { useAuth } from './AuthContext'

const InvitationsContext = createContext(null)

const WS_URL = import.meta.env.VITE_WS_URL || '/ws'
/** Канал пользователя в WebSocket: /topic/user_invitations_{userId}. */
const invitationTopic = (userId) => `/topic/user_invitations_${userId}`

/**
 * Входящие приглашения в команды: общий стейт для колокольчика в шапке
 * и панели приглашений на странице «Команды».
 *
 * Новые приглашения приходят мгновенно через WebSocket (STOMP/SockJS).
 * Опрос по таймеру остался как страховка на случай обрыва соединения.
 */
export function InvitationsProvider({ children }) {
  const { user } = useAuth()
  const [invitations, setInvitations] = useState([])
  const userId = user?.id

  const refresh = useCallback(async () => {
    if (!userId) {
      setInvitations([])
      return []
    }
    try {
      const data = await fetchInvitations()
      setInvitations(data)
      return data
    } catch {
      return []
    }
  }, [userId])

  useEffect(() => {
    if (!userId) {
      setInvitations([])
      return undefined
    }
    refresh()
    // приглашения могут прийти, пока пользователь работает на другой странице
    const timer = setInterval(refresh, 30000)
    const onFocus = () => refresh()
    window.addEventListener('focus', onFocus)
    return () => {
      clearInterval(timer)
      window.removeEventListener('focus', onFocus)
    }
  }, [userId, refresh])

  // живое обновление: новое приглашение приходит по WebSocket без перезагрузки страницы
  const stompRef = useRef(null)
  useEffect(() => {
    if (!userId) return undefined

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      // JWT передаём в заголовке CONNECT — сервер проверяет его и разрешает
      // подписку только на персональный канал текущего пользователя
      connectHeaders: {
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
      onConnect: () => {
        stompClient.subscribe(invitationTopic(userId), (message) => {
          try {
            const payload = JSON.parse(message.body)
            if (payload.type === 'INVITATION_RECEIVED' && payload.invitation) {
              // счётчик и список обновляем сразу, не дожидаясь ответа сервера
              setInvitations((current) => {
                if (current.some((item) => item.id === payload.invitation.id)) return current
                return [payload.invitation, ...current]
              })
            }
          } catch {
            // при некорректном сообщении просто перезапрашиваем список
            refresh()
          }
        })
      },
    })
    stompClient.activate()
    stompRef.current = stompClient
    return () => {
      stompRef.current = null
      stompClient.deactivate()
    }
  }, [userId, refresh])

  const accept = async (id) => {
    const result = await acceptInvitation(id)
    setInvitations((current) => current.filter((item) => item.id !== id))
    return result
  }

  const decline = async (id) => {
    const result = await declineInvitation(id)
    setInvitations((current) => current.filter((item) => item.id !== id))
    return result
  }

  return (
    <InvitationsContext.Provider
      value={{ invitations, count: invitations.length, refresh, accept, decline }}
    >
      {children}
    </InvitationsContext.Provider>
  )
}

export function useInvitations() {
  return useContext(InvitationsContext)
}
