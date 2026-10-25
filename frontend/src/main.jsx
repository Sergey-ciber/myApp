import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { InvitationsProvider } from './context/InvitationsContext'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <InvitationsProvider>
          <App />
        </InvitationsProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
