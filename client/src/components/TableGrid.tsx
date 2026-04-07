import React, { useState, useEffect } from 'react';
import { Clock, Zap, AlertCircle } from 'lucide-react';
import TableActionModal from './TableActionModal';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/table-grid.css';

export interface TableData {
  id: string;
  name: string;
  type: string;
  pricePerHour?: number;
  currentTotal?: number;
  status: 'available' | 'playing' | 'reserved' | 'maintenance';
  startTime?: Date;
  sessionId?: string;
}

interface TableGridProps {
  tables?: TableData[];
  onTableUpdate?: () => void;
  interactive?: boolean;
}

export const TableGrid: React.FC<TableGridProps> = ({
  tables = [],
  onTableUpdate,
  interactive = true,
}) => {
  const [activeTables, setActiveTables] = useState<{ [key: string]: { elapsed: string; bill: number } }>({});
  const [selectedTable, setSelectedTable] = useState<TableData | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    const interval = setInterval(() => {
      const updated: { [key: string]: { elapsed: string; bill: number } } = {};

      tables.forEach((table) => {
        if (table.status === 'playing' && table.startTime) {
          const elapsedMs = Date.now() - new Date(table.startTime).getTime();
          const totalSeconds = Math.floor(elapsedMs / 1000);
          const hours = Math.floor(totalSeconds / 3600);
          const minutes = Math.floor((totalSeconds % 3600) / 60);
          const seconds = totalSeconds % 60;

          const timeStr = hours > 0
            ? `${hours}h ${minutes}m`
            : `${minutes}m ${seconds}s`;

          const billAmount = typeof table.pricePerHour === 'number'
            ? (totalSeconds / 3600) * table.pricePerHour
            : table.currentTotal || 0;

          updated[table.id] = {
            elapsed: timeStr,
            bill: billAmount,
          };
        }
      });

      setActiveTables(updated);
    }, 1000);

    return () => clearInterval(interval);
  }, [tables]);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'available': return 'green';
      case 'playing': return 'red';
      case 'reserved': return 'yellow';
      case 'maintenance': return 'grey';
      default: return 'grey';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'available': return 'Available';
      case 'playing': return 'In Use';
      case 'reserved': return 'Reserved';
      case 'maintenance': return 'Maintenance';
      default: return status;
    }
  };

  return (
    <>
      <div className="table-grid-container">
        <div className="table-grid">
          {tables.map((table) => {
            const tableTimer = activeTables[table.id];
            const statusColor = getStatusColor(table.status);

            return (
              <div key={table.id} className={`table-card status-${statusColor}`}>
                <div className="table-status-badge">
                  <span className={`status-dot status-${statusColor}`}></span>
                  <span className="status-text">{getStatusLabel(table.status)}</span>
                </div>

                <div className="table-card-content">
                  <h3 className="table-name">{table.name}</h3>
                  <p className="table-type">
                    {table.type}
                    {typeof table.pricePerHour === 'number' ? ` - ${formatCurrency(table.pricePerHour)}/h` : ''}
                  </p>

                  {table.status === 'playing' && tableTimer && (
                    <div className="table-timer">
                      <div className="timer-display">
                        <Clock size={16} />
                        <span className="timer-text">{tableTimer.elapsed}</span>
                      </div>
                      <div className="bill-display">
                        <Zap size={16} />
                        <span className="bill-amount">{formatCurrency(tableTimer.bill)}</span>
                      </div>
                    </div>
                  )}

                  {table.status === 'maintenance' && (
                    <div className="maintenance-alert">
                      <AlertCircle size={16} />
                      <span>Under Maintenance</span>
                    </div>
                  )}
                </div>

                {interactive && (
                  <button
                    className="table-action-btn"
                    title={`Manage ${table.name}`}
                    onClick={() => {
                      setSelectedTable(table);
                      setIsModalOpen(true);
                    }}
                  >
                    <span>→</span>
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {interactive && selectedTable && (
        <TableActionModal
          isOpen={isModalOpen}
          onClose={() => {
            setIsModalOpen(false);
            setSelectedTable(null);
          }}
          tableId={selectedTable.id}
          tableName={selectedTable.name}
          tableStatus={selectedTable.status}
          pricePerHour={selectedTable.pricePerHour}
          sessionId={selectedTable.sessionId}
          elapsedTime={activeTables[selectedTable.id]?.elapsed}
          billAmount={activeTables[selectedTable.id]?.bill}
          onTableUpdate={() => {
            setIsModalOpen(false);
            setSelectedTable(null);
            onTableUpdate?.();
          }}
        />
      )}
    </>
  );
};

export default TableGrid;
