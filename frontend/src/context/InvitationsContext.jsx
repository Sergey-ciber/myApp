import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { acceptInvitation, declineInvitation, fetchInvitations } from '../api/teams'
import { useAuth } from './AuthContext'

const InvitationsContext = createContext(null)

/**
 * Входящие приглашения в команды: общий стейт для колокольчика в шапке
 * и панели приглашений на странице «Команды».
 */
export function InvitationsProvider({ children }) {
  const { user } = useAuth()
  const [invitations, setInvitations] = useState([])

  const refresh = useCallback(async () => {
    if (!user) {
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
  }, [user])

  useEffect(() => {
    if (!user) {
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
  }, [user, refresh])

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
