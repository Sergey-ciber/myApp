import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import InvitationsBell from './InvitationsBell'

function initials(user) {
  const name = user?.fullName || user?.username || '?'
  return name.slice(0, 2).toUpperCase()
}

export default function Navbar() {
  const { user, logout, isSuperAdmin } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="navbar">
      <div className="navbar-left">
        {/*<NavLink*/}
        {/*  to="/boards"*/}
        {/*  className={({ isActive }) => `navbar-link${isActive ? ' is-active' : ''}`}*/}
        {/*>*/}
        {/*  Мои доски*/}
        {/*</NavLink>*/}
        {user && (
          <>
            <NavLink
              to="/games"
              className={({ isActive }) => `navbar-link${isActive ? ' is-active' : ''}`}
            >
              Игры
            </NavLink>
            <NavLink
              to="/teams"
              className={({ isActive }) => `navbar-link${isActive ? ' is-active' : ''}`}
            >
              Команды
            </NavLink>
            {isSuperAdmin && (
              <NavLink
                to="/users"
                className={({ isActive }) => `navbar-link${isActive ? ' is-active' : ''}`}
              >
                Пользователи
              </NavLink>
            )}
          </>
        )}
      </div>
      {user && (
        <div className="navbar-user">
          <InvitationsBell />
          <NavLink
            to="/profile"
            className={({ isActive }) => `navbar-profile${isActive ? ' is-active' : ''}`}
          >
            {user.avatarUrl
              ? <img src={user.avatarUrl} alt="avatar" className="avatar avatar-sm" />
              : <span className="avatar avatar-sm avatar-fallback">{initials(user)}</span>}
            <span className="navbar-username">{user.fullName || user.username}</span>
            {isSuperAdmin && <span className="role-badge role-badge-superadmin">Super Admin</span>}
          </NavLink>
          <button className="btn btn-ghost" onClick={handleLogout}>Выйти</button>
        </div>
      )}
    </header>
  )
}
