import { useEffect, useState } from 'react'
import MainLayout from '../components/MainLayout'
import TableGrid from '../components/TableGrid'
import { customerPortalService, type CustomerTable } from '../services/customerPortalService'
import type { User } from '../services/authService'
import type { AppPage } from '../utils/navigation'
import '../styles/customer-portal.css'

interface CustomerDashboardProps {
  onLogout?: () => void
  onNavigate?: (page: AppPage) => void
  user?: User | null
}

export default function CustomerDashboard({
  onLogout = () => {},
  onNavigate = () => {},
  user,
}: CustomerDashboardProps) {
  const [tables, setTables] = useState<CustomerTable[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    const loadTables = async () => {
      try {
        const nextTables = await customerPortalService.getTables()
        if (!cancelled) {
          setTables(nextTables)
        }
      } catch (error) {
        console.error('Failed to load customer dashboard:', error)
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadTables()
    const intervalId = window.setInterval(() => {
      void loadTables()
    }, 30000)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [])

  const gridTables = tables.map((table) => ({
    id: table.id,
    name: table.name,
    type: table.tableTypeName,
    pricePerHour: table.pricePerHour,
    status: table.status,
  }))

  const stats = [
    { label: 'Available Tables', value: tables.filter((table) => table.status === 'available').length },
    { label: 'Reserved Slots', value: tables.filter((table) => table.status === 'reserved').length },
    { label: 'Playing Now', value: tables.filter((table) => table.status === 'playing').length },
    { label: 'Table Types', value: new Set(tables.map((table) => table.tableTypeId)).size },
  ]

  return (
    <MainLayout
      currentPage="customer-dashboard"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'Customer'}
      userRole={user?.role || 'customer'}
    >
      <div className="portal-stack">
        <section className="portal-hero">
          <div>
            <p className="portal-eyebrow">Customer Portal</p>
            <h1>Plan your visit before you arrive</h1>
            <p className="portal-muted">
              Check live table availability, preview pricing, browse the menu, and send your request to the shop.
            </p>
          </div>
          <div className="portal-actions">
            <button type="button" className="portal-button portal-button-primary" onClick={() => onNavigate('customer-reservations')}>
              Request Reservation
            </button>
            <button type="button" className="portal-button portal-button-secondary" onClick={() => onNavigate('customer-chat')}>
              Chat With The Shop
            </button>
          </div>
        </section>

        <section className="portal-stats-grid">
          {stats.map((stat) => (
            <article key={stat.label} className="portal-stat-card">
              <span className="portal-stat-label">{stat.label}</span>
              <strong className="portal-stat-value">{stat.value}</strong>
            </article>
          ))}
        </section>

        <section className="portal-panel">
          <div className="portal-section-header">
            <div>
              <h2>Live Table Availability</h2>
              <p className="portal-muted">
                Status and pricing help you decide when to book. Staff will assign the exact table after approval.
              </p>
            </div>
          </div>

          {loading ? (
            <div className="portal-empty-state">Loading table availability...</div>
          ) : (
            <TableGrid tables={gridTables} interactive={false} />
          )}
        </section>
      </div>
    </MainLayout>
  )
}
