import React, { useState } from 'react';
import {
  Menu,
  X,
  Home,
  Layers,
  UtensilsCrossed,
  BarChart3,
  User,
  Users,
  Bell,
  CalendarClock,
  MessageSquare,
} from 'lucide-react';
import { getNavigationItems, type AppPage, type UserRole } from '../utils/navigation';
import '../styles/sidebar.css';

interface SidebarProps {
  currentPage?: AppPage;
  onNavigate?: (page: AppPage) => void;
  userRole?: UserRole;
}

export const Sidebar: React.FC<SidebarProps> = ({
  currentPage = 'dashboard',
  onNavigate = () => {},
  userRole = 'staff',
}) => {
  const [isOpen, setIsOpen] = useState(true);
  const [isCollapsed, setIsCollapsed] = useState(false);

  const iconMap: Record<AppPage, typeof Home> = {
    dashboard: Home,
    reservations: CalendarClock,
    'table-management': Layers,
    'fb-management': UtensilsCrossed,
    crm: Users,
    'revenue-reports': BarChart3,
    'staff-tables': Layers,
    'staff-menu': UtensilsCrossed,
    'staff-chat': MessageSquare,
    'customer-dashboard': Home,
    'customer-menu': UtensilsCrossed,
    'customer-reservations': CalendarClock,
    'customer-chat': MessageSquare,
    'my-profile': User,
    notifications: Bell,
  };
  const navItems = getNavigationItems(userRole);

  const handleNavigate = (itemId: AppPage) => {
    onNavigate(itemId);
  };

  const toggleCollapse = () => {
    setIsCollapsed(!isCollapsed);
  };

  const closeSidebar = () => {
    setIsOpen(false);
  };

  return (
    <>
      {/* Mobile Menu Toggle */}
      <button
        className="sidebar-mobile-toggle"
        onClick={() => setIsOpen(!isOpen)}
        aria-label="Toggle sidebar"
      >
        {isOpen ? <X size={24} /> : <Menu size={24} />}
      </button>

      {/* Sidebar Overlay (Mobile) */}
      {isOpen && (
        <div className="sidebar-overlay" onClick={closeSidebar} />
      )}

      {/* Sidebar */}
      <aside className={`sidebar ${isOpen ? 'open' : ''} ${isCollapsed ? 'collapsed' : ''}`}>
        {/* Sidebar Header */}
        <div className="sidebar-header">
          <div className="sidebar-logo">
            <div className="logo-icon">🎱</div>
            {!isCollapsed && <span className="logo-text">BIDA</span>}
          </div>
          <button
            className="sidebar-collapse-btn"
            onClick={toggleCollapse}
            aria-label="Collapse sidebar"
          >
            <Menu size={20} />
          </button>
        </div>

        {/* Sidebar Navigation */}
        <nav className="sidebar-nav">
          {navItems.map((item) => {
            const IconComponent = iconMap[item.id];
            const isActive = currentPage === item.id;

            return (
              <button
                key={item.id}
                className={`nav-item ${isActive ? 'active' : ''}`}
                onClick={() => handleNavigate(item.id)}
                title={isCollapsed ? item.label : undefined}
              >
                <IconComponent size={20} />
                {!isCollapsed && <span>{item.label}</span>}
              </button>
            );
          })}
        </nav>

        {/* Sidebar Footer */}
        <div className="sidebar-footer">
          <div className="sidebar-info">
            {!isCollapsed && (
              <>
                <div className="info-title">{userRole === 'customer' ? 'Portal Access' : 'Shift Info'}</div>
                <div className="info-item">
                  <span className="label">{userRole === 'customer' ? 'Mode:' : 'Status:'}</span>
                  <span className="value active-status">
                    {userRole === 'customer' ? 'Customer' : 'On Duty'}
                  </span>
                </div>
              </>
            )}
          </div>
        </div>
      </aside>
    </>
  );
};

export default Sidebar;
