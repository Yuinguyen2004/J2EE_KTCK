import { useState } from 'react'
import { useAuth } from './context/useAuth'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import FBManagement from './pages/FBManagement'
import TableManagement from './pages/TableManagement'
import RevenueAnalytics from './pages/RevenueAnalytics'
import StaffMenu from './pages/StaffMenu'
import StaffTableOperations from './pages/StaffTableOperations'
import StaffReservations from './pages/StaffReservations'
import CrmManagement from './pages/CrmManagement'
import CustomerDashboard from './pages/CustomerDashboard'
import CustomerMenu from './pages/CustomerMenu'
import CustomerReservations from './pages/CustomerReservations'
import CustomerChat from './pages/CustomerChat'
import StaffChatInbox from './pages/StaffChatInbox'
import MyProfile from './pages/MyProfile'
import Notifications from './pages/Notifications'
import { getDefaultPageForRole, isPageAllowedForRole, type AppPage } from './utils/navigation'
import './App.css'

type Page = 'login' | 'register' | AppPage

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
    if (user && isPageAllowedForRole(user.role, page)) {
      setCurrentPage(page)
    }
  }

  if (!isAuthenticated || !user) {
    return currentPage === 'register'
      ? <Register onSuccess={() => setCurrentPage('login')} onGoToLogin={() => setCurrentPage('login')} />
      : <Login onSuccess={() => setCurrentPage('login')} onGoToRegister={() => setCurrentPage('register')} />
  }

  const resolvedPage = currentPage === 'login' || !isPageAllowedForRole(user.role, currentPage)
    ? getDefaultPageForRole(user.role)
    : currentPage

  return (
    <div className="app-container">
      {resolvedPage === 'dashboard' && (
        <Dashboard onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'reservations' && (
        <StaffReservations onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
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
      {resolvedPage === 'staff-chat' && (
        <StaffChatInbox onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'crm' && (
        <CrmManagement onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'customer-dashboard' && (
        <CustomerDashboard onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'customer-menu' && (
        <CustomerMenu onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'customer-reservations' && (
        <CustomerReservations onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
      )}
      {resolvedPage === 'customer-chat' && (
        <CustomerChat onLogout={handleLogout} onNavigate={handleNavigate} user={user} />
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
