import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Edit2, Trash2, Search, Power } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { getErrorMessage } from '../services/error';
import { tableService, type PricingRule, type Table, type TableType } from '../services/tableService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/management.css';

interface TableManagementProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

interface TableTypeDraft {
  name: string;
  description: string;
}

interface PricingRuleDraft {
  tableTypeId: string;
  blockMinutes: string;
  pricePerHour: string;
  sortOrder: string;
}

const STATUS_OPTIONS: Array<Table['status']> = ['available', 'reserved', 'maintenance', 'playing'];
const EMPTY_TABLE_TYPE_DRAFT: TableTypeDraft = { name: '', description: '' };
const EMPTY_PRICING_RULE_DRAFT: PricingRuleDraft = {
  tableTypeId: '',
  blockMinutes: '60',
  pricePerHour: '',
  sortOrder: '1',
};

const getTableTypeLabel = (tableTypeId: string, tableTypes: TableType[], fallback: string) =>
  tableTypes.find((tableType) => tableType.id === tableTypeId)?.name || fallback;

export const TableManagement: React.FC<TableManagementProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const [tables, setTables] = useState<Table[]>([]);
  const [tableTypes, setTableTypes] = useState<TableType[]>([]);
  const [pricingRules, setPricingRules] = useState<PricingRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editData, setEditData] = useState<Partial<Table>>({});
  const [isAdding, setIsAdding] = useState(false);
  const [editingTableTypeId, setEditingTableTypeId] = useState<string | null>(null);
  const [tableTypeDraft, setTableTypeDraft] = useState<TableTypeDraft>(EMPTY_TABLE_TYPE_DRAFT);
  const [isAddingTableType, setIsAddingTableType] = useState(false);
  const [editingPricingRuleId, setEditingPricingRuleId] = useState<string | null>(null);
  const [pricingRuleDraft, setPricingRuleDraft] = useState<PricingRuleDraft>(EMPTY_PRICING_RULE_DRAFT);
  const [isAddingPricingRule, setIsAddingPricingRule] = useState(false);
  const [error, setError] = useState('');
  const hasLoadedRef = useRef(false);
  const isAdmin = user?.role === 'admin';

  const activeTableTypes = useMemo(
    () => tableTypes.filter((tableType) => tableType.active),
    [tableTypes]
  );

  const sortedTableTypes = useMemo(
    () => [...tableTypes].sort((left, right) => {
      if (left.active !== right.active) {
        return Number(right.active) - Number(left.active);
      }

      return left.name.localeCompare(right.name);
    }),
    [tableTypes]
  );

  const sortedPricingRules = useMemo(
    () => [...pricingRules].sort((left, right) => {
      if (left.active !== right.active) {
        return Number(right.active) - Number(left.active);
      }

      if (left.tableTypeName !== right.tableTypeName) {
        return left.tableTypeName.localeCompare(right.tableTypeName);
      }

      if (left.sortOrder !== right.sortOrder) {
        return left.sortOrder - right.sortOrder;
      }

      return left.blockMinutes - right.blockMinutes;
    }),
    [pricingRules]
  );

  const loadManagementData = useCallback(async () => {
    try {
      const [tableData, tableTypeData, pricingRuleData] = await Promise.all([
        tableService.getAll({ includePricing: true }),
        tableService.getTableTypes(),
        tableService.getPricingRules(),
      ]);
      setTables(tableData);
      setTableTypes(tableTypeData);
      setPricingRules(pricingRuleData);
      setError('');
    } catch (loadError) {
      console.error('Failed to fetch tables:', loadError);
      setError(getErrorMessage(loadError, 'Failed to load tables'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (hasLoadedRef.current) {
      return;
    }

    hasLoadedRef.current = true;
    void loadManagementData();
  }, [loadManagementData]);

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
      const tableTypeId = editData.tableTypeId || activeTableTypes[0]?.id;
      if (!tableTypeId) {
        throw new Error('Create an active table type before saving tables.');
      }

      const payload: Omit<Table, '_id'> = {
        name: editData.name || '',
        type: getTableTypeLabel(tableTypeId, tableTypes, editData.type || ''),
        tableTypeId,
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
      await loadManagementData();
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
      await loadManagementData();
    } catch (deleteError) {
      setError(getErrorMessage(deleteError, 'Failed to deactivate table'));
    }
  };

  const handleAddNew = () => {
    const defaultType = activeTableTypes[0] || tableTypes[0];
    if (!defaultType) {
      setError('Create an active table type in the setup section before adding tables.');
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

  const resetTableTypeEditor = () => {
    setEditingTableTypeId(null);
    setTableTypeDraft(EMPTY_TABLE_TYPE_DRAFT);
    setIsAddingTableType(false);
  };

  const resetPricingRuleEditor = () => {
    setEditingPricingRuleId(null);
    setPricingRuleDraft(EMPTY_PRICING_RULE_DRAFT);
    setIsAddingPricingRule(false);
  };

  const handleAddTableType = () => {
    setEditingTableTypeId('new-table-type');
    setTableTypeDraft(EMPTY_TABLE_TYPE_DRAFT);
    setIsAddingTableType(true);
    setError('');
  };

  const handleAddPricingRule = () => {
    const defaultTableType = activeTableTypes[0] || tableTypes[0];
    if (!defaultTableType) {
      setError('Create a table type before adding pricing rules.');
      return;
    }

    setEditingPricingRuleId('new-pricing-rule');
    setPricingRuleDraft({
      ...EMPTY_PRICING_RULE_DRAFT,
      tableTypeId: defaultTableType.id,
    });
    setIsAddingPricingRule(true);
    setError('');
  };

  const handleEditTableType = (tableType: TableType) => {
    setEditingTableTypeId(tableType.id);
    setTableTypeDraft({
      name: tableType.name,
      description: tableType.description || '',
    });
    setIsAddingTableType(false);
    setError('');
  };

  const handleEditPricingRule = (pricingRule: PricingRule) => {
    setEditingPricingRuleId(pricingRule.id);
    setPricingRuleDraft({
      tableTypeId: pricingRule.tableTypeId,
      blockMinutes: String(pricingRule.blockMinutes),
      pricePerHour: String(pricingRule.pricePerMinute * 60),
      sortOrder: String(pricingRule.sortOrder),
    });
    setIsAddingPricingRule(false);
    setError('');
  };

  const handleSaveTableType = async () => {
    if (!editingTableTypeId) {
      return;
    }

    try {
      if (isAddingTableType) {
        await tableService.createTableType({
          name: tableTypeDraft.name,
          description: tableTypeDraft.description,
          active: true,
        });
      } else {
        const currentTableType = tableTypes.find((tableType) => tableType.id === editingTableTypeId);
        await tableService.updateTableType(editingTableTypeId, {
          name: tableTypeDraft.name,
          description: tableTypeDraft.description,
          active: currentTableType?.active,
        });
      }

      resetTableTypeEditor();
      await loadManagementData();
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Failed to save table type'));
    }
  };

  const handleSavePricingRule = async () => {
    if (!editingPricingRuleId) {
      return;
    }

    try {
      const payload = {
        tableTypeId: pricingRuleDraft.tableTypeId,
        blockMinutes: Number(pricingRuleDraft.blockMinutes),
        pricePerMinute: Number(pricingRuleDraft.pricePerHour) / 60,
        sortOrder: Number(pricingRuleDraft.sortOrder),
        active: true,
      };

      if (isAddingPricingRule) {
        await tableService.createPricingRule(payload);
      } else {
        const currentRule = pricingRules.find((pricingRule) => pricingRule.id === editingPricingRuleId);
        await tableService.updatePricingRule(editingPricingRuleId, {
          ...payload,
          active: currentRule?.active ?? true,
        });
      }

      resetPricingRuleEditor();
      await loadManagementData();
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Failed to save pricing rule'));
    }
  };

  const handleToggleTableTypeActive = async (tableType: TableType) => {
    const nextActive = !tableType.active;
    const actionLabel = nextActive ? 'activate' : 'deactivate';

    if (!window.confirm(`Do you want to ${actionLabel} the "${tableType.name}" table type?`)) {
      return;
    }

    try {
      await tableService.setTableTypeActive(tableType.id, nextActive);
      if (editingTableTypeId === tableType.id) {
        resetTableTypeEditor();
      }
      await loadManagementData();
    } catch (toggleError) {
      setError(getErrorMessage(toggleError, `Failed to ${actionLabel} table type`));
    }
  };

  const handleTogglePricingRuleActive = async (pricingRule: PricingRule) => {
    const nextActive = !pricingRule.active;
    const actionLabel = nextActive ? 'activate' : 'deactivate';

    if (!window.confirm(`Do you want to ${actionLabel} this pricing rule for "${pricingRule.tableTypeName}"?`)) {
      return;
    }

    try {
      await tableService.setPricingRuleActive(pricingRule.id, nextActive);
      if (editingPricingRuleId === pricingRule.id) {
        resetPricingRuleEditor();
      }
      await loadManagementData();
    } catch (toggleError) {
      setError(getErrorMessage(toggleError, `Failed to ${actionLabel} pricing rule`));
    }
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
          <div className="management-actions">
            {isAdmin && (
              <button className="add-btn add-btn-secondary" onClick={handleAddTableType}>
                <Plus size={20} />
                Add Table Type
              </button>
            )}
            {isAdmin && (
              <button className="add-btn add-btn-secondary" onClick={handleAddPricingRule}>
                <Plus size={20} />
                Add Pricing Rule
              </button>
            )}
            <button className="add-btn" onClick={handleAddNew}>
              <Plus size={20} />
              Add Table
            </button>
          </div>
        </div>

        {error && (
          <div style={{ color: '#ef4444', background: 'rgba(239,68,68,0.1)', padding: '12px', borderRadius: '8px', marginBottom: '16px' }}>
            {error}
            <button onClick={() => setError('')} style={{ float: 'right', background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>x</button>
          </div>
        )}

        {isAdmin && (
          <div className="management-section">
            <div className="section-heading">
              <div>
                <h2>Table Types</h2>
                <p>Create the categories used by tables and pricing rules so table setup is not blocked.</p>
              </div>
              <div className="filter-chips">
                <span className="filter-chip">Total: {tableTypes.length}</span>
                <span className="filter-chip">Active: {activeTableTypes.length}</span>
              </div>
            </div>

            <div className="data-table table-type-table">
              <div className="table-header">
                <div className="header-cell">Type Name</div>
                <div className="header-cell">Description</div>
                <div className="header-cell">Status</div>
                <div className="header-cell">Actions</div>
              </div>

              <div className="table-body">
                {editingTableTypeId === 'new-table-type' && (
                  <div className="table-row">
                    <div className="cell">
                      <input
                        type="text"
                        value={tableTypeDraft.name}
                        onChange={(event) =>
                          setTableTypeDraft((currentDraft) => ({ ...currentDraft, name: event.target.value }))
                        }
                        className="edit-input"
                        placeholder="Standard Table"
                      />
                    </div>
                    <div className="cell">
                      <input
                        type="text"
                        value={tableTypeDraft.description}
                        onChange={(event) =>
                          setTableTypeDraft((currentDraft) => ({
                            ...currentDraft,
                            description: event.target.value,
                          }))
                        }
                        className="edit-input"
                        placeholder="Optional description"
                      />
                    </div>
                    <div className="cell">
                      <span className="status-badge status-available">active on create</span>
                    </div>
                    <div className="cell actions">
                      <button className="action-save" onClick={handleSaveTableType}>Save</button>
                      <button className="action-cancel" onClick={resetTableTypeEditor}>Cancel</button>
                    </div>
                  </div>
                )}

                {sortedTableTypes.length > 0 ? (
                  sortedTableTypes.map((tableType) => (
                    <div key={tableType.id} className="table-row">
                      {editingTableTypeId === tableType.id ? (
                        <>
                          <div className="cell">
                            <input
                              type="text"
                              value={tableTypeDraft.name}
                              onChange={(event) =>
                                setTableTypeDraft((currentDraft) => ({ ...currentDraft, name: event.target.value }))
                              }
                              className="edit-input"
                              placeholder="Type name"
                            />
                          </div>
                          <div className="cell">
                            <input
                              type="text"
                              value={tableTypeDraft.description}
                              onChange={(event) =>
                                setTableTypeDraft((currentDraft) => ({
                                  ...currentDraft,
                                  description: event.target.value,
                                }))
                              }
                              className="edit-input"
                              placeholder="Optional description"
                            />
                          </div>
                          <div className="cell">
                            <span className={`status-badge ${tableType.active ? 'status-available' : 'status-unavailable'}`}>
                              {tableType.active ? 'active' : 'inactive'}
                            </span>
                          </div>
                          <div className="cell actions">
                            <button className="action-save" onClick={handleSaveTableType}>Save</button>
                            <button className="action-cancel" onClick={resetTableTypeEditor}>Cancel</button>
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="cell">
                            <span className="cell-text">{tableType.name}</span>
                          </div>
                          <div className="cell">
                            <span className="cell-text">{tableType.description || 'Used for table assignment and pricing rules.'}</span>
                          </div>
                          <div className="cell">
                            <span className={`status-badge ${tableType.active ? 'status-available' : 'status-unavailable'}`}>
                              {tableType.active ? 'active' : 'inactive'}
                            </span>
                          </div>
                          <div className="cell actions">
                            <button className="action-btn edit" title="Edit type" onClick={() => handleEditTableType(tableType)}>
                              <Edit2 size={16} />
                            </button>
                            <button
                              className="action-btn delete"
                              title={tableType.active ? 'Deactivate type' : 'Activate type'}
                              onClick={() => handleToggleTableTypeActive(tableType)}
                            >
                              <Power size={16} />
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  ))
                ) : (
                  <div className="empty-state">
                    <div className="empty-text">No table types yet. Create one here before adding tables.</div>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {isAdmin && (
          <div className="management-section">
            <div className="section-heading">
              <div>
                <h2>Pricing Rules</h2>
                <p>Each table type needs at least one active pricing rule or checkout will fail.</p>
              </div>
              <div className="filter-chips">
                <span className="filter-chip">Total: {pricingRules.length}</span>
                <span className="filter-chip">Active: {pricingRules.filter((pricingRule) => pricingRule.active).length}</span>
              </div>
            </div>

            <div className="data-table pricing-rule-table">
              <div className="table-header">
                <div className="header-cell">Table Type</div>
                <div className="header-cell">Block Minutes</div>
                <div className="header-cell">Rate / Hour</div>
                <div className="header-cell">Order</div>
                <div className="header-cell">Status</div>
                <div className="header-cell">Actions</div>
              </div>

              <div className="table-body">
                {editingPricingRuleId === 'new-pricing-rule' && (
                  <div className="table-row">
                    <div className="cell">
                      <select
                        value={pricingRuleDraft.tableTypeId}
                        onChange={(event) =>
                          setPricingRuleDraft((currentDraft) => ({ ...currentDraft, tableTypeId: event.target.value }))
                        }
                        className="edit-input"
                      >
                        {sortedTableTypes.map((tableType) => (
                          <option key={tableType.id} value={tableType.id}>
                            {tableType.name}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="cell">
                      <input
                        type="number"
                        min="1"
                        value={pricingRuleDraft.blockMinutes}
                        onChange={(event) =>
                          setPricingRuleDraft((currentDraft) => ({ ...currentDraft, blockMinutes: event.target.value }))
                        }
                        className="edit-input"
                        placeholder="60"
                      />
                    </div>
                    <div className="cell">
                      <input
                        type="number"
                        min="1"
                        step="1000"
                        value={pricingRuleDraft.pricePerHour}
                        onChange={(event) =>
                          setPricingRuleDraft((currentDraft) => ({ ...currentDraft, pricePerHour: event.target.value }))
                        }
                        className="edit-input"
                        placeholder="60000"
                      />
                    </div>
                    <div className="cell">
                      <input
                        type="number"
                        min="1"
                        value={pricingRuleDraft.sortOrder}
                        onChange={(event) =>
                          setPricingRuleDraft((currentDraft) => ({ ...currentDraft, sortOrder: event.target.value }))
                        }
                        className="edit-input"
                        placeholder="1"
                      />
                    </div>
                    <div className="cell">
                      <span className="status-badge status-available">active on create</span>
                    </div>
                    <div className="cell actions">
                      <button className="action-save" onClick={handleSavePricingRule}>Save</button>
                      <button className="action-cancel" onClick={resetPricingRuleEditor}>Cancel</button>
                    </div>
                  </div>
                )}

                {sortedPricingRules.length > 0 ? (
                  sortedPricingRules.map((pricingRule) => (
                    <div key={pricingRule.id} className="table-row">
                      {editingPricingRuleId === pricingRule.id ? (
                        <>
                          <div className="cell">
                            <select
                              value={pricingRuleDraft.tableTypeId}
                              onChange={(event) =>
                                setPricingRuleDraft((currentDraft) => ({ ...currentDraft, tableTypeId: event.target.value }))
                              }
                              className="edit-input"
                            >
                              {sortedTableTypes.map((tableType) => (
                                <option key={tableType.id} value={tableType.id}>
                                  {tableType.name}
                                </option>
                              ))}
                            </select>
                          </div>
                          <div className="cell">
                            <input
                              type="number"
                              min="1"
                              value={pricingRuleDraft.blockMinutes}
                              onChange={(event) =>
                                setPricingRuleDraft((currentDraft) => ({ ...currentDraft, blockMinutes: event.target.value }))
                              }
                              className="edit-input"
                            />
                          </div>
                          <div className="cell">
                            <input
                              type="number"
                              min="1"
                              step="1000"
                              value={pricingRuleDraft.pricePerHour}
                              onChange={(event) =>
                                setPricingRuleDraft((currentDraft) => ({ ...currentDraft, pricePerHour: event.target.value }))
                              }
                              className="edit-input"
                            />
                          </div>
                          <div className="cell">
                            <input
                              type="number"
                              min="1"
                              value={pricingRuleDraft.sortOrder}
                              onChange={(event) =>
                                setPricingRuleDraft((currentDraft) => ({ ...currentDraft, sortOrder: event.target.value }))
                              }
                              className="edit-input"
                            />
                          </div>
                          <div className="cell">
                            <span className={`status-badge ${pricingRule.active ? 'status-available' : 'status-unavailable'}`}>
                              {pricingRule.active ? 'active' : 'inactive'}
                            </span>
                          </div>
                          <div className="cell actions">
                            <button className="action-save" onClick={handleSavePricingRule}>Save</button>
                            <button className="action-cancel" onClick={resetPricingRuleEditor}>Cancel</button>
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="cell">
                            <span className="cell-text">{pricingRule.tableTypeName}</span>
                          </div>
                          <div className="cell">
                            <span className="cell-text">{pricingRule.blockMinutes} min</span>
                          </div>
                          <div className="cell">
                            <span className="price">{formatCurrency(pricingRule.pricePerMinute * 60)}</span>
                          </div>
                          <div className="cell">
                            <span className="cell-text">{pricingRule.sortOrder}</span>
                          </div>
                          <div className="cell">
                            <span className={`status-badge ${pricingRule.active ? 'status-available' : 'status-unavailable'}`}>
                              {pricingRule.active ? 'active' : 'inactive'}
                            </span>
                          </div>
                          <div className="cell actions">
                            <button className="action-btn edit" title="Edit pricing rule" onClick={() => handleEditPricingRule(pricingRule)}>
                              <Edit2 size={16} />
                            </button>
                            <button
                              className="action-btn delete"
                              title={pricingRule.active ? 'Deactivate pricing rule' : 'Activate pricing rule'}
                              onClick={() => handleTogglePricingRuleActive(pricingRule)}
                            >
                              <Power size={16} />
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  ))
                ) : (
                  <div className="empty-state">
                    <div className="empty-text">No pricing rules yet. Add one before starting or checking out sessions.</div>
                  </div>
                )}
              </div>
            </div>
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

        <div className="data-table table-management-table">
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
