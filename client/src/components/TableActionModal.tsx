import React, { useEffect, useState } from 'react';
import { X, Plus, Minus, ShoppingCart } from 'lucide-react';
import { sessionService } from '../services/sessionService';
import { orderService } from '../services/orderService';
import { fnbService, type FnbItem } from '../services/fnbService';
import { getErrorMessage } from '../services/error';
import { formatCurrency } from '../utils/formatCurrency';
import '../styles/table-action-modal.css';

interface LocalOrderItem {
  fnbItemId: string;
  name: string;
  price: number;
  quantity: number;
}

interface TableActionModalProps {
  isOpen: boolean;
  onClose: () => void;
  tableId: string;
  tableName: string;
  tableStatus: string;
  pricePerHour?: number;
  sessionId?: string;
  elapsedTime?: string;
  billAmount?: number;
  onTableUpdate?: () => void;
}

export const TableActionModal: React.FC<TableActionModalProps> = ({
  isOpen,
  onClose,
  tableId,
  tableName,
  tableStatus,
  pricePerHour,
  sessionId,
  elapsedTime,
  billAmount,
  onTableUpdate,
}) => {
  const [currentOrder, setCurrentOrder] = useState<LocalOrderItem[]>([]);
  const [existingOrders, setExistingOrders] = useState<LocalOrderItem[]>([]);
  const [showFBModal, setShowFBModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [checkoutResult, setCheckoutResult] = useState<{
    duration: number;
    totalTableCost: number;
    totalFnbCost: number;
    totalAmount: number;
  } | null>(null);

  useEffect(() => {
    if (isOpen && sessionId && tableStatus === 'playing') {
      void (async () => {
        try {
          const orders = await orderService.getBySession(sessionId);
          setExistingOrders(orders.map((order) => ({
            fnbItemId: order.fnbItemId,
            name: order.name,
            price: order.price,
            quantity: order.quantity,
          })));
        } catch (error) {
          console.error('Failed to load orders:', error);
        }
      })();
    }
  }, [isOpen, sessionId, tableStatus]);

  const handleStartSession = async () => {
    setLoading(true);
    setError('');
    try {
      await sessionService.start(tableId);
      onTableUpdate?.();
    } catch (error: unknown) {
      setError(getErrorMessage(error, 'Failed to start session'));
    } finally {
      setLoading(false);
    }
  };

  const handleEndSession = async () => {
    if (!sessionId) return;
    setLoading(true);
    setError('');
    try {
      const result = await sessionService.end(sessionId);
      setCheckoutResult({
        duration: result.duration || 0,
        totalTableCost: result.totalTableCost || 0,
        totalFnbCost: result.totalFnbCost || 0,
        totalAmount: result.totalAmount || 0,
      });
    } catch (error: unknown) {
      setError(getErrorMessage(error, 'Failed to end session'));
    } finally {
      setLoading(false);
    }
  };

  const handleAddFnbItem = (item: LocalOrderItem) => {
    const existing = currentOrder.find(o => o.fnbItemId === item.fnbItemId);
    if (existing) {
      setCurrentOrder(
        currentOrder.map(o =>
          o.fnbItemId === item.fnbItemId ? { ...o, quantity: o.quantity + 1 } : o
        )
      );
    } else {
      setCurrentOrder([...currentOrder, { ...item, quantity: 1 }]);
    }
  };

  const handleQuantityChange = (fnbItemId: string, newQty: number) => {
    if (newQty <= 0) {
      setCurrentOrder(currentOrder.filter(o => o.fnbItemId !== fnbItemId));
    } else {
      setCurrentOrder(
        currentOrder.map(o =>
          o.fnbItemId === fnbItemId ? { ...o, quantity: newQty } : o
        )
      );
    }
  };

  const handleSubmitOrder = async () => {
    if (!sessionId || currentOrder.length === 0) return;
    setLoading(true);
    setError('');
    try {
      await orderService.createBatch(
        sessionId,
        currentOrder.map((item) => ({
          fnbItemId: item.fnbItemId,
          quantity: item.quantity,
        }))
      );
      setCurrentOrder([]);
      const orders = await orderService.getBySession(sessionId);
      setExistingOrders(orders.map((order) => ({
        fnbItemId: order.fnbItemId,
        name: order.name,
        price: order.price,
        quantity: order.quantity,
      })));
    } catch (error: unknown) {
      setError(getErrorMessage(error, 'Failed to submit order'));
    } finally {
      setLoading(false);
    }
  };

  const handleCloseReceipt = () => {
    setCheckoutResult(null);
    setExistingOrders([]);
    setCurrentOrder([]);
    onTableUpdate?.();
  };

  const newOrderTotal = currentOrder.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const existingOrderTotal = existingOrders.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const totalBill = (billAmount || 0) + existingOrderTotal + newOrderTotal;

  if (!isOpen) return null;

  if (checkoutResult) {
    return (
      <div className="modal-overlay" onClick={handleCloseReceipt}>
        <div className="table-action-modal" onClick={e => e.stopPropagation()}>
          <div className="modal-header">
            <div>
              <h2>{tableName} - Receipt</h2>
              <p className="table-status-info">Session Complete</p>
            </div>
            <button className="modal-close-btn" onClick={handleCloseReceipt}>
              <X size={24} />
            </button>
          </div>

          <div className="bill-summary">
            <div className="summary-row">
              <span>Duration</span>
              <span>{checkoutResult.duration} minutes</span>
            </div>
            <div className="summary-row">
              <span>Table Cost</span>
              <span>{formatCurrency(checkoutResult.totalTableCost)}</span>
            </div>
            <div className="summary-row">
              <span>F&B Cost</span>
              <span>{formatCurrency(checkoutResult.totalFnbCost)}</span>
            </div>
            <div className="summary-row total">
              <span>Total Amount</span>
              <span>{formatCurrency(checkoutResult.totalAmount)}</span>
            </div>
          </div>

          <div className="modal-actions">
            <button className="table-modal-action-btn table-modal-action-btn--primary" onClick={handleCloseReceipt}>
              Done
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
        <div className="table-action-modal" onClick={e => e.stopPropagation()}>
          <div className="modal-header">
            <div>
              <h2>{tableName}</h2>
              <p className="table-status-info">
                Status: {tableStatus}
                {typeof pricePerHour === 'number' ? ` | ${formatCurrency(pricePerHour)}/h` : ''}
              </p>
            </div>
          <button className="modal-close-btn" onClick={onClose}>
            <X size={24} />
          </button>
        </div>

        {error && (
          <div style={{ color: '#ef4444', background: 'rgba(239,68,68,0.1)', padding: '12px', borderRadius: '8px', margin: '0 0 16px' }}>
            {error}
          </div>
        )}

        {tableStatus === 'playing' && (
          <div className="table-info-section">
            <div className="info-item">
              <span className="info-label">Time Elapsed</span>
              <span className="info-value">{elapsedTime || '0m'}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Table Cost (est.)</span>
              <span className="info-value">{formatCurrency(billAmount || 0)}</span>
            </div>
          </div>
        )}

        {existingOrders.length > 0 && (
          <div className="order-section">
            <h3>Ordered Items</h3>
            <div className="order-items">
              {existingOrders.map((item, idx) => (
                <div key={idx} className="order-item">
                  <div className="item-details">
                    <div className="item-name">{item.name}</div>
                    <div className="item-price">{formatCurrency(item.price)}</div>
                  </div>
                  <div className="item-quantity-control">
                    <span className="qty-value">x{item.quantity}</span>
                  </div>
                  <div className="item-total">{formatCurrency(item.price * item.quantity)}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {currentOrder.length > 0 && (
          <div className="order-section">
            <h3>New Order</h3>
            <div className="order-items">
              {currentOrder.map(item => (
                <div key={item.fnbItemId} className="order-item">
                  <div className="item-details">
                    <div className="item-name">{item.name}</div>
                    <div className="item-price">{formatCurrency(item.price)}</div>
                  </div>
                  <div className="item-quantity-control">
                    <button className="qty-btn" onClick={() => handleQuantityChange(item.fnbItemId, item.quantity - 1)}>
                      <Minus size={16} />
                    </button>
                    <span className="qty-value">{item.quantity}</span>
                    <button className="qty-btn" onClick={() => handleQuantityChange(item.fnbItemId, item.quantity + 1)}>
                      <Plus size={16} />
                    </button>
                  </div>
                  <div className="item-total">{formatCurrency(item.price * item.quantity)}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {tableStatus === 'playing' && (
          <div className="bill-summary">
            <div className="summary-row">
              <span>Table Cost (est.)</span>
              <span>{formatCurrency(billAmount || 0)}</span>
            </div>
            {existingOrderTotal > 0 && (
              <div className="summary-row">
                <span>Existing F&B</span>
                <span>{formatCurrency(existingOrderTotal)}</span>
              </div>
            )}
            {newOrderTotal > 0 && (
              <div className="summary-row">
                <span>New F&B</span>
                <span>{formatCurrency(newOrderTotal)}</span>
              </div>
            )}
            <div className="summary-row total">
              <span>Estimated Total</span>
              <span>{formatCurrency(totalBill)}</span>
            </div>
          </div>
        )}

        <div className="modal-actions">
          {tableStatus === 'available' && (
            <button
              className="table-modal-action-btn table-modal-action-btn--primary"
              onClick={handleStartSession}
              disabled={loading}
            >
              {loading ? 'Starting...' : 'Start Session'}
            </button>
          )}

          {tableStatus === 'playing' && (
            <>
              <button
                className="table-modal-action-btn table-modal-action-btn--secondary"
                onClick={() => setShowFBModal(true)}
              >
                <ShoppingCart size={18} />
                Add F&B
              </button>
              {currentOrder.length > 0 && (
                <button
                  className="table-modal-action-btn table-modal-action-btn--secondary"
                  onClick={handleSubmitOrder}
                  disabled={loading}
                >
                  {loading ? 'Submitting...' : 'Submit Order'}
                </button>
              )}
              <button
                className="table-modal-action-btn table-modal-action-btn--primary"
                onClick={handleEndSession}
                disabled={loading}
              >
                {loading ? 'Closing...' : 'Complete & Checkout'}
              </button>
            </>
          )}
        </div>
      </div>

      {showFBModal && (
        <FBMenuModal
          isOpen={showFBModal}
          onClose={() => setShowFBModal(false)}
          onSelectItem={handleAddFnbItem}
        />
      )}
    </div>
  );
};

interface FBMenuModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectItem: (item: LocalOrderItem) => void;
}

const FBMenuModal: React.FC<FBMenuModalProps> = ({ isOpen, onClose, onSelectItem }) => {
  const [menuItems, setMenuItems] = useState<FnbItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (isOpen) {
      fnbService.getAll()
        .then(items => setMenuItems(items.filter(i => i.isAvailable)))
        .catch(err => console.error('Failed to load menu:', err))
        .finally(() => setLoading(false));
    }
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="fb-menu-modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Order Food & Beverages</h2>
          <button className="modal-close-btn" onClick={onClose}>
            <X size={24} />
          </button>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-secondary)' }}>Loading menu...</div>
        ) : (
          <div className="menu-grid">
            {menuItems.map(item => (
              <button
                key={item._id}
                className="menu-item"
                onClick={() => {
                  onSelectItem({
                    fnbItemId: item._id,
                    name: item.name,
                    price: item.price,
                    quantity: 1,
                  });
                }}
              >
                <div className="item-info">
                  <div className="item-name">{item.name}</div>
                  <div className="item-price">{formatCurrency(item.price)}</div>
                </div>
                <div className="item-add-btn">+</div>
              </button>
            ))}
          </div>
        )}

        <div className="modal-footer">
          <button className="close-menu-btn" onClick={onClose}>
            Close Menu
          </button>
        </div>
      </div>
    </div>
  );
};

export default TableActionModal;
