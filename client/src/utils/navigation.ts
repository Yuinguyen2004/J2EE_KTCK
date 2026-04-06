import type { User } from '../services/authService';

export type UserRole = User['role'];
export type OperationalUserRole = Exclude<UserRole, 'customer'>;

export type AppPage =
  | 'dashboard'
  | 'table-management'
  | 'fb-management'
  | 'revenue-reports'
  | 'staff-tables'
  | 'staff-menu'
  | 'my-profile'
  | 'notifications';

export interface NavigationItem {
  id: AppPage;
  label: string;
}

const navigationByRole: Record<OperationalUserRole, NavigationItem[]> = {
  admin: [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'table-management', label: 'Table Management' },
    { id: 'fb-management', label: 'F&B Management' },
    { id: 'revenue-reports', label: 'Revenue Reports' },
  ],
  staff: [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'staff-tables', label: 'Table Service' },
    { id: 'staff-menu', label: 'Service Menu' },
  ],
};

export const isOperationalUserRole = (role: UserRole): role is OperationalUserRole =>
  role === 'admin' || role === 'staff';

export const getNavigationItems = (role: UserRole): NavigationItem[] =>
  isOperationalUserRole(role) ? navigationByRole[role] : [];

export const getDefaultPageForRole = (role: OperationalUserRole): AppPage => {
  void role;
  return 'dashboard';
};

const sharedUtilityPages: AppPage[] = ['my-profile', 'notifications'];

export const isPageAllowedForRole = (role: OperationalUserRole, page: string): page is AppPage =>
  navigationByRole[role].some((item) => item.id === page) || sharedUtilityPages.includes(page as AppPage);
