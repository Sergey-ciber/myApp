import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import Navbar from './components/Navbar'
import Login from './pages/Login'
import Register from './pages/Register'
import Boards from './pages/Boards'
import BoardDetail from './pages/BoardDetail'
import Profile from './pages/Profile'
import Associations from './pages/Associations'
import Teams from './pages/Teams'
import TeamDetails from './pages/TeamDetails'

function Protected({ children }) {
  const { user, loading } = useAuth()
  if (loading) {
    return <div className="page-loader">Загрузка…</div>
  }
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <div className="app">
      <Navbar />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/" element={<Navigate to="/boards" replace />} />
        <Route path="/boards" element={<Protected><Boards /></Protected>} />
        <Route path="/boards/:id" element={<Protected><BoardDetail /></Protected>} />
        <Route path="/associations" element={<Protected><Associations /></Protected>} />
        <Route path="/teams" element={<Protected><Teams /></Protected>} />
        <Route path="/teams/:id" element={<Protected><TeamDetails /></Protected>} />
        <Route path="/profile" element={<Protected><Profile /></Protected>} />
        <Route path="*" element={<Navigate to="/boards" replace />} />
      </Routes>
    </div>
  )
}
