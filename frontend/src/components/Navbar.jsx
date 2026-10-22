import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

function initials(user) {
  const name = user?.fullName || user?.username || '?'
  return name.slice(0, 2).toUpperCase()
}

export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="navbar">
      <Link to="/boards" className="navbar-brand">
        🗂 Task Manager
      </Link>
      {user && (
        <div className="navbar-user">
          <Link to="/profile" className="navbar-profile">
            {user.avatarUrl
              ? <img src={user.avatarUrl} alt="avatar" className="avatar avatar-sm" />
              : <span className="avatar avatar-sm avatar-fallback">{initials(user)}</span>}
            <span className="navbar-username">{user.fullName || user.username}</span>
          </Link>
          <button className="btn btn-ghost" onClick={handleLogout}>Выйти</button>
        </div>
      )}
    </header>
  )
}
