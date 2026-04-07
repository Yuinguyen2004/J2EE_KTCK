import axios from 'axios';
import api from './api';
import { tableService, type Table } from './tableService';
import { toNumber } from './server';

interface ServerSession {
  id: number;
  tableId: number;
  tableName: string;
  customerId: number | null;
  customerName: string | null;
  customerMembershipTierName: string | null;
  customerMembershipDiscountPercent: number | string | null;
  staffId: number | null;
  staffName: string | null;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';
  startedAt: string;
  endedAt: string | null;
  elapsedSeconds: number | null;
  billableSeconds: number | null;
  totalPausedSeconds: number | null;
  totalAmount: number | string | null;
  notes: string | null;
}

interface ServerInvoice {
  id: number;
  totalAmount: number | string;
  tableAmount: number | string;
  orderAmount: number | string;
  discountAmount: number | string;
}

interface ServerEndSessionResponse {
  session: ServerSession;
  invoice: ServerInvoice;
}

export interface Session {
  _id: string;
  tableId: string;
  tableName: string;
  customerId?: string;
  customerName?: string;
  customerMembershipTierName?: string;
  customerMembershipDiscountPercent?: number;
  staffId?: string;
  staffName?: string;
  startTime: string;
  endTime?: string;
  duration?: number;
  totalTableCost?: number;
  totalFnbCost?: number;
  totalAmount?: number;
  status: 'active' | 'completed';
}

export interface SessionCheckoutResult extends Session {
  invoiceId: string;
  discountAmount?: number;
}

interface GetByTableOptions {
  tableStatus?: Table['status'];
}

const mapSession = (session: ServerSession): Session => ({
  _id: String(session.id),
  tableId: String(session.tableId),
  tableName: session.tableName,
  customerId: session.customerId ? String(session.customerId) : undefined,
  customerName: session.customerName || undefined,
  customerMembershipTierName: session.customerMembershipTierName || undefined,
  customerMembershipDiscountPercent: session.customerMembershipDiscountPercent == null
    ? undefined
    : toNumber(session.customerMembershipDiscountPercent),
  staffId: session.staffId ? String(session.staffId) : undefined,
  staffName: session.staffName || undefined,
  startTime: session.startedAt,
  endTime: session.endedAt || undefined,
  duration: session.elapsedSeconds ? Math.ceil(session.elapsedSeconds / 60) : undefined,
  totalAmount: toNumber(session.totalAmount),
  status: session.status === 'COMPLETED' || session.status === 'CANCELLED' ? 'completed' : 'active',
});

const mapCheckout = (payload: ServerEndSessionResponse): SessionCheckoutResult => ({
  ...mapSession(payload.session),
  totalTableCost: toNumber(payload.invoice.tableAmount),
  totalFnbCost: toNumber(payload.invoice.orderAmount),
  discountAmount: toNumber(payload.invoice.discountAmount),
  totalAmount: toNumber(payload.invoice.totalAmount),
  invoiceId: String(payload.invoice.id),
  status: 'completed',
});

const canHaveActiveSession = (tableStatus?: Table['status']) => tableStatus === 'playing';

const getActiveSessionForTable = async (
  tableId: string,
  options?: GetByTableOptions,
): Promise<Session | null> => {
  if (options?.tableStatus && !canHaveActiveSession(options.tableStatus)) {
    return null;
  }

  try {
    const { data } = await api.get<ServerSession>(`/tables/${tableId}/active-session`);
    return mapSession(data);
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null;
    }

    throw error;
  }
};

export const sessionService = {
  async start(
    tableId: string,
    input?: {
      customerId?: string | null;
      overrideReserved?: boolean;
      notes?: string | null;
    },
  ): Promise<Session> {
    const { data } = await api.post<ServerSession>(`/tables/${tableId}/start-session`, {
      customerId: input?.customerId ? Number(input.customerId) : null,
      overrideReserved: input?.overrideReserved ?? false,
      notes: input?.notes?.trim() || null,
    });
    return mapSession(data);
  },

  async end(sessionId: string): Promise<SessionCheckoutResult> {
    const { data } = await api.post<ServerEndSessionResponse>(`/sessions/${sessionId}/end`);
    return mapCheckout(data);
  },

  async getById(id: string): Promise<Session> {
    const { data } = await api.get<ServerSession>(`/sessions/${id}`);
    return mapSession(data);
  },

  async getByTable(tableId: string, options?: GetByTableOptions): Promise<Session | null> {
    return getActiveSessionForTable(tableId, options);
  },

  async getAll(params?: { status?: string }): Promise<Session[]> {
    if (params?.status && params.status !== 'active') {
      return [];
    }

    const tables = await tableService.getAll();
    const activeTables = tables.filter((table) => canHaveActiveSession(table.status));
    const sessions = await Promise.all(
      activeTables.map((table) => getActiveSessionForTable(table._id, { tableStatus: table.status }))
    );
    return sessions.filter((session): session is Session => session !== null);
  },
};
