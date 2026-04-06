import { fnbService } from './fnbService';
import { sessionService } from './sessionService';
import { tableService } from './tableService';
import type { User } from './authService';
import type { AppPage } from '../utils/navigation';
import { formatCurrency } from '../utils/formatCurrency';

export interface AppNotification {
  id: string;
  level: 'critical' | 'attention' | 'info';
  title: string;
  message: string;
  page: AppPage;
  actionLabel: string;
  createdAt: string;
  source: 'activity' | 'snapshot';
}

interface SessionStartedEvent {
  sessionId: string;
  tableId: string;
  tableName?: string;
  staffId?: string;
  staffName?: string;
  startedAt?: string;
}

interface SessionEndedEvent {
  sessionId: string;
  tableId: string;
  tableName?: string;
  staffId?: string;
  staffName?: string;
  duration?: number;
  totalTableCost?: number;
  totalFnbCost?: number;
  totalAmount?: number;
  endedAt?: string;
}

interface OrderCreatedEvent {
  orderId: string;
  sessionId: string;
  tableId?: string;
  tableName?: string;
  staffId?: string;
  staffName?: string;
  fnbItemId?: string;
  itemName?: string;
  quantity?: number;
  totalPrice?: number;
  createdAt?: string;
}

interface TableStatusChangeEvent {
  tableId: string;
  tableName?: string;
  status: string;
}

const STORAGE_KEY = 'bida_notification_activity_feed';
const UPDATE_EVENT = 'bida-notifications-updated';
const MAX_ACTIVITY_ITEMS = 20;

const tablePageByRole: Record<User['role'], AppPage> = {
  admin: 'table-management',
  staff: 'staff-tables',
  customer: 'my-profile',
};

const menuPageByRole: Record<User['role'], AppPage> = {
  admin: 'fb-management',
  staff: 'staff-menu',
  customer: 'my-profile',
};

const dispatchUpdate = () => {
  window.dispatchEvent(new CustomEvent(UPDATE_EVENT));
};

const readActivityNotifications = (): AppNotification[] => {
  if (typeof window === 'undefined') {
    return [];
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }

    const parsed = JSON.parse(raw) as AppNotification[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const writeActivityNotifications = (notifications: AppNotification[]) => {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(notifications));
};

const persistActivityNotification = (notification: AppNotification) => {
  const current = readActivityNotifications();
  const next = [notification, ...current.filter((item) => item.id !== notification.id)].slice(0, MAX_ACTIVITY_ITEMS);
  writeActivityNotifications(next);
  dispatchUpdate();
};

const buildSnapshotNotifications = async (role: User['role']): Promise<AppNotification[]> => {
  const [tables, sessions, fnbItems] = await Promise.all([
    tableService.getAll(),
    sessionService.getAll({ status: 'active' }),
    fnbService.getAll(),
  ]);

  const maintenanceTables = tables.filter((table) => table.status === 'maintenance');
  const availableTables = tables.filter((table) => table.status === 'available');
  const unavailableItems = fnbItems.filter((item) => !item.isAvailable);
  const createdAt = new Date().toISOString();
  const notifications: AppNotification[] = [];

  if (maintenanceTables.length > 0) {
    notifications.push({
      id: 'snapshot-maintenance',
      level: 'critical',
      title: `${maintenanceTables.length} table${maintenanceTables.length > 1 ? 's' : ''} under maintenance`,
      message: 'Review floor availability and avoid assigning incoming guests to blocked tables.',
      page: tablePageByRole[role],
      actionLabel: role === 'admin' ? 'Review tables' : 'View service floor',
      createdAt,
      source: 'snapshot',
    });
  }

  if (sessions.length > 0) {
    notifications.push({
      id: 'snapshot-active-sessions',
      level: 'info',
      title: `${sessions.length} active session${sessions.length > 1 ? 's' : ''} running`,
      message: 'Live sessions are in progress. Keep orders and checkout flow synchronized.',
      page: role === 'admin' ? 'dashboard' : 'staff-tables',
      actionLabel: 'Open live floor',
      createdAt,
      source: 'snapshot',
    });
  }

  if (availableTables.length === 0) {
    notifications.push({
      id: 'snapshot-no-available-tables',
      level: 'attention',
      title: 'No available tables right now',
      message: 'Every table is occupied or blocked. Staff should manage turnover closely.',
      page: tablePageByRole[role],
      actionLabel: 'Check table status',
      createdAt,
      source: 'snapshot',
    });
  }

  if (unavailableItems.length > 0) {
    notifications.push({
      id: 'snapshot-unavailable-menu-items',
      level: 'attention',
      title: `${unavailableItems.length} menu item${unavailableItems.length > 1 ? 's are' : ' is'} unavailable`,
      message: 'Ordering should avoid unavailable items until stock or menu status is updated.',
      page: menuPageByRole[role],
      actionLabel: role === 'admin' ? 'Manage menu' : 'Check service menu',
      createdAt,
      source: 'snapshot',
    });
  }

  if (notifications.length === 0) {
    notifications.push({
      id: 'snapshot-all-clear',
      level: 'info',
      title: 'No operational alerts',
      message: 'Tables and menu are in a healthy state right now.',
      page: 'dashboard',
      actionLabel: 'Back to dashboard',
      createdAt,
      source: 'snapshot',
    });
  }

  return notifications;
};

const buildActivityNotification = (
  eventName: 'session:started' | 'session:ended' | 'order:created' | 'table:statusChange',
  payload: SessionStartedEvent | SessionEndedEvent | OrderCreatedEvent | TableStatusChangeEvent,
  role: User['role']
): AppNotification | null => {
  if (eventName === 'session:started') {
    const event = payload as SessionStartedEvent;
    return {
      id: `activity-session-started-${event.sessionId}`,
      level: 'info',
      title: `${event.tableName || 'A table'} session started`,
      message: `${event.staffName || 'A staff member'} opened a new playing session.`,
      page: tablePageByRole[role],
      actionLabel: 'Open live floor',
      createdAt: event.startedAt || new Date().toISOString(),
      source: 'activity',
    };
  }

  if (eventName === 'session:ended') {
    const event = payload as SessionEndedEvent;
    return {
      id: `activity-session-ended-${event.sessionId}`,
      level: 'attention',
      title: `${event.tableName || 'A table'} checkout completed`,
      message: `Session closed in ${event.duration || 0} minutes with a total of ${formatCurrency(event.totalAmount || 0)}.`,
      page: tablePageByRole[role],
      actionLabel: 'Review table status',
      createdAt: event.endedAt || new Date().toISOString(),
      source: 'activity',
    };
  }

  if (eventName === 'order:created') {
    const event = payload as OrderCreatedEvent;
    return {
      id: `activity-order-created-${event.orderId}`,
      level: 'info',
      title: `${event.tableName || 'A table'} added F&B`,
      message: `Added x${event.quantity || 0} ${event.itemName || 'item'} for ${formatCurrency(event.totalPrice || 0)}.`,
      page: tablePageByRole[role],
      actionLabel: 'View live session',
      createdAt: event.createdAt || new Date().toISOString(),
      source: 'activity',
    };
  }

  const event = payload as TableStatusChangeEvent;
  if (event.status !== 'maintenance') {
    return null;
  }

  return {
    id: `activity-table-maintenance-${event.tableId}-${event.status}`,
    level: 'critical',
    title: `${event.tableName || 'A table'} moved to maintenance`,
    message: 'This table is no longer available for new guests until it is restored.',
    page: tablePageByRole[role],
    actionLabel: 'Check table status',
    createdAt: new Date().toISOString(),
    source: 'activity',
  };
};

export const notificationService = {
  async getNotifications(role: User['role']): Promise<AppNotification[]> {
    const [snapshotNotifications, activityNotifications] = await Promise.all([
      buildSnapshotNotifications(role),
      Promise.resolve(readActivityNotifications()),
    ]);

    return [...activityNotifications, ...snapshotNotifications].sort(
      (left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
    );
  },

  recordRealtimeNotification(
    eventName: 'session:started' | 'session:ended' | 'order:created' | 'table:statusChange',
    payload: SessionStartedEvent | SessionEndedEvent | OrderCreatedEvent | TableStatusChangeEvent,
    role: User['role']
  ) {
    const notification = buildActivityNotification(eventName, payload, role);
    if (!notification) {
      return;
    }

    persistActivityNotification(notification);
  },

  subscribe(listener: () => void) {
    const handler = () => listener();
    window.addEventListener(UPDATE_EVENT, handler);
    return () => {
      window.removeEventListener(UPDATE_EVENT, handler);
    };
  },
};
