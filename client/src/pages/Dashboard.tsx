import React, { useEffect, useEffectEvent, useState } from 'react';
import MainLayout from '../components/MainLayout';
import TableGrid from '../components/TableGrid';
import { tableService, type Table } from '../services/tableService';
import { sessionService, type Session } from '../services/sessionService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import '../styles/dashboard.css';

interface DashboardProps {
  onLogout?: () => void;
  onNavigate?: (page: AppPage) => void;
  user?: User | null;
}

export const Dashboard: React.FC<DashboardProps> = ({ onLogout = () => {}, onNavigate = () => {}, user }) => {
  const [tables, setTables] = useState<Table[]>([]);
  const [activeSessions, setActiveSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const isAdmin = user?.role === 'admin';

  const loadDashboardData = useEffectEvent(async () => {
    try {
      const tablesData = await tableService.getAll({ includePricing: true });
      const sessionsData = await Promise.all(
        tablesData.map((table) => sessionService.getByTable(table._id, { tableStatus: table.status }))
      );

      setTables(tablesData);
      setActiveSessions(sessionsData.filter((session): session is Session => session !== null));
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
    } finally {
      setLoading(false);
    }
  });

  useEffect(() => {
    void loadDashboardData();
  }, [refreshVersion]);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setRefreshVersion((current) => current + 1);
    }, 30000);

    return () => {
      window.clearInterval(intervalId);
    };
  }, []);

  const activeTablesCount = tables.filter(t => t.status === 'playing').length;
  const availableTablesCount = tables.filter(t => t.status === 'available').length;
  const maintenanceTablesCount = tables.filter(t => t.status === 'maintenance').length;

  const gridTables = tables.map(table => {
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

  const stats = [
    isAdmin
      ? { label: 'Active Tables', value: String(activeTablesCount), change: `/ ${tables.length}`, color: 'blue' }
      : { label: 'Tables In Service', value: String(activeTablesCount), change: 'Now playing', color: 'blue' },
    isAdmin
      ? { label: 'Available', value: String(availableTablesCount), change: 'Ready', color: 'green' }
      : { label: 'Ready For Guests', value: String(availableTablesCount), change: 'Can start now', color: 'green' },
    { label: 'Active Sessions', value: String(activeSessions.length), change: 'Live', color: 'purple' },
    isAdmin
      ? { label: 'Total Tables', value: String(tables.length), change: 'All', color: 'cyan' }
      : { label: 'Maintenance', value: String(maintenanceTablesCount), change: 'Needs support', color: 'cyan' },
  ];

  return (
    <MainLayout
      currentPage="dashboard"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="dashboard-container">
        {/* Welcome Section */}
        <div className="welcome-section">
          <div>
            <h1>Welcome back, {user?.fullName?.split(' ')[0] || 'User'}</h1>
            <p>
              {isAdmin
                ? 'Here&apos;s your billiard club overview for today'
                : 'Track live tables, active sessions, and service flow for this shift'}
            </p>
          </div>
          <div className="time-display">
            {new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </div>
        </div>

        {/* Stats Grid */}
        <div className="stats-grid">
          {stats.map((stat) => (
            <div key={stat.label} className={`stat-card ${stat.color}`}>
              <div className="stat-label">{stat.label}</div>
              <div className="stat-value">{stat.value}</div>
              <div className="stat-change">{stat.change}</div>
            </div>
          ))}
        </div>

        {/* Floor Map - Table Grid */}
        <div className="dashboard-card full-width">
          <h2>{isAdmin ? 'Floor Map - Live Table Status' : 'Service Floor - Live Table Status'}</h2>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-secondary)' }}>Loading tables...</div>
          ) : (
            <TableGrid
              tables={gridTables}
              onTableUpdate={() => setRefreshVersion((current) => current + 1)}
            />
          )}
        </div>
      </div>
    </MainLayout>
  );
};

export default Dashboard;
