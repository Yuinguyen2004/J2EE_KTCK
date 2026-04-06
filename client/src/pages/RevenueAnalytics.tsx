import React, { useEffect, useEffectEvent, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Calendar } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { revenueService, type MonthlyRevenue, type RevenueSummary } from '../services/revenueService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/analytics.css';

interface RevenueAnalyticsProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

export const RevenueAnalytics: React.FC<RevenueAnalyticsProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());
  const [monthly, setMonthly] = useState<MonthlyRevenue | null>(null);
  const [summary, setSummary] = useState<RevenueSummary | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useEffectEvent(async () => {
    setLoading(true);
    try {
      const [monthlyData, summaryData] = await Promise.all([
        revenueService.getMonthly(month, year),
        revenueService.getSummary(month, year),
      ]);
      setMonthly(monthlyData);
      setSummary(summaryData);
    } catch (error) {
      console.error('Failed to fetch revenue data:', error);
    } finally {
      setLoading(false);
    }
  });

  useEffect(() => {
    void fetchData();
  }, [month, year]);

  const chartData = monthly?.dailyBreakdown.map((entry) => ({
    date: `Day ${entry.day}`,
    revenue: entry.revenue,
    invoices: entry.invoiceCount,
  })) || [];

  const revenueMixData = summary ? [
    { name: 'Table Revenue', value: summary.totalTableCost },
    { name: 'F&B Revenue', value: summary.totalFnbCost },
  ] : [];

  const topTablesData = summary?.topTables.map((table) => ({
    name: table.name,
    invoices: table.invoiceCount,
    revenue: table.totalRevenue,
  })) || [];

  const stats = [
    { label: 'Monthly Revenue', value: formatCurrency(monthly?.totalRevenue || 0), change: `${monthly?.invoiceCount || 0} paid invoices` },
    { label: 'Table Revenue', value: formatCurrency(summary?.totalTableCost || 0), change: 'Settled table usage' },
    { label: 'F&B Revenue', value: formatCurrency(summary?.totalFnbCost || 0), change: 'Settled menu sales' },
    { label: 'Avg Per Day', value: formatCurrency(monthly?.averagePerDay || 0), change: 'Calendar bucket average' },
  ];

  return (
    <MainLayout
      currentPage="revenue-reports"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="analytics-container">
        <div className="analytics-header">
          <div>
            <h1>Revenue Analytics</h1>
            <p>Revenue buckets and paid invoice totals derived from the existing server endpoints.</p>
          </div>
          <div className="date-range-picker">
            <Calendar size={20} />
            <select
              value={month}
              onChange={(event) => setMonth(Number(event.target.value))}
              className="date-input"
            >
              {Array.from({ length: 12 }, (_, index) => (
                <option key={index + 1} value={index + 1}>
                  {new Date(2000, index).toLocaleString('en', { month: 'long' })}
                </option>
              ))}
            </select>
            <select
              value={year}
              onChange={(event) => setYear(Number(event.target.value))}
              className="date-input"
            >
              {Array.from({ length: 5 }, (_, index) => {
                const optionYear = now.getFullYear() - 2 + index;
                return <option key={optionYear} value={optionYear}>{optionYear}</option>;
              })}
            </select>
          </div>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>Loading revenue data...</div>
        ) : (
          <>
            <div className="stats-grid">
              {stats.map((stat) => (
                <div key={stat.label} className="stat-card">
                  <div className="stat-content">
                    <div className="stat-label">{stat.label}</div>
                    <div className="stat-value">{stat.value}</div>
                    <div className="stat-change">{stat.change}</div>
                  </div>
                </div>
              ))}
            </div>

            <div className="charts-grid">
              <div className="chart-card full-width">
                <h2>Daily Revenue - {new Date(2000, month - 1).toLocaleString('en', { month: 'long' })} {year}</h2>
                {chartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <LineChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                      <XAxis dataKey="date" stroke="#555" fontSize={11} />
                      <YAxis stroke="#555" fontSize={11} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: '#1f2330',
                          border: '1px solid rgba(255,255,255,0.1)',
                          borderRadius: '8px',
                          fontSize: '12px',
                        }}
                        labelStyle={{ color: '#edf0f5' }}
                      />
                      <Legend />
                      <Line
                        type="monotone"
                        dataKey="revenue"
                        stroke="#d4a853"
                        strokeWidth={2}
                        dot={{ fill: '#d4a853', r: 3 }}
                        activeDot={{ r: 5, fill: '#e5bc6a' }}
                        name="Revenue"
                      />
                    </LineChart>
                  </ResponsiveContainer>
                ) : (
                  <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>No revenue data for this period</div>
                )}
              </div>

              <div className="chart-card">
                <h2>Revenue Mix</h2>
                {revenueMixData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <BarChart data={revenueMixData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                      <XAxis dataKey="name" stroke="#555" fontSize={11} />
                      <YAxis stroke="#555" fontSize={11} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: '#1f2330',
                          border: '1px solid rgba(255,255,255,0.1)',
                          borderRadius: '8px',
                          fontSize: '12px',
                        }}
                        labelStyle={{ color: '#edf0f5' }}
                      />
                      <Legend />
                      <Bar dataKey="value" fill="#2d8a5e" name="Revenue" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>No invoice data</div>
                )}
              </div>

              <div className="chart-card">
                <h2>Top Tables</h2>
                {topTablesData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <BarChart data={topTablesData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                      <XAxis dataKey="name" stroke="#555" fontSize={11} />
                      <YAxis stroke="#555" fontSize={11} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: '#1f2330',
                          border: '1px solid rgba(255,255,255,0.1)',
                          borderRadius: '8px',
                          fontSize: '12px',
                        }}
                        labelStyle={{ color: '#edf0f5' }}
                      />
                      <Legend />
                      <Bar dataKey="revenue" fill="#d4a853" name="Revenue" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>No table revenue data</div>
                )}
              </div>
            </div>

            <div className="detailed-tables">
              <div className="detail-table-card">
                <h3>Top Performing Tables</h3>
                <table className="detail-table">
                  <thead>
                    <tr>
                      <th>Table</th>
                      <th>Invoices</th>
                      <th>Revenue</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(summary?.topTables || []).map((table) => (
                      <tr key={table.tableId}>
                        <td>{table.name}</td>
                        <td>{table.invoiceCount}</td>
                        <td className="revenue-value">{formatCurrency(table.totalRevenue)}</td>
                      </tr>
                    ))}
                    {(!summary?.topTables || summary.topTables.length === 0) && (
                      <tr><td colSpan={3} style={{ textAlign: 'center' }}>No data</td></tr>
                    )}
                  </tbody>
                </table>
              </div>

              <div className="detail-table-card">
                <h3>Daily Revenue Breakdown</h3>
                <table className="detail-table">
                  <thead>
                    <tr>
                      <th>Day</th>
                      <th>Invoices</th>
                      <th>Revenue</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(monthly?.dailyBreakdown || []).map((entry) => (
                      <tr key={entry.day}>
                        <td>{entry.day}</td>
                        <td>{entry.invoiceCount}</td>
                        <td className="revenue-value">{formatCurrency(entry.revenue)}</td>
                      </tr>
                    ))}
                    {(!monthly?.dailyBreakdown || monthly.dailyBreakdown.length === 0) && (
                      <tr><td colSpan={3} style={{ textAlign: 'center' }}>No data</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
      </div>
    </MainLayout>
  );
};

export default RevenueAnalytics;
