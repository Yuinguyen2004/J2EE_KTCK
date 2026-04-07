import React, { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, Search, UserCheck, UserX } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { getErrorMessage } from '../services/error';
import {
  customerService,
  membershipService,
  userLookupService,
  type Customer,
  type MembershipTier,
  type CustomerUser,
} from '../services/customerService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/management.css';

interface CrmManagementProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

type CrmTab = 'customers' | 'memberships';

export const CrmManagement: React.FC<CrmManagementProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const isAdmin = user?.role === 'admin';

  const [activeTab, setActiveTab] = useState<CrmTab>('customers');
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [memberships, setMemberships] = useState<MembershipTier[]>([]);
  const [customerUsers, setCustomerUsers] = useState<CustomerUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState('');

  // Customer editing state
  const [editingCustomerId, setEditingCustomerId] = useState<string | null>(null);
  const [customerEditData, setCustomerEditData] = useState<{
    userId: string;
    membershipTierId: string;
    notes: string;
  }>({ userId: '', membershipTierId: '', notes: '' });
  const [isAddingCustomer, setIsAddingCustomer] = useState(false);

  // Membership editing state
  const [editingMembershipId, setEditingMembershipId] = useState<string | null>(null);
  const [membershipEditData, setMembershipEditData] = useState<{
    name: string;
    discountPercent: number;
    minimumSpend: number;
    description: string;
  }>({ name: '', discountPercent: 0, minimumSpend: 0, description: '' });
  const [isAddingMembership, setIsAddingMembership] = useState(false);

  useEffect(() => {
    void (async () => {
      try {
        const [customerList, membershipList] = await Promise.all([
          customerService.getAll(),
          membershipService.getAll(),
        ]);
        setCustomers(customerList);
        setMemberships(membershipList);

        if (isAdmin) {
          const users = await userLookupService.getCustomerUsers();
          setCustomerUsers(users);
        }
      } catch (loadError) {
        setError(getErrorMessage(loadError, 'Failed to load CRM data'));
      } finally {
        setLoading(false);
      }
    })();
  }, [isAdmin]);

  const reload = async () => {
    try {
      const [customerList, membershipList] = await Promise.all([
        customerService.getAll(),
        membershipService.getAll(),
      ]);
      setCustomers(customerList);
      setMemberships(membershipList);
    } catch (reloadError) {
      setError(getErrorMessage(reloadError, 'Failed to reload data'));
    }
  };

  // -- Customer handlers --

  const filteredCustomers = customers.filter((c) => {
    const query = searchQuery.toLowerCase();
    return (
      c.fullName.toLowerCase().includes(query) ||
      c.email.toLowerCase().includes(query) ||
      c.phone.toLowerCase().includes(query) ||
      (c.membershipTierName || '').toLowerCase().includes(query)
    );
  });

  const filteredMemberships = memberships.filter((m) => {
    const query = searchQuery.toLowerCase();
    return (
      m.name.toLowerCase().includes(query) ||
      m.description.toLowerCase().includes(query)
    );
  });

  const existingUserIds = new Set(customers.map((c) => c.userId));
  const availableUsers = customerUsers.filter((u) => !existingUserIds.has(u.id));

  const handleEditCustomer = (customer: Customer) => {
    setEditingCustomerId(customer.id);
    setCustomerEditData({
      userId: customer.userId,
      membershipTierId: customer.membershipTierId || '',
      notes: customer.notes,
    });
  };

  const handleSaveCustomer = async () => {
    if (!editingCustomerId) return;
    try {
      const payload = {
        userId: customerEditData.userId,
        membershipTierId: customerEditData.membershipTierId || null,
        notes: customerEditData.notes,
      };

      if (isAddingCustomer) {
        await customerService.create(payload);
        setIsAddingCustomer(false);
      } else {
        await customerService.update(editingCustomerId, payload);
      }

      setEditingCustomerId(null);
      setError('');
      await reload();
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Failed to save customer'));
    }
  };

  const handleToggleCustomerActive = async (customer: Customer) => {
    const action = customer.active ? 'deactivate' : 'activate';
    if (!window.confirm(`${action.charAt(0).toUpperCase() + action.slice(1)} customer ${customer.fullName}?`)) return;
    try {
      await customerService.setActive(customer.id, !customer.active);
      await reload();
    } catch (toggleError) {
      setError(getErrorMessage(toggleError, `Failed to ${action} customer`));
    }
  };

  const handleAddCustomer = () => {
    if (availableUsers.length === 0) {
      setError('No available customer accounts. Register a new customer account first.');
      return;
    }
    const tempId = `new-${Date.now()}`;
    setEditingCustomerId(tempId);
    setCustomerEditData({ userId: availableUsers[0].id, membershipTierId: '', notes: '' });
    setIsAddingCustomer(true);
    setError('');
  };

  const handleCancelCustomerEdit = () => {
    setEditingCustomerId(null);
    setIsAddingCustomer(false);
  };

  // -- Membership handlers (admin only) --

  const handleEditMembership = (tier: MembershipTier) => {
    setEditingMembershipId(tier.id);
    setMembershipEditData({
      name: tier.name,
      discountPercent: tier.discountPercent,
      minimumSpend: tier.minimumSpend,
      description: tier.description,
    });
  };

  const handleSaveMembership = async () => {
    if (!editingMembershipId) return;
    try {
      const payload = {
        name: membershipEditData.name,
        discountPercent: membershipEditData.discountPercent,
        minimumSpend: membershipEditData.minimumSpend,
        description: membershipEditData.description,
      };

      if (isAddingMembership) {
        await membershipService.create(payload);
        setIsAddingMembership(false);
      } else {
        await membershipService.update(editingMembershipId, payload);
      }

      setEditingMembershipId(null);
      setError('');
      await reload();
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Failed to save membership tier'));
    }
  };

  const handleToggleMembershipActive = async (tier: MembershipTier) => {
    const action = tier.active ? 'deactivate' : 'activate';
    if (!window.confirm(`${action.charAt(0).toUpperCase() + action.slice(1)} membership tier "${tier.name}"?`)) return;
    try {
      await membershipService.setActive(tier.id, !tier.active);
      await reload();
    } catch (toggleError) {
      setError(getErrorMessage(toggleError, `Failed to ${action} membership tier`));
    }
  };

  const handleAddMembership = () => {
    const tempId = `new-${Date.now()}`;
    setEditingMembershipId(tempId);
    setMembershipEditData({ name: '', discountPercent: 0, minimumSpend: 0, description: '' });
    setIsAddingMembership(true);
    setError('');
  };

  const handleCancelMembershipEdit = () => {
    setEditingMembershipId(null);
    setIsAddingMembership(false);
  };

  if (loading) {
    return (
      <MainLayout currentPage="crm" onNavigate={onNavigate} onLogout={onLogout} user={user} userName={user?.fullName || 'User'} userRole={user?.role || 'staff'}>
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>Loading CRM data...</div>
      </MainLayout>
    );
  }

  return (
    <MainLayout
      currentPage="crm"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="management-container">
        <div className="management-header">
          <div>
            <h1>Customer Relationship Management</h1>
            <p>Manage customer profiles{isAdmin ? ', membership tiers, and customer accounts' : ' and membership details'}.</p>
          </div>
        </div>

        {error && (
          <div style={{ color: '#ef4444', background: 'rgba(239,68,68,0.1)', padding: '12px', borderRadius: '8px', marginBottom: '16px' }}>
            {error}
            <button onClick={() => setError('')} style={{ float: 'right', background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>x</button>
          </div>
        )}

        {/* Tab Navigation */}
        <div className="category-filters" style={{ marginBottom: '20px' }}>
          <button
            className={`filter-chip-button ${activeTab === 'customers' ? 'active' : ''}`}
            onClick={() => { setActiveTab('customers'); setSearchQuery(''); }}
          >
            Customers ({customers.length})
          </button>
          {isAdmin && (
            <button
              className={`filter-chip-button ${activeTab === 'memberships' ? 'active' : ''}`}
              onClick={() => { setActiveTab('memberships'); setSearchQuery(''); }}
            >
              Membership Tiers ({memberships.length})
            </button>
          )}
        </div>

        {/* -- Customers Tab -- */}
        {activeTab === 'customers' && (
          <div className="management-section">
            <div className="section-heading">
              <div>
                <h2>Customer Profiles</h2>
                <p>View and manage customer profiles linked to registered accounts.</p>
              </div>
              {isAdmin && (
                <button className="add-btn" onClick={handleAddCustomer}>
                  <Plus size={20} />
                  Add Customer
                </button>
              )}
            </div>

            <div className="search-section">
              <div className="search-input-wrapper">
                <Search size={20} />
                <input
                  type="text"
                  placeholder="Search by name, email, phone, or membership..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="search-input"
                />
              </div>
              <div className="filter-chips">
                <span className="filter-chip">Total: {customers.length}</span>
                <span className="filter-chip">Active: {customers.filter((c) => c.active).length}</span>
              </div>
            </div>

            <div className="data-table crm-customer-table">
              <div className="table-header">
                <div className="header-cell">Name</div>
                <div className="header-cell">Email</div>
                <div className="header-cell">Phone</div>
                <div className="header-cell">Membership</div>
                <div className="header-cell">Status</div>
                <div className="header-cell">Actions</div>
              </div>

              {/* Inline add row */}
              {isAddingCustomer && (
                <div className="table-body">
                  <div className="table-row">
                    <div className="cell" style={{ gridColumn: 'span 3' }}>
                      <select
                        value={customerEditData.userId}
                        onChange={(e) => setCustomerEditData({ ...customerEditData, userId: e.target.value })}
                        className="edit-input"
                      >
                        {availableUsers.map((u) => (
                          <option key={u.id} value={u.id}>{u.fullName} ({u.email})</option>
                        ))}
                      </select>
                    </div>
                    <div className="cell">
                      <select
                        value={customerEditData.membershipTierId}
                        onChange={(e) => setCustomerEditData({ ...customerEditData, membershipTierId: e.target.value })}
                        className="edit-input"
                      >
                        <option value="">No Membership</option>
                        {memberships.filter((m) => m.active).map((m) => (
                          <option key={m.id} value={m.id}>{m.name}</option>
                        ))}
                      </select>
                    </div>
                    <div className="cell">
                      <input
                        type="text"
                        value={customerEditData.notes}
                        onChange={(e) => setCustomerEditData({ ...customerEditData, notes: e.target.value })}
                        className="edit-input"
                        placeholder="Notes"
                      />
                    </div>
                    <div className="cell actions">
                      <button className="action-save" onClick={handleSaveCustomer}>Save</button>
                      <button className="action-cancel" onClick={handleCancelCustomerEdit}>Cancel</button>
                    </div>
                  </div>
                </div>
              )}

              {filteredCustomers.length > 0 ? (
                <div className="table-body">
                  {filteredCustomers.map((customer) => (
                    <div key={customer.id} className="table-row">
                      {editingCustomerId === customer.id ? (
                        <>
                          <div className="cell"><span className="cell-text">{customer.fullName}</span></div>
                          <div className="cell"><span className="cell-subtext">{customer.email}</span></div>
                          <div className="cell"><span className="cell-subtext">{customer.phone || '-'}</span></div>
                          <div className="cell">
                            <select
                              value={customerEditData.membershipTierId}
                              onChange={(e) => setCustomerEditData({ ...customerEditData, membershipTierId: e.target.value })}
                              className="edit-input"
                            >
                              <option value="">No Membership</option>
                              {memberships.filter((m) => m.active).map((m) => (
                                <option key={m.id} value={m.id}>{m.name}</option>
                              ))}
                            </select>
                          </div>
                          <div className="cell">
                            <input
                              type="text"
                              value={customerEditData.notes}
                              onChange={(e) => setCustomerEditData({ ...customerEditData, notes: e.target.value })}
                              className="edit-input"
                              placeholder="Notes"
                            />
                          </div>
                          <div className="cell actions">
                            <button className="action-save" onClick={handleSaveCustomer}>Save</button>
                            <button className="action-cancel" onClick={handleCancelCustomerEdit}>Cancel</button>
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="cell"><span className="cell-text">{customer.fullName}</span></div>
                          <div className="cell"><span className="cell-subtext">{customer.email}</span></div>
                          <div className="cell"><span className="cell-subtext">{customer.phone || '-'}</span></div>
                          <div className="cell">
                            <span className="cell-subtext">{customer.membershipTierName || 'None'}</span>
                          </div>
                          <div className="cell">
                            <span className={`status-badge status-${customer.active ? 'available' : 'unavailable'}`}>
                              {customer.active ? 'Active' : 'Inactive'}
                            </span>
                          </div>
                          <div className="cell actions">
                            <button className="action-btn edit" title="Edit" onClick={() => handleEditCustomer(customer)}>
                              <Edit2 size={16} />
                            </button>
                            <button
                              className="action-btn delete"
                              title={customer.active ? 'Deactivate' : 'Activate'}
                              onClick={() => handleToggleCustomerActive(customer)}
                            >
                              {customer.active ? <UserX size={16} /> : <UserCheck size={16} />}
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty-state">
                  <div className="empty-text">
                    {searchQuery ? 'No customers match the current filter.' : 'No customer profiles yet.'}
                  </div>
                </div>
              )}
            </div>

            <div className="stats-footer">
              <div className="stat">
                <span className="stat-label">Total Customers</span>
                <span className="stat-value">{customers.length}</span>
              </div>
              <div className="stat">
                <span className="stat-label">Active</span>
                <span className="stat-value">{customers.filter((c) => c.active).length}</span>
              </div>
              <div className="stat">
                <span className="stat-label">With Membership</span>
                <span className="stat-value">{customers.filter((c) => c.membershipTierId).length}</span>
              </div>
            </div>
          </div>
        )}

        {/* -- Membership Tiers Tab (admin only) -- */}
        {activeTab === 'memberships' && isAdmin && (
          <div className="management-section">
            <div className="section-heading">
              <div>
                <h2>Membership Tiers</h2>
                <p>Define discount tiers and spending thresholds for customer memberships.</p>
              </div>
              <button className="add-btn" onClick={handleAddMembership}>
                <Plus size={20} />
                Add Tier
              </button>
            </div>

            <div className="search-section">
              <div className="search-input-wrapper">
                <Search size={20} />
                <input
                  type="text"
                  placeholder="Search membership tiers..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="search-input"
                />
              </div>
              <div className="filter-chips">
                <span className="filter-chip">Total: {memberships.length}</span>
                <span className="filter-chip">Active: {memberships.filter((m) => m.active).length}</span>
              </div>
            </div>

            <div className="data-table crm-membership-table">
              <div className="table-header">
                <div className="header-cell">Tier Name</div>
                <div className="header-cell">Discount %</div>
                <div className="header-cell">Min. Spend</div>
                <div className="header-cell">Description</div>
                <div className="header-cell">Status</div>
                <div className="header-cell">Actions</div>
              </div>

              {/* Inline add row */}
              {isAddingMembership && (
                <div className="table-body">
                  <div className="table-row">
                    <div className="cell">
                      <input
                        type="text"
                        value={membershipEditData.name}
                        onChange={(e) => setMembershipEditData({ ...membershipEditData, name: e.target.value })}
                        className="edit-input"
                        placeholder="Tier name"
                      />
                    </div>
                    <div className="cell">
                      <input
                        type="number"
                        value={membershipEditData.discountPercent}
                        onChange={(e) => setMembershipEditData({ ...membershipEditData, discountPercent: Number(e.target.value) })}
                        className="edit-input"
                        min={0}
                        max={100}
                        step={0.5}
                      />
                    </div>
                    <div className="cell">
                      <input
                        type="number"
                        value={membershipEditData.minimumSpend}
                        onChange={(e) => setMembershipEditData({ ...membershipEditData, minimumSpend: Number(e.target.value) })}
                        className="edit-input"
                        min={0}
                        step={10000}
                      />
                    </div>
                    <div className="cell">
                      <input
                        type="text"
                        value={membershipEditData.description}
                        onChange={(e) => setMembershipEditData({ ...membershipEditData, description: e.target.value })}
                        className="edit-input"
                        placeholder="Description"
                      />
                    </div>
                    <div className="cell" />
                    <div className="cell actions">
                      <button className="action-save" onClick={handleSaveMembership}>Save</button>
                      <button className="action-cancel" onClick={handleCancelMembershipEdit}>Cancel</button>
                    </div>
                  </div>
                </div>
              )}

              {filteredMemberships.length > 0 ? (
                <div className="table-body">
                  {filteredMemberships.map((tier) => (
                    <div key={tier.id} className="table-row">
                      {editingMembershipId === tier.id ? (
                        <>
                          <div className="cell">
                            <input
                              type="text"
                              value={membershipEditData.name}
                              onChange={(e) => setMembershipEditData({ ...membershipEditData, name: e.target.value })}
                              className="edit-input"
                              placeholder="Tier name"
                            />
                          </div>
                          <div className="cell">
                            <input
                              type="number"
                              value={membershipEditData.discountPercent}
                              onChange={(e) => setMembershipEditData({ ...membershipEditData, discountPercent: Number(e.target.value) })}
                              className="edit-input"
                              min={0}
                              max={100}
                              step={0.5}
                            />
                          </div>
                          <div className="cell">
                            <input
                              type="number"
                              value={membershipEditData.minimumSpend}
                              onChange={(e) => setMembershipEditData({ ...membershipEditData, minimumSpend: Number(e.target.value) })}
                              className="edit-input"
                              min={0}
                              step={10000}
                            />
                          </div>
                          <div className="cell">
                            <input
                              type="text"
                              value={membershipEditData.description}
                              onChange={(e) => setMembershipEditData({ ...membershipEditData, description: e.target.value })}
                              className="edit-input"
                              placeholder="Description"
                            />
                          </div>
                          <div className="cell" />
                          <div className="cell actions">
                            <button className="action-save" onClick={handleSaveMembership}>Save</button>
                            <button className="action-cancel" onClick={handleCancelMembershipEdit}>Cancel</button>
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="cell"><span className="cell-text">{tier.name}</span></div>
                          <div className="cell"><span className="cell-text">{tier.discountPercent}%</span></div>
                          <div className="cell"><span className="cell-text">{formatCurrency(tier.minimumSpend)}</span></div>
                          <div className="cell"><span className="cell-subtext">{tier.description || 'No description'}</span></div>
                          <div className="cell">
                            <span className={`status-badge status-${tier.active ? 'available' : 'unavailable'}`}>
                              {tier.active ? 'Active' : 'Inactive'}
                            </span>
                          </div>
                          <div className="cell actions">
                            <button className="action-btn edit" title="Edit" onClick={() => handleEditMembership(tier)}>
                              <Edit2 size={16} />
                            </button>
                            <button
                              className="action-btn delete"
                              title={tier.active ? 'Deactivate' : 'Activate'}
                              onClick={() => handleToggleMembershipActive(tier)}
                            >
                              {tier.active ? <Trash2 size={16} /> : <UserCheck size={16} />}
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty-state">
                  <div className="empty-text">
                    {searchQuery ? 'No membership tiers match the current filter.' : 'No membership tiers defined yet.'}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </MainLayout>
  );
};

export default CrmManagement;
