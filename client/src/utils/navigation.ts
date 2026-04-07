import type { User } from '../services/authService';

export type UserRole = User['role'];
export type OperationalUserRole = Exclude<UserRole, 'customer'>;

export type AppPage =
  | 'dashboard'
  | 'reservations'
  | 'table-management'
  | 'fb-management'
  | 'crm'
  | 'revenue-reports'
  | 'staff-tables'
  | 'staff-menu'
  | 'staff-chat'
  | 'customer-dashboard'
  | 'customer-menu'
  | 'customer-reservations'
  | 'customer-chat'
  | 'my-profile'
  | 'notifications';

export interface NavigationItem {
  id: AppPage;
  label: string;
}

const navigationByRole: Record<UserRole, NavigationItem[]> = {
  admin: [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'reservations', label: 'Reservations' },
    { id: 'table-management', label: 'Table Management' },
    { id: 'fb-management', label: 'F&B Management' },
    { id: 'crm', label: 'CRM' },
    { id: 'staff-chat', label: 'Customer Chat' },
    { id: 'revenue-reports', label: 'Revenue Reports' },
  ],
  staff: [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'reservations', label: 'Reservations' },
    { id: 'staff-tables', label: 'Table Service' },
    { id: 'staff-menu', label: 'Service Menu' },
    { id: 'crm', label: 'Customers' },
    { id: 'staff-chat', label: 'Customer Chat' },
  ],
  customer: [
    { id: 'customer-dashboard', label: 'Portal' },
    { id: 'customer-menu', label: 'Menu' },
    { id: 'customer-reservations', label: 'Reservations' },
    { id: 'customer-chat', label: 'Chat' },
  ],
};

export const isOperationalUserRole = (role: UserRole): role is OperationalUserRole =>
  role === 'admin' || role === 'staff';

export const getNavigationItems = (role: UserRole): NavigationItem[] =>
  navigationByRole[role];

export const getDefaultPageForRole = (role: UserRole): AppPage =>
  role === 'customer' ? 'customer-dashboard' : 'dashboard';

const sharedUtilityPages: AppPage[] = ['my-profile', 'notifications'];

export const isPageAllowedForRole = (role: UserRole, page: string): page is AppPage => {
  const utilityPages = role === 'customer' ? [] : sharedUtilityPages;
  return navigationByRole[role].some((item) => item.id === page)
    || utilityPages.includes(page as AppPage);
};
