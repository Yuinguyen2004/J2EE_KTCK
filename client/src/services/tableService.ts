import axios from 'axios';
import api from './api';
import { readPageItems, type PageResponse, toNumber } from './server';

type ClientTableStatus = 'available' | 'playing' | 'reserved' | 'maintenance';
type ServerTableStatus = 'AVAILABLE' | 'IN_USE' | 'PAUSED' | 'RESERVED' | 'MAINTENANCE';

interface ServerTable {
  id: number;
  name: string;
  tableTypeId: number;
  tableTypeName: string;
  status: ServerTableStatus;
  floorPositionX: number;
  floorPositionY: number;
  active: boolean;
}

interface ServerTableType {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
}

interface ServerPricingRule {
  id: number;
  tableTypeId: number;
  tableTypeName: string;
  blockMinutes: number;
  pricePerMinute: number | string;
  sortOrder: number;
  active: boolean;
}

export interface Table {
  _id: string;
  name: string;
  type: string;
  tableTypeId: string;
  pricePerHour?: number;
  status: ClientTableStatus;
  position: { x: number; y: number };
  active: boolean;
}

export interface TableType {
  id: string;
  name: string;
  description?: string;
  active: boolean;
}

export interface PricingRule {
  id: string;
  tableTypeId: string;
  tableTypeName: string;
  blockMinutes: number;
  pricePerMinute: number;
  active: boolean;
  sortOrder: number;
}

const PAGE_SIZE = 100;

const toClientStatus = (status: ServerTableStatus): ClientTableStatus => {
  switch (status) {
    case 'IN_USE':
    case 'PAUSED':
      return 'playing';
    case 'RESERVED':
      return 'reserved';
    case 'MAINTENANCE':
      return 'maintenance';
    default:
      return 'available';
  }
};

const toServerStatus = (status: ClientTableStatus): ServerTableStatus => {
  switch (status) {
    case 'playing':
      return 'IN_USE';
    case 'reserved':
      return 'RESERVED';
    case 'maintenance':
      return 'MAINTENANCE';
    default:
      return 'AVAILABLE';
  }
};

const mapTableTypes = (items: ServerTableType[]): TableType[] =>
  items.map((item) => ({
    id: String(item.id),
    name: item.name,
    description: item.description || undefined,
    active: item.active,
  }));

const buildTableTypePayload = (tableType: Pick<TableType, 'name'> & Partial<Pick<TableType, 'description' | 'active'>>) => {
  if (!tableType.name.trim()) {
    throw new Error('Table type name is required');
  }

  return {
    name: tableType.name.trim(),
    description: tableType.description?.trim() || null,
    active: tableType.active,
  };
};

const mapPricingRules = (items: ServerPricingRule[]): PricingRule[] =>
  items.map((item) => ({
    id: String(item.id),
    tableTypeId: String(item.tableTypeId),
    tableTypeName: item.tableTypeName,
    blockMinutes: item.blockMinutes,
    pricePerMinute: toNumber(item.pricePerMinute),
    sortOrder: item.sortOrder,
    active: item.active,
  }));

const buildPricingRulePayload = (
  pricingRule: Pick<PricingRule, 'tableTypeId' | 'blockMinutes' | 'pricePerMinute' | 'sortOrder'>
  & Partial<Pick<PricingRule, 'active'>>
) => {
  if (!pricingRule.tableTypeId) {
    throw new Error('Select a table type for the pricing rule');
  }

  return {
    tableTypeId: Number(pricingRule.tableTypeId),
    blockMinutes: pricingRule.blockMinutes,
    pricePerMinute: pricingRule.pricePerMinute,
    sortOrder: pricingRule.sortOrder,
    active: pricingRule.active,
  };
};

const buildHourlyRateMap = (pricingRules: PricingRule[]) => {
  const rates = new Map<string, number>();

  pricingRules
    .filter((rule) => rule.active)
    .sort((left, right) => left.blockMinutes - right.blockMinutes)
    .forEach((rule) => {
      if (!rates.has(rule.tableTypeId)) {
        rates.set(rule.tableTypeId, rule.pricePerMinute * 60);
      }
    });

  return rates;
};

const loadOptionalPricingRules = async (
  loadPricingRules: () => Promise<PricingRule[]>
): Promise<PricingRule[] | null> => {
  try {
    return await loadPricingRules();
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 403) {
      console.warn('Pricing rules are unavailable for this role; continuing without hourly rates.');
      return null;
    }

    throw error;
  }
};

const mapTables = (items: ServerTable[], rates?: Map<string, number>): Table[] =>
  items.map((item) => ({
    _id: String(item.id),
    name: item.name,
    type: item.tableTypeName,
    tableTypeId: String(item.tableTypeId),
    pricePerHour: rates?.get(String(item.tableTypeId)),
    status: toClientStatus(item.status),
    position: {
      x: item.floorPositionX,
      y: item.floorPositionY,
    },
    active: item.active,
  }));

const buildUpsertPayload = (table: Partial<Table>) => {
  if (!table.name || !table.tableTypeId || !table.position) {
    throw new Error('Table name, type, and floor position are required');
  }

  return {
    name: table.name,
    tableTypeId: Number(table.tableTypeId),
    status: toServerStatus(table.status || 'available'),
    floorPositionX: table.position.x,
    floorPositionY: table.position.y,
    active: table.active ?? true,
  };
};

export const tableService = {
  async getAll(options?: { includePricing?: boolean }): Promise<Table[]> {
    const [{ data: tablePage }, pricingRules] = await Promise.all([
      api.get<PageResponse<ServerTable>>('/tables', { params: { size: PAGE_SIZE } }),
      options?.includePricing
        ? loadOptionalPricingRules(() => this.getPricingRules())
        : Promise.resolve<PricingRule[] | null>(null),
    ]);

    const rates = pricingRules ? buildHourlyRateMap(pricingRules) : undefined;
    return mapTables(readPageItems(tablePage), rates);
  },

  async getById(id: string, options?: { includePricing?: boolean }): Promise<Table> {
    const [{ data }, pricingRules] = await Promise.all([
      api.get<ServerTable>(`/tables/${id}`),
      options?.includePricing
        ? loadOptionalPricingRules(() => this.getPricingRules())
        : Promise.resolve<PricingRule[] | null>(null),
    ]);

    const rates = pricingRules ? buildHourlyRateMap(pricingRules) : undefined;
    return mapTables([data], rates)[0];
  },

  async create(table: Omit<Table, '_id'>): Promise<Table> {
    const { data } = await api.post<ServerTable>('/tables', buildUpsertPayload(table));
    return mapTables([data])[0];
  },

  async update(id: string, updates: Partial<Table>): Promise<Table> {
    const { data } = await api.put<ServerTable>(`/tables/${id}`, buildUpsertPayload(updates));
    return mapTables([data])[0];
  },

  async delete(id: string): Promise<void> {
    await api.patch(`/tables/${id}/active`, { active: false });
  },

  async getTableTypes(): Promise<TableType[]> {
    const { data } = await api.get<PageResponse<ServerTableType>>('/table-types', {
      params: { size: PAGE_SIZE },
    });
    return mapTableTypes(readPageItems(data));
  },

  async createTableType(tableType: Pick<TableType, 'name'> & Partial<Pick<TableType, 'description' | 'active'>>): Promise<TableType> {
    const { data } = await api.post<ServerTableType>('/table-types', buildTableTypePayload(tableType));
    return mapTableTypes([data])[0];
  },

  async updateTableType(
    id: string,
    tableType: Pick<TableType, 'name'> & Partial<Pick<TableType, 'description' | 'active'>>
  ): Promise<TableType> {
    const { data } = await api.put<ServerTableType>(`/table-types/${id}`, buildTableTypePayload(tableType));
    return mapTableTypes([data])[0];
  },

  async setTableTypeActive(id: string, active: boolean): Promise<TableType> {
    const { data } = await api.patch<ServerTableType>(`/table-types/${id}/active`, { active });
    return mapTableTypes([data])[0];
  },

  async getPricingRules(): Promise<PricingRule[]> {
    const { data } = await api.get<PageResponse<ServerPricingRule>>('/pricing-rules', {
      params: { size: PAGE_SIZE },
    });
    return mapPricingRules(readPageItems(data));
  },

  async createPricingRule(
    pricingRule: Pick<PricingRule, 'tableTypeId' | 'blockMinutes' | 'pricePerMinute' | 'sortOrder'>
    & Partial<Pick<PricingRule, 'active'>>
  ): Promise<PricingRule> {
    const { data } = await api.post<ServerPricingRule>('/pricing-rules', buildPricingRulePayload(pricingRule));
    return mapPricingRules([data])[0];
  },

  async updatePricingRule(
    id: string,
    pricingRule: Pick<PricingRule, 'tableTypeId' | 'blockMinutes' | 'pricePerMinute' | 'sortOrder'>
    & Partial<Pick<PricingRule, 'active'>>
  ): Promise<PricingRule> {
    const { data } = await api.put<ServerPricingRule>(`/pricing-rules/${id}`, buildPricingRulePayload(pricingRule));
    return mapPricingRules([data])[0];
  },

  async setPricingRuleActive(id: string, active: boolean): Promise<PricingRule> {
    const { data } = await api.patch<ServerPricingRule>(`/pricing-rules/${id}/active`, { active });
    return mapPricingRules([data])[0];
  },
};
