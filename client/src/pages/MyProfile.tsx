import React, { useEffect, useMemo, useState } from 'react';
import { Mail, ShieldCheck, UserCircle2 } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { authService, type User } from '../services/authService';
import { fnbService } from '../services/fnbService';
import { sessionService } from '../services/sessionService';
import { tableService } from '../services/tableService';
import type { AppPage } from '../utils/navigation';
import '../styles/account.css';

interface MyProfileProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

const ADMIN_CAPABILITIES = [
  'Manage billiard tables and pricing',
  'Manage food and beverage catalog',
  'Access revenue analytics and reports',
  'Monitor live operations across the floor',
];

const STAFF_CAPABILITIES = [
  'Start and close table sessions',
  'Take F&B orders from active tables',
  'View service floor and item availability',
  'Monitor operational notifications for the shift',
];

export const MyProfile: React.FC<MyProfileProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const [profile, setProfile] = useState<User | null>(user ?? null);
  const [loading, setLoading] = useState(true);
  const [snapshot, setSnapshot] = useState({
    totalTables: 0,
    activeSessions: 0,
    availableItems: 0,
  });

  useEffect(() => {
    let isCancelled = false;

    const loadProfile = async () => {
      try {
        const [profileData, tables, sessions, fnbItems] = await Promise.all([
          authService.getMe(),
          tableService.getAll(),
          sessionService.getAll({ status: 'active' }),
          fnbService.getAll(),
        ]);

        if (!isCancelled) {
          setProfile(profileData);
          setSnapshot({
            totalTables: tables.length,
            activeSessions: sessions.length,
            availableItems: fnbItems.filter((item) => item.isAvailable).length,
          });
        }
      } catch (error) {
        console.error('Failed to load profile page:', error);
      } finally {
        if (!isCancelled) {
          setLoading(false);
        }
      }
    };

    void loadProfile();

    return () => {
      isCancelled = true;
    };
  }, []);

  const capabilities = useMemo(() => {
    return profile?.role === 'admin' ? ADMIN_CAPABILITIES : STAFF_CAPABILITIES;
  }, [profile?.role]);

  return (
    <MainLayout
      currentPage="my-profile"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={profile ?? user}
      userName={profile?.fullName || user?.fullName || 'User'}
      userRole={profile?.role || user?.role || 'staff'}
    >
      <div className="account-container">
        <div className="account-header">
          <div>
            <h1>My Profile</h1>
            <p>Identity, role access, and live operational context for the current account.</p>
          </div>
        </div>

        {loading ? (
          <div className="account-panel centered">Loading profile...</div>
        ) : (
          <>
            <div className="account-grid">
              <section className="account-panel profile-hero">
                <div className="profile-identity">
                  <div className="profile-avatar">
                    <UserCircle2 size={48} />
                  </div>
                  <div>
                    <h2>{profile?.fullName || 'User'}</h2>
                    <p>{profile?.email || 'No email found'}</p>
                  </div>
                </div>
                <div className="profile-meta-list">
                  <div className="meta-row">
                    <span className="meta-label"><Mail size={14} /> Email</span>
                    <span className="meta-value">{profile?.email || 'No email found'}</span>
                  </div>
                  <div className="meta-row">
                    <span className="meta-label"><ShieldCheck size={14} /> Role</span>
                    <span className="meta-value role-pill">{profile?.role || 'staff'}</span>
                  </div>
                  <div className="meta-row">
                    <span className="meta-label">Account ID</span>
                    <span className="meta-value">{profile?.id || 'Unknown'}</span>
                  </div>
                </div>
              </section>

              <section className="account-panel">
                <h3>Access Scope</h3>
                <div className="capability-list">
                  {capabilities.map((capability) => (
                    <div key={capability} className="capability-item">{capability}</div>
                  ))}
                </div>
              </section>
            </div>

            <div className="account-summary-grid">
              <div className="account-stat-card">
                <span className="account-stat-label">Tables In System</span>
                <span className="account-stat-value">{snapshot.totalTables}</span>
              </div>
              <div className="account-stat-card">
                <span className="account-stat-label">Active Sessions</span>
                <span className="account-stat-value">{snapshot.activeSessions}</span>
              </div>
              <div className="account-stat-card">
                <span className="account-stat-label">Available Menu Items</span>
                <span className="account-stat-value">{snapshot.availableItems}</span>
              </div>
            </div>
          </>
        )}
      </div>
    </MainLayout>
  );
};

export default MyProfile;
