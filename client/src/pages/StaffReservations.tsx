import React, { useEffect, useEffectEvent, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { getErrorMessage } from '../services/error';
import { reservationService, type Reservation, type ReservationStatus } from '../services/reservationService';
import { tableService, type Table } from '../services/tableService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import '../styles/management.css';

interface StaffReservationsProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

type ReservationFilter = ReservationStatus | 'ALL';

const FILTER_OPTIONS: { id: ReservationFilter; label: string }[] = [
  { id: 'PENDING', label: 'Pending' },
  { id: 'CONFIRMED', label: 'Confirmed' },
  { id: 'CHECKED_IN', label: 'Checked In' },
  { id: 'COMPLETED', label: 'Completed' },
  { id: 'CANCELLED', label: 'Cancelled' },
  { id: 'ALL', label: 'All' },
];

const formatDateRange = (from: string, to: string) =>
  `${new Date(from).toLocaleString()} to ${new Date(to).toLocaleString()}`;

const statusLabel = (status: ReservationStatus) => {
  switch (status) {
    case 'CHECKED_IN':
      return 'Checked In';
    default:
      return `${status.charAt(0)}${status.slice(1).toLowerCase()}`;
  }
};

const statusClassName = (status: ReservationStatus) => status.toLowerCase().replace('_', '-');

const tableStatusLabel = (status: Table['status']) => {
  switch (status) {
    case 'playing':
      return 'In Use';
    case 'reserved':
      return 'Reserved';
    case 'maintenance':
      return 'Maintenance';
    default:
      return 'Available';
  }
};

export const StaffReservations: React.FC<StaffReservationsProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [tables, setTables] = useState<Table[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState<ReservationFilter>('PENDING');
  const [assignments, setAssignments] = useState<Record<string, string>>({});
  const [approvingId, setApprovingId] = useState<string | null>(null);
  const [error, setError] = useState('');

  const loadReservationData = useEffectEvent(async () => {
    try {
      const [reservationList, tableList] = await Promise.all([
        reservationService.getAll({ sortBy: 'reservedFrom', direction: 'asc' }),
        tableService.getAll(),
      ]);

      setReservations(reservationList);
      setTables(tableList.filter((table) => table.active));
      setAssignments((current) => {
        const next = { ...current };

        reservationList.forEach((reservation) => {
          if (reservation.status === 'PENDING' && reservation.tableId && !next[reservation.id]) {
            next[reservation.id] = reservation.tableId;
          }
        });

        return next;
      });
      setError('');
    } catch (loadError) {
      setError(getErrorMessage(loadError, 'Failed to load reservation requests.'));
    } finally {
      setLoading(false);
    }
  });

  useEffect(() => {
    void loadReservationData();
  }, [refreshVersion]);

  const counts = useMemo(() => {
    return reservations.reduce<Record<ReservationFilter, number>>(
      (summary, reservation) => {
        summary.ALL += 1;
        summary[reservation.status] += 1;
        return summary;
      },
      {
        PENDING: 0,
        CONFIRMED: 0,
        CHECKED_IN: 0,
        COMPLETED: 0,
        CANCELLED: 0,
        ALL: 0,
      },
    );
  }, [reservations]);

  const filteredReservations = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();

    return reservations.filter((reservation) => {
      if (filter !== 'ALL' && reservation.status !== filter) {
        return false;
      }

      if (!normalizedQuery) {
        return true;
      }

      const haystack = [
        reservation.customerName,
        reservation.tableName,
        reservation.staffName,
        reservation.notes,
        reservation.status,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();

      return haystack.includes(normalizedQuery);
    });
  }, [filter, reservations, searchQuery]);

  const activeTables = useMemo(
    () => tables.filter((table) => table.active && table.status !== 'maintenance'),
    [tables],
  );

  const handleApprove = async (reservationId: string) => {
    const tableId = assignments[reservationId];
    if (!tableId) {
      setError('Choose a table before confirming the reservation.');
      return;
    }

    try {
      setApprovingId(reservationId);
      await reservationService.confirm(reservationId, tableId);
      setRefreshVersion((current) => current + 1);
    } catch (confirmError) {
      setError(getErrorMessage(confirmError, 'Failed to confirm the reservation.'));
    } finally {
      setApprovingId(null);
    }
  };

  if (loading) {
    return (
      <MainLayout
        currentPage="reservations"
        onNavigate={onNavigate}
        onLogout={onLogout}
        user={user}
        userName={user?.fullName || 'User'}
        userRole={user?.role || 'staff'}
      >
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>
          Loading reservation approvals...
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout
      currentPage="reservations"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="management-container">
        <div className="management-header">
          <div>
            <h1>Reservation Review</h1>
            <p>Approve customer requests by assigning the real table the floor team will hold for them.</p>
          </div>
          <div className="read-only-note">Customer requests stay editable until you confirm them here</div>
        </div>

        {error && <div className="management-alert management-alert-error">{error}</div>}

        <div className="search-section">
          <div className="search-input-wrapper">
            <Search size={20} />
            <input
              type="text"
              placeholder="Search by customer, table, staff, note, or status..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="search-input"
            />
          </div>
          <div className="filter-chips">
            <span className="filter-chip">Pending: {counts.PENDING}</span>
            <span className="filter-chip">Confirmed: {counts.CONFIRMED}</span>
            <span className="filter-chip">Checked In: {counts.CHECKED_IN}</span>
          </div>
        </div>

        <div className="category-filters">
          {FILTER_OPTIONS.map((option) => (
            <button
              key={option.id}
              type="button"
              className={`filter-chip-button ${filter === option.id ? 'active' : ''}`}
              onClick={() => setFilter(option.id)}
            >
              {option.label} ({counts[option.id]})
            </button>
          ))}
        </div>

        <div className="reservation-approval-summary">
          <div className="reservation-summary-card">
            <span className="reservation-summary-label">Open Requests</span>
            <strong>{counts.PENDING}</strong>
            <p>Requests still waiting for table assignment and staff confirmation.</p>
          </div>
          <div className="reservation-summary-card">
            <span className="reservation-summary-label">Ready Tables</span>
            <strong>{tables.filter((table) => table.status === 'available' && table.active).length}</strong>
            <p>Currently available tables you can assign immediately.</p>
          </div>
          <div className="reservation-summary-card">
            <span className="reservation-summary-label">Reservations Loaded</span>
            <strong>{counts.ALL}</strong>
            <p>Use the filters to move between pending, confirmed, and closed work.</p>
          </div>
        </div>

        <div className="data-table reservation-approval-table">
          <div className="table-header">
            <div className="header-cell">Customer</div>
            <div className="header-cell">Requested Window</div>
            <div className="header-cell">Request Details</div>
            <div className="header-cell">Table Assignment</div>
            <div className="header-cell">Action</div>
          </div>

          {filteredReservations.length > 0 ? (
            <div className="table-body">
              {filteredReservations.map((reservation) => {
                const selectedTableId = assignments[reservation.id] || '';

                return (
                  <div key={reservation.id} className="table-row">
                    <div className="cell reservation-cell-block">
                      <div>
                        <span className="cell-text">{reservation.customerName || 'Walk-in / unknown customer'}</span>
                        <div className="cell-subtext">
                          Reservation #{reservation.id}
                        </div>
                      </div>
                    </div>
                    <div className="cell reservation-cell-block">
                      <div>
                        <span className="cell-text">{formatDateRange(reservation.reservedFrom, reservation.reservedTo)}</span>
                        <div className="cell-subtext">
                          Party size: {reservation.partySize}
                        </div>
                      </div>
                    </div>
                    <div className="cell reservation-cell-block">
                      <div>
                        <span className={`status-badge status-${statusClassName(reservation.status)}`}>
                          {statusLabel(reservation.status)}
                        </span>
                        <div className="cell-subtext">
                          {reservation.notes || 'No request note provided'}
                        </div>
                        <div className="cell-subtext">
                          {reservation.staffName ? `Handled by ${reservation.staffName}` : 'Awaiting staff decision'}
                        </div>
                      </div>
                    </div>
                    <div className="cell reservation-cell-block">
                      {reservation.status === 'PENDING' ? (
                        <select
                          className="edit-input reservation-table-select"
                          value={selectedTableId}
                          onChange={(event) =>
                            setAssignments((current) => ({
                              ...current,
                              [reservation.id]: event.target.value,
                            }))
                          }
                        >
                          <option value="">Assign a table</option>
                          {activeTables.map((table) => (
                            <option key={table._id} value={table._id}>
                              {table.name} · {table.type} · {tableStatusLabel(table.status)}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <div>
                          <span className="cell-text">{reservation.tableName || 'No table assigned'}</span>
                          <div className="cell-subtext">
                            {reservation.tableStatus ? tableStatusLabel(reservation.tableStatus) : 'No table state'}
                          </div>
                        </div>
                      )}
                    </div>
                    <div className="cell actions">
                      {reservation.status === 'PENDING' ? (
                        <button
                          type="button"
                          className="action-save"
                          disabled={!selectedTableId || approvingId === reservation.id}
                          onClick={() => void handleApprove(reservation.id)}
                        >
                          {approvingId === reservation.id ? 'Approving...' : 'Approve'}
                        </button>
                      ) : (
                        <span className="badge">
                          {reservation.checkedInAt ? 'Customer arrived' : 'No action needed'}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="empty-state">
              <div className="empty-text">
                {filter === 'PENDING'
                  ? 'No pending customer requests need approval right now.'
                  : 'No reservations match the current filters.'}
              </div>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
};

export default StaffReservations;
