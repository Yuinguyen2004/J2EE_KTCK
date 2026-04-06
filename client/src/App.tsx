import { useState } from 'react'
import { useAuth } from './context/useAuth'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import FBManagement from './pages/FBManagement'
import TableManagement from './pages/TableManagement'
import RevenueAnalytics from './pages/RevenueAnalytics'
import StaffMenu from './pages/StaffMenu'
import StaffTableOperations from './pages/StaffTableOperations'
import MyProfile from './pages/MyProfile'
import Notifications from './pages/Notifications'
import { getDefaultPageForRole, isOperationalUserRole, isPageAllowedForRole, type AppPage } from './utils/navigation'
import './App.css'

type Page = 'login' | AppPage

function App() {
  const { user, isAuthenticated, isLoading, logout } = useAuth()
  const [currentPage, setCurrentPage] = useState<Page>('login')

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <p>Loading...</p>
      </div>
    )
  }

  const handleLogout = () => {
    void logout()
    setCurrentPage('login')
  }

  const handleNavigate = (page: AppPage) => {
    if (user && isOperationalUserRole(user.role) && isPageAllowedForRole(user.role, page)) {
      setCurrentPage(page)
    }
  }

  if (!isAuthenticated || !user) {
    return <Login onSuccess={() => setCurrentPage('dashboard')} />
  }

  if (!isOperationalUserRole(user.role)) {
    return (
      <div className="auth-container">
        <div className="auth-form-container">
          <div className="auth-form-wrapper">
            <div className="auth-header">
              <h1>Operations Access Only</h1>
              <p className="subtitle">
                This client is for staff and admin workflows. Customer accounts are not supported here.
              </p>
            </div>
            <button type="button" className="button button-primary" onClick={handleLogout}>
              Sign Out
            </button>
          </div>
        </div>
      </div>
    )
  }

  const resolvedPage = currentPage === 'login' || !isPageAllowedForRole(user.role, currentPage)
    ? getDefaultPageForRole(user.role)
    : currentPage

  return (
    <div className="app-container">
      {resolvedPage === 'dashboard' && (
        <Dashboard onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'fb-management' && (
        <FBManagement onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'table-management' && (
        <TableManagement onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'staff-tables' && (
        <StaffTableOperations onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'revenue-reports' && (
        <RevenueAnalytics onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'staff-menu' && (
        <StaffMenu onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'my-profile' && (
        <MyProfile onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'notifications' && (
        <Notifications onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
    </div>
  )
}

export default App
