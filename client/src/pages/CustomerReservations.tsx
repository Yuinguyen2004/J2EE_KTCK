import { useEffect, useMemo, useState } from 'react'
import MainLayout from '../components/MainLayout'
import { customerPortalService, type CustomerReservation, type CustomerTable, type PricingPreview } from '../services/customerPortalService'
import type { User } from '../services/authService'
import { formatCurrency } from '../utils/formatCurrency'
import type { AppPage } from '../utils/navigation'
import '../styles/customer-portal.css'

interface CustomerReservationsProps {
  onLogout?: () => void
  onNavigate?: (page: AppPage) => void
  user?: User | null
}

const toDateTimeLocalValue = (isoValue: string) => {
  const date = new Date(isoValue)
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return localDate.toISOString().slice(0, 16)
}

export default function CustomerReservations({
  onLogout = () => {},
  onNavigate = () => {},
  user,
}: CustomerReservationsProps) {
  const [reservations, setReservations] = useState<CustomerReservation[]>([])
  const [tables, setTables] = useState<CustomerTable[]>([])
  const [loading, setLoading] = useState(true)
  const [editingReservationId, setEditingReservationId] = useState<string | null>(null)
  const [tableTypeId, setTableTypeId] = useState('')
  const [reservedFrom, setReservedFrom] = useState('')
  const [reservedTo, setReservedTo] = useState('')
  const [partySize, setPartySize] = useState('2')
  const [notes, setNotes] = useState('')
  const [preview, setPreview] = useState<PricingPreview | null>(null)
  const [previewError, setPreviewError] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  const loadData = async () => {
    const [nextReservations, nextTables] = await Promise.all([
      customerPortalService.getReservations(),
      customerPortalService.getTables(),
    ])

    setReservations(nextReservations)
    setTables(nextTables)
  }

  useEffect(() => {
    let cancelled = false

    void loadData()
      .catch((error) => {
        console.error('Failed to load reservations:', error)
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

  const tableTypes = useMemo(() => {
    const byId = new Map<string, { id: string; name: string }>()
    tables.forEach((table) => {
      if (!byId.has(table.tableTypeId)) {
        byId.set(table.tableTypeId, {
          id: table.tableTypeId,
          name: table.tableTypeName,
        })
      }
    })
    return Array.from(byId.values())
  }, [tables])

  const durationMinutes = useMemo(() => {
    if (!reservedFrom || !reservedTo) {
      return 0
    }

    const start = new Date(reservedFrom).getTime()
    const end = new Date(reservedTo).getTime()
    return Number.isFinite(start) && Number.isFinite(end) && end > start
      ? Math.round((end - start) / 60000)
      : 0
  }, [reservedFrom, reservedTo])

  useEffect(() => {
    if (!tableTypeId || durationMinutes <= 0) {
      setPreview(null)
      setPreviewError('')
      return
    }

    let cancelled = false

    void customerPortalService.getPricingPreview(tableTypeId, durationMinutes)
      .then((nextPreview) => {
        if (!cancelled) {
          setPreview(nextPreview)
          setPreviewError('')
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setPreview(null)
          setPreviewError(error instanceof Error ? error.message : 'Price preview is unavailable right now.')
        }
      })

    return () => {
      cancelled = true
    }
  }, [durationMinutes, tableTypeId])

  const resetForm = () => {
    setEditingReservationId(null)
    setTableTypeId('')
    setReservedFrom('')
    setReservedTo('')
    setPartySize('2')
    setNotes('')
    setPreview(null)
    setPreviewError('')
    setFormError('')
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()

    const start = reservedFrom ? new Date(reservedFrom) : null
    const end = reservedTo ? new Date(reservedTo) : null
    if (!start || !end || Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      setFormError('Choose a valid reservation window.')
      return
    }

    setSaving(true)
    setFormError('')

    try {
      const draft = {
        reservedFrom: start.toISOString(),
        reservedTo: end.toISOString(),
        partySize: Number(partySize),
        notes,
      }

      if (editingReservationId) {
        await customerPortalService.updateReservation(editingReservationId, draft)
      } else {
        await customerPortalService.createReservation(draft)
      }

      await loadData()
      resetForm()
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Reservation request failed.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <MainLayout
      currentPage="customer-reservations"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'Customer'}
      userRole={user?.role || 'customer'}
    >
      <div className="portal-stack portal-two-column">
        <section className="portal-panel">
          <div className="portal-section-header">
            <div>
              <p className="portal-eyebrow">Reservations</p>
              <h1>{editingReservationId ? 'Update your request' : 'Request a reservation'}</h1>
              <p className="portal-muted">
                Choose your time slot and party size. Table type is used only for the estimate; staff assigns the real table after review.
              </p>
            </div>
          </div>

          <form className="portal-form" onSubmit={handleSubmit}>
            <label className="portal-field">
              <span>Estimate table type</span>
              <select value={tableTypeId} onChange={(event) => setTableTypeId(event.target.value)}>
                <option value="">Select a table type for price preview</option>
                {tableTypes.map((tableType) => (
                  <option key={tableType.id} value={tableType.id}>{tableType.name}</option>
                ))}
              </select>
            </label>

            <label className="portal-field">
              <span>Start time</span>
              <input type="datetime-local" value={reservedFrom} onChange={(event) => setReservedFrom(event.target.value)} />
            </label>

            <label className="portal-field">
              <span>End time</span>
              <input type="datetime-local" value={reservedTo} onChange={(event) => setReservedTo(event.target.value)} />
            </label>

            <label className="portal-field">
              <span>Party size</span>
              <input type="number" min="1" value={partySize} onChange={(event) => setPartySize(event.target.value)} />
            </label>

            <label className="portal-field portal-field-full">
              <span>Notes</span>
              <textarea value={notes} onChange={(event) => setNotes(event.target.value)} rows={4} placeholder="Share arrival details, preferred atmosphere, or other requests." />
            </label>

            {formError && <div className="portal-error">{formError}</div>}

            <div className="portal-actions">
              <button type="submit" className="portal-button portal-button-primary" disabled={saving}>
                {saving ? 'Saving...' : editingReservationId ? 'Update Request' : 'Send Request'}
              </button>
              {(editingReservationId || reservedFrom || reservedTo || notes || tableTypeId) && (
                <button type="button" className="portal-button portal-button-secondary" onClick={resetForm}>
                  Clear Form
                </button>
              )}
            </div>
          </form>

          <div className="portal-preview-card">
            <h3>Estimated Pricing</h3>
            {preview ? (
              <>
                <div className="portal-preview-row">
                  <span>Table type</span>
                  <strong>{preview.tableTypeName}</strong>
                </div>
                <div className="portal-preview-row">
                  <span>Duration</span>
                  <strong>{preview.durationMinutes} minutes</strong>
                </div>
                <div className="portal-preview-row">
                  <span>Gross amount</span>
                  <strong>{formatCurrency(preview.grossAmount)}</strong>
                </div>
                <div className="portal-preview-row">
                  <span>Discount</span>
                  <strong>
                    {formatCurrency(preview.discountAmount)}
                    {preview.membershipTierName ? ` (${preview.membershipTierName})` : ''}
                  </strong>
                </div>
                <div className="portal-preview-row portal-preview-total">
                  <span>Estimated total</span>
                  <strong>{formatCurrency(preview.estimatedTotal)}</strong>
                </div>
              </>
            ) : (
              <p className="portal-muted">
                Select a table type plus a valid start and end time to preview the reservation cost.
              </p>
            )}
            {previewError && <div className="portal-error">{previewError}</div>}
          </div>
        </section>

        <section className="portal-panel">
          <div className="portal-section-header">
            <div>
              <h2>My Requests</h2>
              <p className="portal-muted">Pending requests can still be edited or cancelled until staff confirms them.</p>
            </div>
          </div>

          {loading ? (
            <div className="portal-empty-state">Loading reservations...</div>
          ) : reservations.length === 0 ? (
            <div className="portal-empty-state">No reservation requests yet.</div>
          ) : (
            <div className="portal-list">
              {reservations.map((reservation) => (
                <article key={reservation.id} className="portal-list-card">
                  <div className="portal-list-header">
                    <div>
                      <h3>{reservation.tableName || 'Awaiting table assignment'}</h3>
                      <p className="portal-muted">
                        {new Date(reservation.reservedFrom).toLocaleString()} to {new Date(reservation.reservedTo).toLocaleString()}
                      </p>
                    </div>
                    <span className={`portal-status status-${reservation.status.toLowerCase()}`}>{reservation.status}</span>
                  </div>
                  <p className="portal-muted">Party size: {reservation.partySize}</p>
                  {reservation.notes && <p className="portal-note">{reservation.notes}</p>}
                  {reservation.status === 'PENDING' && (
                    <div className="portal-actions">
                      <button
                        type="button"
                        className="portal-button portal-button-secondary"
                        onClick={() => {
                          setEditingReservationId(reservation.id)
                          setReservedFrom(toDateTimeLocalValue(reservation.reservedFrom))
                          setReservedTo(toDateTimeLocalValue(reservation.reservedTo))
                          setPartySize(String(reservation.partySize))
                          setNotes(reservation.notes || '')
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="portal-button portal-button-danger"
                        onClick={() => {
                          void customerPortalService.cancelReservation(reservation.id)
                            .then(loadData)
                            .catch((error) => {
                              setFormError(error instanceof Error ? error.message : 'Could not cancel the reservation.')
                            })
                        }}
                      >
                        Cancel
                      </button>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </MainLayout>
  )
}
