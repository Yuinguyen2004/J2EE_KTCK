import React, { useEffect, useMemo, useState } from 'react';
import { Plus, Edit2, Trash2, Search } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { getErrorMessage } from '../services/error';
import { tableService, type Table, type TableType } from '../services/tableService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/management.css';

interface TableManagementProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

const STATUS_OPTIONS: Array<Table['status']> = ['available', 'reserved', 'maintenance', 'playing'];

const getTableTypeLabel = (tableTypeId: string, tableTypes: TableType[], fallback: string) =>
  tableTypes.find((tableType) => tableType.id === tableTypeId)?.name || fallback;

export const TableManagement: React.FC<TableManagementProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const [tables, setTables] = useState<Table[]>([]);
  const [tableTypes, setTableTypes] = useState<TableType[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editData, setEditData] = useState<Partial<Table>>({});
  const [isAdding, setIsAdding] = useState(false);
  const [error, setError] = useState('');

  const activeTableTypes = useMemo(
    () => tableTypes.filter((tableType) => tableType.active),
    [tableTypes]
  );

  useEffect(() => {
    void (async () => {
      try {
        const [tableData, tableTypeData] = await Promise.all([
          tableService.getAll({ includePricing: true }),
          tableService.getTableTypes(),
        ]);
        setTables(tableData);
        setTableTypes(tableTypeData);
      } catch (loadError) {
        console.error('Failed to fetch tables:', loadError);
        setError(getErrorMessage(loadError, 'Failed to load tables'));
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filteredTables = tables.filter((table) => {
    const query = searchQuery.toLowerCase();
    return (
      table.name.toLowerCase().includes(query) ||
      getTableTypeLabel(table.tableTypeId, tableTypes, table.type).toLowerCase().includes(query)
    );
  });

  const handleEdit = (table: Table) => {
    setEditingId(table._id);
    setEditData({ ...table });
  };

  const handleSave = async () => {
    if (!editingId) {
      return;
    }

    try {
      if (!editData.tableTypeId && activeTableTypes[0]) {
        editData.tableTypeId = activeTableTypes[0].id;
      }

      const payload: Omit<Table, '_id'> = {
        name: editData.name || '',
        type: getTableTypeLabel(editData.tableTypeId || '', tableTypes, editData.type || ''),
        tableTypeId: editData.tableTypeId || '',
        pricePerHour: editData.pricePerHour,
        status: editData.status || 'available',
        position: editData.position || { x: 0, y: 0 },
        active: editData.active ?? true,
      };

      if (isAdding) {
        await tableService.create(payload);
        setIsAdding(false);
      } else {
        await tableService.update(editingId, payload);
      }

      setEditingId(null);
      setEditData({});
      setError('');
      const [tableData, tableTypeData] = await Promise.all([
        tableService.getAll({ includePricing: true }),
        tableService.getTableTypes(),
      ]);
      setTables(tableData);
      setTableTypes(tableTypeData);
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Failed to save table'));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Deactivate this table?')) {
      return;
    }

    try {
      await tableService.delete(id);
      const [tableData, tableTypeData] = await Promise.all([
        tableService.getAll({ includePricing: true }),
        tableService.getTableTypes(),
      ]);
      setTables(tableData);
      setTableTypes(tableTypeData);
    } catch (deleteError) {
      setError(getErrorMessage(deleteError, 'Failed to deactivate table'));
    }
  };

  const handleAddNew = () => {
    const defaultType = activeTableTypes[0] || tableTypes[0];
    if (!defaultType) {
      setError('Create at least one active table type on the server before adding tables.');
      return;
    }

    const tempId = `new-${Date.now()}`;
    const newTable: Table = {
      _id: tempId,
      name: '',
      type: defaultType.name,
      tableTypeId: defaultType.id,
      pricePerHour: undefined,
      status: 'available',
      position: { x: 0, y: 0 },
      active: true,
    };

    setTables((currentTables) => [...currentTables, newTable]);
    setEditingId(tempId);
    setEditData(newTable);
    setIsAdding(true);
    setError('');
  };

  const handleCancelEdit = () => {
    if (isAdding) {
      setTables((currentTables) => currentTables.filter((table) => table._id !== editingId));
      setIsAdding(false);
    }

    setEditingId(null);
    setEditData({});
  };

  if (loading) {
    return (
      <MainLayout currentPage="table-management" onNavigate={onNavigate} onLogout={onLogout} user={user} userName={user?.fullName || 'User'} userRole={user?.role || 'staff'}>
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>Loading tables...</div>
      </MainLayout>
    );
  }

  return (
    <MainLayout
      currentPage="table-management"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="management-container">
        <div className="management-header">
          <div>
            <h1>Billiard Table Management</h1>
            <p>Manage table metadata, floor positions, and lifecycle status from the existing server model.</p>
          </div>
          <button className="add-btn" onClick={handleAddNew}>
            <Plus size={20} />
            Add Table
          </button>
        </div>

        {error && (
          <div style={{ color: '#ef4444', background: 'rgba(239,68,68,0.1)', padding: '12px', borderRadius: '8px', marginBottom: '16px' }}>
            {error}
            <button onClick={() => setError('')} style={{ float: 'right', background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>x</button>
          </div>
        )}

        <div className="search-section">
          <div className="search-input-wrapper">
            <Search size={20} />
            <input
              type="text"
              placeholder="Search by name or type..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="search-input"
            />
          </div>
          <div className="filter-chips">
            <span className="filter-chip">Total: {tables.length}</span>
            <span className="filter-chip">Active: {tables.filter((table) => table.active).length}</span>
          </div>
        </div>

        <div className="data-table">
          <div className="table-header">
            <div className="header-cell">Table Name</div>
            <div className="header-cell">Type</div>
            <div className="header-cell">Floor Position</div>
            <div className="header-cell">Derived Rate</div>
            <div className="header-cell">Status</div>
            <div className="header-cell">Actions</div>
          </div>

          {filteredTables.length > 0 ? (
            <div className="table-body">
              {filteredTables.map((table) => (
                <div key={table._id} className="table-row">
                  {editingId === table._id ? (
                    <>
                      <div className="cell">
                        <input
                          type="text"
                          value={editData.name || ''}
                          onChange={(event) => setEditData({ ...editData, name: event.target.value })}
                          className="edit-input"
                          placeholder="Table name"
                        />
                      </div>
                      <div className="cell">
                        <select
                          value={editData.tableTypeId || activeTableTypes[0]?.id || ''}
                          onChange={(event) => {
                            const selectedType = tableTypes.find((tableType) => tableType.id === event.target.value);
                            setEditData({
                              ...editData,
                              tableTypeId: event.target.value,
                              type: selectedType?.name || '',
                            });
                          }}
                          className="edit-input"
                        >
                          {activeTableTypes.map((tableType) => (
                            <option key={tableType.id} value={tableType.id}>
                              {tableType.name}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div className="cell">
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                          <input
                            type="number"
                            value={editData.position?.x ?? 0}
                            onChange={(event) => setEditData({
                              ...editData,
                              position: {
                                x: Number(event.target.value),
                                y: editData.position?.y ?? 0,
                              },
                            })}
                            className="edit-input"
                            placeholder="X"
                          />
                          <input
                            type="number"
                            value={editData.position?.y ?? 0}
                            onChange={(event) => setEditData({
                              ...editData,
                              position: {
                                x: editData.position?.x ?? 0,
                                y: Number(event.target.value),
                              },
                            })}
                            className="edit-input"
                            placeholder="Y"
                          />
                        </div>
                      </div>
                      <div className="cell">
                        <span className="price">
                          {typeof editData.pricePerHour === 'number' ? formatCurrency(editData.pricePerHour) : 'Set in pricing rules'}
                        </span>
                      </div>
                      <div className="cell">
                        <select
                          value={editData.status || 'available'}
                          onChange={(event) => setEditData({ ...editData, status: event.target.value as Table['status'] })}
                          className="edit-input"
                        >
                          {STATUS_OPTIONS.map((status) => (
                            <option key={status} value={status}>
                              {status}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div className="cell actions">
                        <button className="action-save" onClick={handleSave}>Save</button>
                        <button className="action-cancel" onClick={handleCancelEdit}>Cancel</button>
                      </div>
                    </>
                  ) : (
                    <>
                      <div className="cell">
                        <span className="cell-text">{table.name}</span>
                      </div>
                      <div className="cell">
                        <span className="badge">{getTableTypeLabel(table.tableTypeId, tableTypes, table.type)}</span>
                      </div>
                      <div className="cell">
                        <span className="cell-text">{table.position.x}, {table.position.y}</span>
                      </div>
                      <div className="cell">
                        <span className="price">
                          {typeof table.pricePerHour === 'number' ? formatCurrency(table.pricePerHour) : 'Pricing rule driven'}
                        </span>
                      </div>
                      <div className="cell">
                        <span className={`status-badge status-${table.status}`}>
                          {table.status}
                        </span>
                      </div>
                      <div className="cell actions">
                        <button className="action-btn edit" title="Edit" onClick={() => handleEdit(table)}>
                          <Edit2 size={16} />
                        </button>
                        <button className="action-btn delete" title="Deactivate" onClick={() => handleDelete(table._id)}>
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <div className="empty-text">No tables found matching your search.</div>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
};

export default TableManagement;
