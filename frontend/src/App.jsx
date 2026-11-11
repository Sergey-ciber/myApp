import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import { trackPageview } from './metrika'
import Navbar from './components/Navbar'
import Login from './pages/Login'
import Register from './pages/Register'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import Boards from './pages/Boards'
import BoardDetail from './pages/BoardDetail'
import Profile from './pages/Profile'
import Associations from './pages/Associations'
import Teams from './pages/Teams'
import TeamDetails from './pages/TeamDetails'
import Users from './pages/Users'

function Protected({ children }) {
  const { user, loading } = useAuth()
  if (loading) {
    return <div className="page-loader">Загрузка…</div>
  }
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  const location = useLocation()
  const { loading } = useAuth()
  const isAuthRoute = ['/login', '/register', '/forgot-password', '/reset-password']
    .includes(location.pathname)

  // SPA: при смене маршрута отправляем хит в Метрику вручную.
  // Ждём окончания проверки авторизации, иначе хит уйдёт на промежуточный
  // URL, с которого пользователя сейчас перенаправит на /login.
  useEffect(() => {
    if (loading) return
    trackPageview()
  }, [loading, location.pathname, location.search])

  return (
    <div className="app">
      {!isAuthRoute && <Navbar />}
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/" element={<Navigate to="/associations" replace />} />
        {/*<Route path="/boards/:id" element={<Protected><BoardDetail /></Protected>} />*/}
        <Route path="/associations" element={<Protected><Associations /></Protected>} />
        <Route path="/teams" element={<Protected><Teams /></Protected>} />
        <Route path="/teams/:id" element={<Protected><TeamDetails /></Protected>} />
        <Route path="/users" element={<Protected><Users /></Protected>} />
        <Route path="/profile" element={<Protected><Profile /></Protected>} />
        <Route path="*" element={<Navigate to="/boards" replace />} />
      </Routes>
    </div>
  )
  // return (
  //     <div className="app">
  //       {!isAuthRoute && <Navbar />}
  //       <Routes>
  //         <Route path="/login" element={<Login />} />
  //         <Route path="/register" element={<Register />} />
  //         <Route path="/" element={<Navigate to="/boards" replace />} />
  //         <Route path="/boards" element={<Protected><Boards /></Protected>} />
  //         <Route path="/boards/:id" element={<Protected><BoardDetail /></Protected>} />
  //         <Route path="/associations" element={<Protected><Associations /></Protected>} />
  //         <Route path="/teams" element={<Protected><Teams /></Protected>} />
  //         <Route path="/teams/:id" element={<Protected><TeamDetails /></Protected>} />
  //         <Route path="/profile" element={<Protected><Profile /></Protected>} />
  //         <Route path="*" element={<Navigate to="/boards" replace />} />
  //       </Routes>
  //     </div>
  // )
}
