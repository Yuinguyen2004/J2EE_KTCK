import api from './api';
import { readPageItems, type PageResponse, toNumber } from './server';

interface ServerMenuItem {
  id: number;
  name: string;
  description: string | null;
  price: number | string;
  imageUrl: string | null;
  active: boolean;
}

export interface FnbItem {
  _id: string;
  name: string;
  description: string;
  price: number;
  image?: string;
  isAvailable: boolean;
}

const PAGE_SIZE = 100;

const mapMenuItems = (items: ServerMenuItem[]): FnbItem[] =>
  items.map((item) => ({
    _id: String(item.id),
    name: item.name,
    description: item.description || '',
    price: toNumber(item.price),
    image: item.imageUrl || undefined,
    isAvailable: item.active,
  }));

export const fnbService = {
  async getAll(): Promise<FnbItem[]> {
    const { data } = await api.get<PageResponse<ServerMenuItem>>('/menu-items', {
      params: { size: PAGE_SIZE },
    });
    return mapMenuItems(readPageItems(data));
  },

  async getById(id: string): Promise<FnbItem> {
    const { data } = await api.get<ServerMenuItem>(`/menu-items/${id}`);
    return mapMenuItems([data])[0];
  },

  async create(item: Omit<FnbItem, '_id'>): Promise<FnbItem> {
    const { data } = await api.post<ServerMenuItem>('/menu-items', {
      name: item.name,
      description: item.description || null,
      price: item.price,
      imageUrl: item.image || null,
      active: item.isAvailable,
    });
    return mapMenuItems([data])[0];
  },

  async update(id: string, updates: Partial<FnbItem>): Promise<FnbItem> {
    if (!updates.name || typeof updates.price !== 'number') {
      throw new Error('Menu item name and price are required');
    }

    const { data } = await api.put<ServerMenuItem>(`/menu-items/${id}`, {
      name: updates.name,
      description: updates.description || null,
      price: updates.price,
      imageUrl: updates.image || null,
      active: updates.isAvailable ?? true,
    });
    return mapMenuItems([data])[0];
  },

  async delete(id: string): Promise<void> {
    await api.patch(`/menu-items/${id}/active`, { active: false });
  },
};
