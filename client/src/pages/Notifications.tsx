import React, { useEffect, useMemo, useState } from 'react';
import { Bell, ChevronRight } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { notificationService, type AppNotification } from '../services/notificationService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import '../styles/account.css';

interface NotificationsProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

export const Notifications: React.FC<NotificationsProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) {
      setNotifications([]);
      setLoading(false);
      return;
    }

    let isCancelled = false;
    const loadNotifications = async () => {
      try {
        const items = await notificationService.getNotifications(user.role);
        if (!isCancelled) {
          setNotifications(items);
        }
      } catch (error) {
        console.error('Failed to load notifications page:', error);
      } finally {
        if (!isCancelled) {
          setLoading(false);
        }
      }
    };

    void loadNotifications();
    const unsubscribe = notificationService.subscribe(() => {
      void loadNotifications();
    });

    return () => {
      isCancelled = true;
      unsubscribe();
    };
  }, [user]);

  const summary = useMemo(() => {
    return {
      critical: notifications.filter((item) => item.level === 'critical').length,
      attention: notifications.filter((item) => item.level === 'attention').length,
      info: notifications.filter((item) => item.level === 'info').length,
    };
  }, [notifications]);

  return (
    <MainLayout
      currentPage="notifications"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="account-container">
        <div className="account-header">
          <div>
            <h1>Notifications</h1>
            <p>Operational alerts derived from live tables, menu availability, and current sessions.</p>
          </div>
        </div>

        {loading ? (
          <div className="account-panel centered">Loading notifications...</div>
        ) : (
          <>
            <div className="account-summary-grid">
              <div className="account-stat-card">
                <span className="account-stat-label">Critical</span>
                <span className="account-stat-value">{summary.critical}</span>
              </div>
              <div className="account-stat-card">
                <span className="account-stat-label">Attention</span>
                <span className="account-stat-value">{summary.attention}</span>
              </div>
              <div className="account-stat-card">
                <span className="account-stat-label">Info</span>
                <span className="account-stat-value">{summary.info}</span>
              </div>
            </div>

            <div className="notification-list">
              {notifications.map((notification) => (
                <section key={notification.id} className={`account-panel notification-card level-${notification.level}`}>
                  <div className="notification-main">
                    <div className="notification-icon">
                      <Bell size={18} />
                    </div>
                    <div className="notification-copy">
                      <div className="notification-topline">
                        <span className={`notification-level ${notification.level}`}>{notification.level}</span>
                        <span className="notification-source">{notification.source}</span>
                        <h3>{notification.title}</h3>
                      </div>
                      <div className="notification-time">
                        {new Date(notification.createdAt).toLocaleString([], {
                          hour: '2-digit',
                          minute: '2-digit',
                          day: '2-digit',
                          month: 'short',
                        })}
                      </div>
                      <p>{notification.message}</p>
                    </div>
                  </div>
                  <button className="notification-action" onClick={() => onNavigate(notification.page)}>
                    <span>{notification.actionLabel}</span>
                    <ChevronRight size={16} />
                  </button>
                </section>
              ))}
            </div>
          </>
        )}
      </div>
    </MainLayout>
  );
};

export default Notifications;
