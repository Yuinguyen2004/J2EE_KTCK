import api from './api';
import { readPageItems, type PageResponse } from './server';

type ServerTableStatus = 'AVAILABLE' | 'IN_USE' | 'PAUSED' | 'RESERVED' | 'MAINTENANCE';
type ServerReservationStatus = 'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED';
type ClientTableStatus = 'available' | 'playing' | 'reserved' | 'maintenance';

interface ServerReservation {
  id: number;
  tableId: number | null;
  tableName: string | null;
  tableStatus: ServerTableStatus | null;
  customerId: number | null;
  customerName: string | null;
  staffId: number | null;
  staffName: string | null;
  status: ServerReservationStatus;
  reservedFrom: string;
  reservedTo: string;
  partySize: number;
  checkedInAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ReservationStatus = ServerReservationStatus;

export interface Reservation {
  id: string;
  tableId?: string;
  tableName?: string;
  tableStatus?: ClientTableStatus;
  customerId?: string;
  customerName?: string;
  staffId?: string;
  staffName?: string;
  status: ReservationStatus;
  reservedFrom: string;
  reservedTo: string;
  partySize: number;
  checkedInAt?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

interface ReservationQueryOptions {
  status?: ReservationStatus;
  q?: string;
  sortBy?: 'createdAt' | 'reservedFrom' | 'reservedTo' | 'status' | 'tableName' | 'customerName' | 'staffName' | 'updatedAt';
  direction?: 'asc' | 'desc';
}

const PAGE_SIZE = 100;

const toClientTableStatus = (status: ServerTableStatus | null | undefined): ClientTableStatus | undefined => {
  switch (status) {
    case 'IN_USE':
    case 'PAUSED':
      return 'playing';
    case 'RESERVED':
      return 'reserved';
    case 'MAINTENANCE':
      return 'maintenance';
    case 'AVAILABLE':
      return 'available';
    default:
      return undefined;
  }
};

const mapReservations = (items: ServerReservation[]): Reservation[] =>
  items.map((reservation) => ({
    id: String(reservation.id),
    tableId: reservation.tableId == null ? undefined : String(reservation.tableId),
    tableName: reservation.tableName || undefined,
    tableStatus: toClientTableStatus(reservation.tableStatus),
    customerId: reservation.customerId == null ? undefined : String(reservation.customerId),
    customerName: reservation.customerName || undefined,
    staffId: reservation.staffId == null ? undefined : String(reservation.staffId),
    staffName: reservation.staffName || undefined,
    status: reservation.status,
    reservedFrom: reservation.reservedFrom,
    reservedTo: reservation.reservedTo,
    partySize: reservation.partySize,
    checkedInAt: reservation.checkedInAt || undefined,
    notes: reservation.notes || undefined,
    createdAt: reservation.createdAt,
    updatedAt: reservation.updatedAt,
  }));

export const reservationService = {
  async getAll(options?: ReservationQueryOptions): Promise<Reservation[]> {
    const { data } = await api.get<PageResponse<ServerReservation>>('/reservations', {
      params: {
        size: PAGE_SIZE,
        status: options?.status,
        q: options?.q,
        sortBy: options?.sortBy || 'reservedFrom',
        direction: options?.direction || 'asc',
      },
    });

    return mapReservations(readPageItems(data));
  },

  async confirm(id: string, tableId: string): Promise<Reservation> {
    const { data } = await api.post<ServerReservation>(`/reservations/${id}/confirm`, {
      tableId: Number(tableId),
    });

    return mapReservations([data])[0];
  },
};
