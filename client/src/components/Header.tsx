import React, { useEffect, useEffectEvent, useState } from 'react';
import {
  Search,
  Bell,
  ChevronDown,
  LogOut,
  User,
} from 'lucide-react';
import type { User as AuthUser } from '../services/authService';
import { notificationService } from '../services/notificationService';
import type { AppPage } from '../utils/navigation';
import '../styles/header.css';

interface HeaderProps {
  currentPage?: AppPage;
  onNavigate?: (page: AppPage) => void;
  user?: AuthUser | null;
  userName?: string;
  userRole?: string;
  onLogout?: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  currentPage = 'dashboard',
  onNavigate = () => {},
  user = null,
  userName = 'John Doe',
  userRole = 'Floor Manager',
  onLogout = () => {},
}) => {
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [notificationCount, setNotificationCount] = useState(0);
  const isCustomer = user?.role === 'customer';
  const showNotifications = !isCustomer;

  const loadNotifications = useEffectEvent(async (currentUser: AuthUser) => {
    try {
      const notifications = await notificationService.getNotifications(currentUser.role);
      const actionableNotifications = notifications.filter((item) => item.id !== 'snapshot-all-clear');
      setNotificationCount(actionableNotifications.length);
    } catch (error) {
      console.error('Failed to load notification count:', error);
      setNotificationCount(0);
    }
  });

  useEffect(() => {
    if (!user || !showNotifications) {
      return;
    }

    const refreshNotifications = () => {
      void loadNotifications(user);
    };

    refreshNotifications();
    const unsubscribe = notificationService.subscribe(refreshNotifications);
    const intervalId = window.setInterval(refreshNotifications, 30000);

    return () => {
      unsubscribe();
      window.clearInterval(intervalId);
    };
  }, [showNotifications, user]);

  const handleLogout = () => {
    onLogout();
    setIsProfileOpen(false);
  };

  const handleNavigate = (page: AppPage) => {
    onNavigate(page);
    setIsProfileOpen(false);
  };

  return (
    <header className="header">
      <div className="header-content">
        {/* Search Bar */}
        <div className="search-container">
          <Search size={18} className="search-icon" />
          <input
            type="text"
            placeholder={isCustomer ? 'Search tables, menu, reservations...' : 'Search tables, orders, reports...'}
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            className="search-input"
          />
        </div>

        {/* Right Side Actions */}
        <div className="header-actions">
          {/* Notifications */}
          {showNotifications && (
            <button
              className={`notification-btn ${currentPage === 'notifications' ? 'active' : ''}`}
              aria-label="Notifications"
              onClick={() => handleNavigate('notifications')}
            >
              <Bell size={20} />
              {notificationCount > 0 && (
                <span className="notification-badge">{notificationCount}</span>
              )}
            </button>
          )}

          {/* User Profile Dropdown */}
          <div className="profile-dropdown">
            <button
              className={`profile-btn ${currentPage === 'my-profile' ? 'active' : ''}`}
              onClick={() => setIsProfileOpen(!isProfileOpen)}
              aria-label="User profile menu"
            >
              <div className="user-avatar">
                <User size={18} />
              </div>
              <div className="user-info">
                <div className="user-name">{userName}</div>
                <div className="user-role">{userRole}</div>
              </div>
              <ChevronDown
                size={16}
                className={`chevron ${isProfileOpen ? 'open' : ''}`}
              />
            </button>

            {/* Dropdown Menu */}
            {isProfileOpen && (
              <div className="dropdown-menu">
                {!isCustomer && (
                  <>
                    <button className="dropdown-item" onClick={() => handleNavigate('my-profile')}>
                      <User size={16} />
                      <span>My Profile</span>
                    </button>
                    <button className="dropdown-item" onClick={() => handleNavigate('notifications')}>
                      <Bell size={16} />
                      <span>Notifications</span>
                    </button>
                    <div className="dropdown-divider" />
                  </>
                )}
                <button
                  className="dropdown-item logout"
                  onClick={handleLogout}
                >
                  <LogOut size={16} />
                  <span>Logout</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
