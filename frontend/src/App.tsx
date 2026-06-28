import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import Layout from './components/Layout'
import AuthPage from './pages/AuthPage'
import DashboardPage from './pages/DashboardPage'
import DocumentsPage from './pages/DocumentsPage'
import ChatPage from './pages/ChatPage'
import AdminPage from './pages/AdminPage'
import { WorkspaceProvider } from './context/WorkspaceContext'

function Protected() {
  const { user } = useAuth()
  return user
    ? <WorkspaceProvider><Layout><Outlet /></Layout></WorkspaceProvider>
    : <Navigate to="/login" replace />
}

function AdminOnly() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <AdminPage /> : <Navigate to="/" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage />} />
      <Route element={<Protected />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/admin" element={<AdminOnly />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
