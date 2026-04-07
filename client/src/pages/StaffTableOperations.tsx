import React, { useEffect, useEffectEvent, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import TableActionModal from '../components/TableActionModal';
import TableGrid, { type TableData } from '../components/TableGrid';
import { tableService, type Table } from '../services/tableService';
import { sessionService, type Session } from '../services/sessionService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/management.css';
import '../styles/dashboard.css';

interface StaffTableOperationsProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

const formatElapsedTime = (startTime?: Date, now = Date.now()) => {
  if (!startTime) {
    return 'Waiting for service';
  }

  const elapsedMs = Math.max(now - startTime.getTime(), 0);
  const totalSeconds = Math.floor(elapsedMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }

  return `${minutes}m ${seconds}s`;
};

const estimateBill = (startTime: Date | undefined, pricePerHour: number | undefined, now = Date.now()) => {
  if (!startTime) {
    return 0;
  }

  const elapsedMs = Math.max(now - startTime.getTime(), 0);
  return pricePerHour ? (elapsedMs / 3600000) * pricePerHour : 0;
};

export const StaffTableOperations: React.FC<StaffTableOperationsProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const [tables, setTables] = useState<Table[]>([]);
  const [activeSessions, setActiveSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [selectedTable, setSelectedTable] = useState<TableData | null>(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setNow(Date.now());
    }, 1000);

    return () => window.clearInterval(intervalId);
  }, []);

  const loadOperationsData = useEffectEvent(async () => {
    try {
      const tablesData = await tableService.getAll({ includePricing: true });
      const sessionsData = await Promise.all(
        tablesData.map((table) => sessionService.getByTable(table._id, { tableStatus: table.status }))
      );

      setTables(tablesData);
      setActiveSessions(sessionsData.filter((session): session is Session => session !== null));
    } catch (error) {
      console.error('Failed to load staff table operations:', error);
    } finally {
      setLoading(false);
    }
  });

  useEffect(() => {
    void loadOperationsData();
  }, [refreshVersion]);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setRefreshVersion((current) => current + 1);
    }, 30000);

    return () => {
      window.clearInterval(intervalId);
    };
  }, []);

  const tableOperations = useMemo<TableData[]>(() => {
    return tables.map((table) => {
      const session = activeSessions.find((activeSession) => activeSession.tableId === table._id);

      return {
        id: table._id,
        name: table.name,
        type: table.type,
        pricePerHour: table.pricePerHour,
        currentTotal: session?.totalAmount,
        status: table.status,
        startTime: session ? new Date(session.startTime) : undefined,
        sessionId: session?._id,
      };
    });
  }, [activeSessions, tables]);

  const filteredTables = tableOperations.filter((table) => {
    const query = searchQuery.toLowerCase();
    return (
      table.name.toLowerCase().includes(query) ||
      table.type.toLowerCase().includes(query) ||
      table.status.toLowerCase().includes(query)
    );
  });

  const activeTableCount = tableOperations.filter((table) => table.status === 'playing').length;
  const availableTableCount = tableOperations.filter((table) => table.status === 'available').length;
  const maintenanceTableCount = tableOperations.filter((table) => table.status === 'maintenance').length;

  if (loading) {
    return (
      <MainLayout
        currentPage="staff-tables"
        onNavigate={onNavigate}
        onLogout={onLogout}
        user={user}
        userName={user?.fullName || 'User'}
        userRole={user?.role || 'staff'}
      >
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>Loading service floor...</div>
      </MainLayout>
    );
  }

  return (
    <MainLayout
      currentPage="staff-tables"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="management-container">
        <div className="management-header">
          <div>
            <h1>Table Service</h1>
            <p>Run live tables, open sessions, and complete checkout without admin-only setup actions.</p>
          </div>
          <div className="read-only-note">Admin controls table setup and pricing</div>
        </div>

        <div className="search-section">
          <div className="search-input-wrapper">
            <Search size={20} />
            <input
              type="text"
              placeholder="Search by table name, type, or status..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="search-input"
            />
          </div>
          <div className="filter-chips">
            <span className="filter-chip">Open Sessions: {activeTableCount}</span>
            <span className="filter-chip">Ready: {availableTableCount}</span>
            <span className="filter-chip">Maintenance: {maintenanceTableCount}</span>
          </div>
        </div>

        <div className="data-table staff-ops-table">
          <div className="table-header">
            <div className="header-cell">Table</div>
            <div className="header-cell">Status</div>
            <div className="header-cell">Rate</div>
            <div className="header-cell">Session</div>
            <div className="header-cell">Action</div>
          </div>

          {filteredTables.length > 0 ? (
            <div className="table-body">
              {filteredTables.map((table) => {
                const sessionLabel = table.status === 'playing'
                  ? formatElapsedTime(table.startTime, now)
                  : table.status === 'maintenance'
                    ? 'Unavailable for play'
                    : 'Ready to start';

                const sessionValue = table.status === 'playing'
                  ? formatCurrency(
                    typeof table.pricePerHour === 'number'
                      ? estimateBill(table.startTime, table.pricePerHour, now)
                      : table.currentTotal || 0
                  )
                  : table.sessionId
                    ? 'Session attached'
                    : 'No active session';

                return (
                  <div key={table.id} className="table-row">
                    <div className="cell">
                      <div>
                        <span className="cell-text">{table.name}</span>
                        <div className="cell-subtext">{table.type.toUpperCase()}</div>
                      </div>
                    </div>
                    <div className="cell">
                      <span className={`status-badge status-${table.status}`}>
                        {table.status === 'available'
                          ? 'Available'
                          : table.status === 'playing'
                            ? 'In Use'
                            : 'Maintenance'}
                      </span>
                    </div>
                    <div className="cell">
                      <span className="price">
                        {typeof table.pricePerHour === 'number' ? formatCurrency(table.pricePerHour) : 'Managed by pricing rules'}
                      </span>
                    </div>
                    <div className="cell">
                      <div className="session-meta">
                        <span>{sessionLabel}</span>
                        <span className="cell-subtext">{sessionValue}</span>
                      </div>
                    </div>
                    <div className="cell actions">
                      <button className="action-save" onClick={() => setSelectedTable(table)}>
                        Manage
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="empty-state">
              <div className="empty-text">No tables found matching your search.</div>
            </div>
          )}
        </div>

        <div className="dashboard-card full-width">
          <h2>Live Service Floor</h2>
          <TableGrid
            tables={filteredTables}
            onTableUpdate={() => setRefreshVersion((current) => current + 1)}
          />
        </div>

        <div className="stats-footer">
          <div className="stat">
            <span className="stat-label">Total Tables</span>
            <span className="stat-value">{tableOperations.length}</span>
          </div>
          <div className="stat">
            <span className="stat-label">In Service</span>
            <span className="stat-value">{activeTableCount}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Ready Now</span>
            <span className="stat-value">{availableTableCount}</span>
          </div>
        </div>
      </div>

      {selectedTable && (
        <TableActionModal
          isOpen={true}
          onClose={() => setSelectedTable(null)}
          tableId={selectedTable.id}
          tableName={selectedTable.name}
          tableStatus={selectedTable.status}
          pricePerHour={selectedTable.pricePerHour}
          sessionId={selectedTable.sessionId}
          elapsedTime={formatElapsedTime(selectedTable.startTime, now)}
          billAmount={
            typeof selectedTable.pricePerHour === 'number'
              ? estimateBill(selectedTable.startTime, selectedTable.pricePerHour, now)
              : selectedTable.currentTotal || 0
          }
          onTableUpdate={() => {
            setSelectedTable(null);
            setRefreshVersion((current) => current + 1);
          }}
        />
      )}
    </MainLayout>
  );
};

export default StaffTableOperations;
