import React, { type ReactNode } from 'react';
import Sidebar from './Sidebar';
import Header from './Header';
import type { User } from '../services/authService';
import type { AppPage, UserRole } from '../utils/navigation';
import '../styles/layout.css';

interface MainLayoutProps {
  children: ReactNode;
  currentPage?: AppPage;
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
  userName?: string;
  userRole?: UserRole;
}

export const MainLayout: React.FC<MainLayoutProps> = ({
  children,
  currentPage = 'dashboard',
  onNavigate = () => {},
  onLogout = () => {},
  user = null,
  userName = 'John Doe',
  userRole = 'staff',
}) => {
  return (
    <div className="main-layout">
      <Sidebar currentPage={currentPage} onNavigate={onNavigate} userRole={userRole} />
      <div className="main-content">
        <Header
          currentPage={currentPage}
          onNavigate={onNavigate}
          user={user}
          userName={userName}
          userRole={userRole}
          onLogout={onLogout}
        />
        <main className="content-area">
          {children}
        </main>
      </div>
    </div>
  );
};

export default MainLayout;
