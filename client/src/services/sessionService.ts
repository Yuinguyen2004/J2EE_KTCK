import axios from 'axios';
import api from './api';
import { tableService } from './tableService';
import { toNumber } from './server';

interface ServerSession {
  id: number;
  tableId: number;
  tableName: string;
  customerId: number | null;
  customerName: string | null;
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
}

interface ServerEndSessionResponse {
  session: ServerSession;
  invoice: ServerInvoice;
}

export interface Session {
  _id: string;
  tableId: string;
  tableName: string;
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
}

const mapSession = (session: ServerSession): Session => ({
  _id: String(session.id),
  tableId: String(session.tableId),
  tableName: session.tableName,
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
  totalAmount: toNumber(payload.invoice.totalAmount),
  invoiceId: String(payload.invoice.id),
  status: 'completed',
});

const getActiveSessionForTable = async (tableId: string): Promise<Session | null> => {
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
  async start(tableId: string): Promise<Session> {
    const { data } = await api.post<ServerSession>(`/tables/${tableId}/start-session`, {
      customerId: null,
      overrideReserved: false,
      notes: null,
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

  async getByTable(tableId: string): Promise<Session | null> {
    return getActiveSessionForTable(tableId);
  },

  async getAll(params?: { status?: string }): Promise<Session[]> {
    if (params?.status && params.status !== 'active') {
      return [];
    }

    const tables = await tableService.getAll();
    const sessions = await Promise.all(tables.map((table) => getActiveSessionForTable(table._id)));
    return sessions.filter((session): session is Session => session !== null);
  },
};
