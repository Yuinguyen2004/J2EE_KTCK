import api from './api';
import { readPageItems, type PageResponse, toNumber } from './server';

interface ServerOrderItem {
  id: number;
  menuItemId: number;
  menuItemName: string;
  quantity: number;
  unitPrice: number | string;
  subtotal: number | string;
}

interface ServerOrder {
  id: number;
  sessionId: number;
  status: 'PENDING' | 'CONFIRMED';
  totalAmount: number | string;
  orderedAt: string;
  createdAt: string;
  items: ServerOrderItem[];
}

export interface OrderLineItem {
  _id: string;
  sessionId: string;
  fnbItemId: string;
  name: string;
  quantity: number;
  price: number;
  subtotal: number;
  status: 'pending' | 'confirmed';
  createdAt: string;
}

const PAGE_SIZE = 100;

const flattenOrderItems = (orders: ServerOrder[]): OrderLineItem[] =>
  orders.flatMap((order) =>
    order.items.map((item) => ({
      _id: String(item.id),
      sessionId: String(order.sessionId),
      fnbItemId: String(item.menuItemId),
      name: item.menuItemName,
      quantity: item.quantity,
      price: toNumber(item.unitPrice),
      subtotal: toNumber(item.subtotal),
      status: order.status === 'CONFIRMED' ? 'confirmed' : 'pending',
      createdAt: order.createdAt || order.orderedAt,
    }))
  );

const fetchOrdersBySession = async (sessionId: string) => {
  const { data } = await api.get<PageResponse<ServerOrder>>('/orders', {
    params: {
      sessionId,
      size: PAGE_SIZE,
      sortBy: 'createdAt',
      direction: 'desc',
    },
  });

  return readPageItems(data);
};

export const orderService = {
  async create(sessionId: string, fnbItemId: string, quantity: number): Promise<OrderLineItem[]> {
    return this.createBatch(sessionId, [{ fnbItemId, quantity }]);
  },

  async createBatch(
    sessionId: string,
    items: Array<{ fnbItemId: string; quantity: number }>
  ): Promise<OrderLineItem[]> {
    const { data } = await api.post<ServerOrder>('/orders', {
      sessionId: Number(sessionId),
      items: items.map((item) => ({
        menuItemId: Number(item.fnbItemId),
        quantity: item.quantity,
      })),
      notes: null,
    });

    return flattenOrderItems([data]);
  },

  async getBySession(sessionId: string): Promise<OrderLineItem[]> {
    const orders = await fetchOrdersBySession(sessionId);
    return flattenOrderItems(orders);
  },

  async getTotalCost(sessionId: string): Promise<number> {
    const orders = await fetchOrdersBySession(sessionId);
    return orders.reduce((total, order) => total + toNumber(order.totalAmount), 0);
  },
};
