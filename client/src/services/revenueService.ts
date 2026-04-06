import api from './api';
import { readPageItems, type PageResponse, toNumber } from './server';

interface ServerRevenueBucket {
  label: string;
  bucketStart: string;
  bucketEnd: string;
  invoiceCount: number;
  totalAmount: number | string;
}

interface ServerRevenueReport {
  from: string;
  to: string;
  groupBy: 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
  invoiceCount: number;
  totalAmount: number | string;
  buckets: ServerRevenueBucket[];
}

interface ServerInvoice {
  id: number;
  tableId: number | null;
  tableName: string | null;
  status: 'DRAFT' | 'ISSUED' | 'PAID' | 'VOID';
  tableAmount: number | string;
  orderAmount: number | string;
  totalAmount: number | string;
  paidAt: string | null;
}

export interface MonthlyRevenue {
  month: number;
  year: number;
  totalRevenue: number;
  totalTableCost: number;
  totalFnbCost: number;
  invoiceCount: number;
  averagePerDay: number;
  dailyBreakdown: { day: number; revenue: number; invoiceCount: number }[];
}

export interface RevenueSummary {
  totalRevenue: number;
  totalTableCost: number;
  totalFnbCost: number;
  invoiceCount: number;
  topTables: { tableId: string; name: string; totalRevenue: number; invoiceCount: number }[];
}

const INVOICE_PAGE_SIZE = 500;

const toIsoDate = (year: number, month: number, day: number) =>
  new Date(Date.UTC(year, month - 1, day)).toISOString().slice(0, 10);

const daysInMonth = (year: number, month: number) => new Date(year, month, 0).getDate();

const fetchPaidInvoices = async () => {
  const { data } = await api.get<PageResponse<ServerInvoice>>('/invoices', {
    params: {
      status: 'PAID',
      size: INVOICE_PAGE_SIZE,
      sortBy: 'paidAt',
      direction: 'desc',
    },
  });

  return readPageItems(data);
};

const filterInvoicesForMonth = (invoices: ServerInvoice[], year: number, month: number) =>
  invoices.filter((invoice) => {
    if (!invoice.paidAt) {
      return false;
    }

    const paidAt = new Date(invoice.paidAt);
    return paidAt.getUTCFullYear() === year && paidAt.getUTCMonth() + 1 === month;
  });

const buildSummary = (invoices: ServerInvoice[]): RevenueSummary => {
  const topTablesMap = new Map<string, { tableId: string; name: string; totalRevenue: number; invoiceCount: number }>();

  invoices.forEach((invoice) => {
    const key = String(invoice.tableId ?? 'unknown');
    const current = topTablesMap.get(key) || {
      tableId: key,
      name: invoice.tableName || 'Unknown table',
      totalRevenue: 0,
      invoiceCount: 0,
    };

    current.totalRevenue += toNumber(invoice.totalAmount);
    current.invoiceCount += 1;
    topTablesMap.set(key, current);
  });

  return {
    totalRevenue: invoices.reduce((sum, invoice) => sum + toNumber(invoice.totalAmount), 0),
    totalTableCost: invoices.reduce((sum, invoice) => sum + toNumber(invoice.tableAmount), 0),
    totalFnbCost: invoices.reduce((sum, invoice) => sum + toNumber(invoice.orderAmount), 0),
    invoiceCount: invoices.length,
    topTables: Array.from(topTablesMap.values())
      .sort((left, right) => right.totalRevenue - left.totalRevenue)
      .slice(0, 5),
  };
};

export const revenueService = {
  async getMonthly(month: number, year: number): Promise<MonthlyRevenue> {
    const from = toIsoDate(year, month, 1);
    const to = toIsoDate(year, month, daysInMonth(year, month));

    const [{ data: report }, paidInvoices] = await Promise.all([
      api.get<ServerRevenueReport>('/reports/revenue', {
        params: { from, to, groupBy: 'DAY' },
      }),
      fetchPaidInvoices(),
    ]);

    const monthlyInvoices = filterInvoicesForMonth(paidInvoices, year, month);
    const summary = buildSummary(monthlyInvoices);

    return {
      month,
      year,
      totalRevenue: toNumber(report.totalAmount),
      totalTableCost: summary.totalTableCost,
      totalFnbCost: summary.totalFnbCost,
      invoiceCount: report.invoiceCount,
      averagePerDay: report.buckets.length > 0 ? toNumber(report.totalAmount) / report.buckets.length : 0,
      dailyBreakdown: report.buckets.map((bucket) => ({
        day: new Date(bucket.bucketStart).getUTCDate(),
        revenue: toNumber(bucket.totalAmount),
        invoiceCount: bucket.invoiceCount,
      })),
    };
  },

  async getSummary(month: number, year: number): Promise<RevenueSummary> {
    const invoices = await fetchPaidInvoices();
    return buildSummary(filterInvoicesForMonth(invoices, year, month));
  },
};
