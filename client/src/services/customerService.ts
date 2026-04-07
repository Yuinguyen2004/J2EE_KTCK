import api from './api';
import { readPageItems, type PageResponse, toNumber } from './server';

interface ServerCustomer {
  id: number;
  userId: number;
  email: string;
  fullName: string;
  phone: string | null;
  active: boolean;
  membershipTierId: number | null;
  membershipTierName: string | null;
  notes: string | null;
  memberSince: string | null;
  createdAt: string;
  updatedAt: string;
}

interface ServerMembershipTier {
  id: number;
  name: string;
  discountPercent: number | string;
  minimumSpend: number | string | null;
  description: string | null;
  active: boolean;
}

interface ServerUser {
  id: number;
  email: string;
  fullName: string;
  role: string;
  active: boolean;
}

export interface Customer {
  id: string;
  userId: string;
  email: string;
  fullName: string;
  phone: string;
  active: boolean;
  membershipTierId: string | null;
  membershipTierName: string | null;
  notes: string;
  memberSince: string | null;
}

export interface MembershipTier {
  id: string;
  name: string;
  discountPercent: number;
  minimumSpend: number;
  description: string;
  active: boolean;
}

export interface CustomerUser {
  id: string;
  email: string;
  fullName: string;
}

const PAGE_SIZE = 100;

const mapCustomers = (items: ServerCustomer[]): Customer[] =>
  items.map((item) => ({
    id: String(item.id),
    userId: String(item.userId),
    email: item.email,
    fullName: item.fullName,
    phone: item.phone || '',
    active: item.active,
    membershipTierId: item.membershipTierId ? String(item.membershipTierId) : null,
    membershipTierName: item.membershipTierName || null,
    notes: item.notes || '',
    memberSince: item.memberSince || null,
  }));

const mapMembershipTiers = (items: ServerMembershipTier[]): MembershipTier[] =>
  items.map((item) => ({
    id: String(item.id),
    name: item.name,
    discountPercent: toNumber(item.discountPercent),
    minimumSpend: toNumber(item.minimumSpend),
    description: item.description || '',
    active: item.active,
  }));

const mapCustomerUsers = (items: ServerUser[]): CustomerUser[] =>
  items
    .filter((user) => user.role === 'CUSTOMER' && user.active)
    .map((user) => ({
      id: String(user.id),
      email: user.email,
      fullName: user.fullName,
    }));

export const customerService = {
  async getAll(): Promise<Customer[]> {
    const { data } = await api.get<PageResponse<ServerCustomer>>('/customers', {
      params: { size: PAGE_SIZE },
    });
    return mapCustomers(readPageItems(data));
  },

  async create(customer: {
    userId: string;
    membershipTierId?: string | null;
    notes?: string;
    memberSince?: string | null;
  }): Promise<Customer> {
    const { data } = await api.post<ServerCustomer>('/customers', {
      userId: Number(customer.userId),
      membershipTierId: customer.membershipTierId ? Number(customer.membershipTierId) : null,
      notes: customer.notes?.trim() || null,
      memberSince: customer.memberSince || null,
    });
    return mapCustomers([data])[0];
  },

  async update(
    id: string,
    customer: {
      userId: string;
      membershipTierId?: string | null;
      notes?: string;
      memberSince?: string | null;
    },
  ): Promise<Customer> {
    const { data } = await api.put<ServerCustomer>(`/customers/${id}`, {
      userId: Number(customer.userId),
      membershipTierId: customer.membershipTierId ? Number(customer.membershipTierId) : null,
      notes: customer.notes?.trim() || null,
      memberSince: customer.memberSince || null,
    });
    return mapCustomers([data])[0];
  },

  async setActive(id: string, active: boolean): Promise<Customer> {
    const { data } = await api.patch<ServerCustomer>(`/customers/${id}/active`, { active });
    return mapCustomers([data])[0];
  },
};

export const membershipService = {
  async getAll(): Promise<MembershipTier[]> {
    const { data } = await api.get<PageResponse<ServerMembershipTier>>('/memberships', {
      params: { size: PAGE_SIZE },
    });
    return mapMembershipTiers(readPageItems(data));
  },

  async create(tier: {
    name: string;
    discountPercent: number;
    minimumSpend: number;
    description?: string;
    active?: boolean;
  }): Promise<MembershipTier> {
    const { data } = await api.post<ServerMembershipTier>('/memberships', {
      name: tier.name.trim(),
      discountPercent: tier.discountPercent,
      minimumSpend: tier.minimumSpend,
      description: tier.description?.trim() || null,
      active: tier.active ?? true,
    });
    return mapMembershipTiers([data])[0];
  },

  async update(
    id: string,
    tier: {
      name: string;
      discountPercent: number;
      minimumSpend: number;
      description?: string;
      active?: boolean;
    },
  ): Promise<MembershipTier> {
    const { data } = await api.put<ServerMembershipTier>(`/memberships/${id}`, {
      name: tier.name.trim(),
      discountPercent: tier.discountPercent,
      minimumSpend: tier.minimumSpend,
      description: tier.description?.trim() || null,
      active: tier.active ?? true,
    });
    return mapMembershipTiers([data])[0];
  },

  async setActive(id: string, active: boolean): Promise<MembershipTier> {
    const { data } = await api.patch<ServerMembershipTier>(`/memberships/${id}/active`, { active });
    return mapMembershipTiers([data])[0];
  },
};

export const userLookupService = {
  async getCustomerUsers(): Promise<CustomerUser[]> {
    const { data } = await api.get<PageResponse<ServerUser>>('/users', {
      params: { size: PAGE_SIZE },
    });
    return mapCustomerUsers(readPageItems(data));
  },
};
