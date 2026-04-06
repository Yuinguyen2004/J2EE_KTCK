import React, { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, Search } from 'lucide-react';
import MainLayout from '../components/MainLayout';
import { getErrorMessage } from '../services/error';
import { fnbService, type FnbItem } from '../services/fnbService';
import type { User } from '../services/authService';
import type { AppPage } from '../utils/navigation';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/management.css';

interface FBManagementProps {
  onNavigate?: (page: AppPage) => void;
  onLogout?: () => void;
  user?: User | null;
}

export const FBManagement: React.FC<FBManagementProps> = ({
  onNavigate = () => {},
  onLogout = () => {},
  user,
}) => {
  const [fbItems, setFbItems] = useState<FnbItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editData, setEditData] = useState<Partial<FnbItem>>({});
  const [isAdding, setIsAdding] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    void (async () => {
      try {
        const items = await fnbService.getAll();
        setFbItems(items);
      } catch (loadError) {
        console.error('Failed to fetch menu items:', loadError);
        setError(getErrorMessage(loadError, 'Failed to load menu items'));
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filteredItems = fbItems.filter((item) => {
    const query = searchQuery.toLowerCase();
    return (
      item.name.toLowerCase().includes(query) ||
      item.description.toLowerCase().includes(query)
    );
  });

  const handleEdit = (item: FnbItem) => {
    setEditingId(item._id);
    setEditData({ ...item });
  };

  const handleSave = async () => {
    if (!editingId) {
      return;
    }

    try {
      const payload: Omit<FnbItem, '_id'> = {
        name: editData.name || '',
        description: editData.description || '',
        price: editData.price || 0,
        image: editData.image,
        isAvailable: editData.isAvailable !== false,
      };

      if (isAdding) {
        await fnbService.create(payload);
        setIsAdding(false);
      } else {
        await fnbService.update(editingId, payload);
      }

      setEditingId(null);
      setEditData({});
      setError('');
      const items = await fnbService.getAll();
      setFbItems(items);
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Failed to save item'));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Deactivate this item?')) {
      return;
    }

    try {
      await fnbService.delete(id);
      const items = await fnbService.getAll();
      setFbItems(items);
    } catch (deleteError) {
      setError(getErrorMessage(deleteError, 'Failed to deactivate item'));
    }
  };

  const handleAddNew = () => {
    const tempId = `new-${Date.now()}`;
    const newItem: FnbItem = {
      _id: tempId,
      name: '',
      description: '',
      price: 0,
      isAvailable: true,
    };

    setFbItems((currentItems) => [...currentItems, newItem]);
    setEditingId(tempId);
    setEditData(newItem);
    setIsAdding(true);
    setError('');
  };

  const handleCancelEdit = () => {
    if (isAdding) {
      setFbItems((currentItems) => currentItems.filter((item) => item._id !== editingId));
      setIsAdding(false);
    }

    setEditingId(null);
    setEditData({});
  };

  if (loading) {
    return (
      <MainLayout currentPage="fb-management" onNavigate={onNavigate} onLogout={onLogout} user={user} userName={user?.fullName || 'User'} userRole={user?.role || 'staff'}>
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-secondary)' }}>Loading menu items...</div>
      </MainLayout>
    );
  }

  return (
    <MainLayout
      currentPage="fb-management"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'User'}
      userRole={user?.role || 'staff'}
    >
      <div className="management-container">
        <div className="management-header">
          <div>
            <h1>Food & Beverage Management</h1>
            <p>Manage the existing server menu catalog, descriptions, and item availability.</p>
          </div>
          <button className="add-btn" onClick={handleAddNew}>
            <Plus size={20} />
            Add Item
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
              placeholder="Search by item name or description..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="search-input"
            />
          </div>
          <div className="filter-chips">
            <span className="filter-chip">Total Items: {fbItems.length}</span>
            <span className="filter-chip">Available: {fbItems.filter((item) => item.isAvailable).length}</span>
          </div>
        </div>

        <div className="data-table">
          <div className="table-header">
            <div className="header-cell">Item Name</div>
            <div className="header-cell">Description</div>
            <div className="header-cell">Price</div>
            <div className="header-cell">Status</div>
            <div className="header-cell">Actions</div>
          </div>

          {filteredItems.length > 0 ? (
            <div className="table-body">
              {filteredItems.map((item) => (
                <div key={item._id} className="table-row">
                  {editingId === item._id ? (
                    <>
                      <div className="cell">
                        <input
                          type="text"
                          value={editData.name || ''}
                          onChange={(event) => setEditData({ ...editData, name: event.target.value })}
                          className="edit-input"
                          placeholder="Item name"
                        />
                      </div>
                      <div className="cell">
                        <input
                          type="text"
                          value={editData.description || ''}
                          onChange={(event) => setEditData({ ...editData, description: event.target.value })}
                          className="edit-input"
                          placeholder="Description"
                        />
                      </div>
                      <div className="cell">
                        <input
                          type="number"
                          value={editData.price || 0}
                          onChange={(event) => setEditData({ ...editData, price: Number(event.target.value) })}
                          className="edit-input"
                          step="1000"
                        />
                      </div>
                      <div className="cell">
                        <select
                          value={editData.isAvailable ? 'available' : 'unavailable'}
                          onChange={(event) => setEditData({ ...editData, isAvailable: event.target.value === 'available' })}
                          className="edit-input"
                        >
                          <option value="available">Available</option>
                          <option value="unavailable">Unavailable</option>
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
                        <span className="cell-text">{item.name}</span>
                      </div>
                      <div className="cell">
                        <span className="cell-subtext">{item.description || 'No description'}</span>
                      </div>
                      <div className="cell">
                        <span className="price">{formatCurrency(item.price)}</span>
                      </div>
                      <div className="cell">
                        <span className={`status-badge status-${item.isAvailable ? 'available' : 'unavailable'}`}>
                          {item.isAvailable ? 'Available' : 'Unavailable'}
                        </span>
                      </div>
                      <div className="cell actions">
                        <button className="action-btn edit" title="Edit" onClick={() => handleEdit(item)}>
                          <Edit2 size={16} />
                        </button>
                        <button className="action-btn delete" title="Deactivate" onClick={() => handleDelete(item._id)}>
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
              <div className="empty-text">No menu items found for this filter.</div>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
};

export default FBManagement;
