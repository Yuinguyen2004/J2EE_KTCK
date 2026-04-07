import { useEffect, useState } from 'react'
import MainLayout from '../components/MainLayout'
import { customerPortalService, type CustomerMenuItem } from '../services/customerPortalService'
import type { User } from '../services/authService'
import { formatCurrency } from '../utils/formatCurrency'
import type { AppPage } from '../utils/navigation'
import '../styles/customer-portal.css'

interface CustomerMenuProps {
  onLogout?: () => void
  onNavigate?: (page: AppPage) => void
  user?: User | null
}

export default function CustomerMenu({
  onLogout = () => {},
  onNavigate = () => {},
  user,
}: CustomerMenuProps) {
  const [items, setItems] = useState<CustomerMenuItem[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    void customerPortalService.getMenuItems()
      .then((nextItems) => {
        if (!cancelled) {
          setItems(nextItems)
        }
      })
      .catch((error) => {
        console.error('Failed to load customer menu:', error)
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <MainLayout
      currentPage="customer-menu"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'Customer'}
      userRole={user?.role || 'customer'}
    >
      <div className="portal-stack">
        <section className="portal-panel">
          <div className="portal-section-header">
            <div>
              <p className="portal-eyebrow">Menu</p>
              <h1>Browse what the shop is serving</h1>
              <p className="portal-muted">
                This is the live customer menu. Availability is managed by staff on the operations side.
              </p>
            </div>
          </div>

          {loading ? (
            <div className="portal-empty-state">Loading menu...</div>
          ) : (
            <div className="portal-card-grid">
              {items.map((item) => (
                <article key={item.id} className="portal-card">
                  {item.imageUrl && (
                    <img src={item.imageUrl} alt={item.name} className="portal-card-image" />
                  )}
                  <div className="portal-card-body">
                    <div className="portal-card-heading">
                      <h3>{item.name}</h3>
                      <strong>{formatCurrency(item.price)}</strong>
                    </div>
                    <p className="portal-muted">{item.description || 'Freshly prepared for your table.'}</p>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </MainLayout>
  )
}
