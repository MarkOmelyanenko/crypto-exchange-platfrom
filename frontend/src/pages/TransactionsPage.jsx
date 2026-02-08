import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { list, getById } from '../shared/api/services/transactionsService';
import CryptoIcon from '../shared/components/CryptoIcon';

/* ──────────────────── helpers ──────────────────── */

const fmtUsd = (v, decimals = 2) => {
  if (v == null || isNaN(Number(v))) return '—';
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals, maximumFractionDigits: decimals,
  }).format(Number(v)) + ' USDT';
};

/** Format a value with its quote currency: "1,234.56 USDT" for USDT, "0.02947 BTC" otherwise */
const fmtQuote = (v, quoteAsset) => {
  if (v == null || isNaN(Number(v))) return '—';
  if (!quoteAsset || quoteAsset === 'USDT' || quoteAsset === 'USDC') {
    return fmtUsd(v);
  }
  const n = Number(v);
  // Use up to 8 decimal places for crypto-quoted values, strip trailing zeros
  return n.toFixed(8).replace(/\.?0+$/, '') + ' ' + quoteAsset;
};

const fmtQty = (v) => {
  if (v == null || isNaN(Number(v))) return '—';
  const n = Number(v);
  if (n === 0) return '0';
  return n.toFixed(8).replace(/\.?0+$/, '');
};

const fmtDate = (d) => {
  if (!d) return '—';
  return new Date(d).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
};

/* ──────────────────── main page ──────────────────── */

function TransactionsPage() {
  const navigate = useNavigate();
  const [transactions, setTransactions] = useState([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters
  const [symbol, setSymbol] = useState('');
  const [side, setSide] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize] = useState(15);
  const [sortField, setSortField] = useState('createdAt');
  const [sortDir, setSortDir] = useState('desc');

  // Detail modal
  const [selectedTx, setSelectedTx] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = { page, size: pageSize, sort: sortField, dir: sortDir };
      if (symbol.trim()) params.symbol = symbol.trim().toUpperCase();
      if (side) params.side = side;

      const data = await list(params);
      setTransactions(data.items || []);
      setTotal(data.total || 0);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load transactions.');
      setTransactions([]);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, symbol, side, sortField, sortDir]);

  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

  const handleSort = (field) => {
    if (sortField === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDir('desc');
    }
    setPage(0);
  };

  const handleFilter = () => {
    setPage(0);
    fetchTransactions();
  };

  const handleRowClick = async (tx) => {
    setDetailLoading(true);
    try {
      const detail = await getById(tx.id);
      setSelectedTx(detail);
    } catch {
      setSelectedTx(tx); // fallback to row data
    } finally {
      setDetailLoading(false);
    }
  };

  const sortIcon = (field) => {
    if (sortField !== field) return '';
    return sortDir === 'asc' ? ' ↑' : ' ↓';
  };

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto', overflow: 'hidden' }}>
      <h1 className="resp-page-title" style={{ fontSize: 28, fontWeight: 700, marginBottom: 20, color: '#111827' }}>Transactions</h1>

      {/* ─── Filters ─── */}
      <div className="resp-filter-bar">
        <div style={styles.filterGroup}>
          <label style={styles.filterLabel}>Symbol</label>
          <input
            type="text"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleFilter()}
            placeholder="e.g. BTC, ETH/BTC"
            style={{ ...styles.filterInput, width: '100%', minWidth: 100 }}
          />
        </div>
        <div style={styles.filterGroup}>
          <label style={styles.filterLabel}>Side</label>
          <select value={side} onChange={(e) => { setSide(e.target.value); setPage(0); }} style={{ ...styles.filterSelect, width: '100%' }}>
            <option value="">All</option>
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </div>
        <div className="filter-actions" style={{ display: 'flex', gap: 8 }}>
          <button onClick={handleFilter} style={styles.filterBtn}>Apply</button>
          <button onClick={() => { setSymbol(''); setSide(''); setPage(0); }} style={styles.clearFilterBtn}>Clear</button>
        </div>
      </div>

      {/* ─── Error ─── */}
      {error && (
        <div style={styles.errorBox}>
          <span>{error}</span>
          <button onClick={fetchTransactions} style={styles.retryBtn}>Retry</button>
        </div>
      )}

      {/* ─── Table ─── */}
      {loading && transactions.length === 0 ? (
        <Skeleton height={300} />
      ) : transactions.length === 0 ? (
        <EmptyState message="No transactions found. Start trading on the Assets page!" />
      ) : (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={styles.table}>
              <thead>
                <tr>
                  <Th align="left" sortable onClick={() => handleSort('createdAt')}>
                    Date{sortIcon('createdAt')}
                  </Th>
                  <Th align="left">Symbol</Th>
                  <Th align="center">Side</Th>
                  <Th align="right">Quantity</Th>
                  <Th align="right">Price</Th>
                  <Th align="right" sortable onClick={() => handleSort('totalUsd')}>
                    Total{sortIcon('totalUsd')}
                  </Th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((tx) => (
                  <tr
                    key={tx.id}
                    onClick={() => handleRowClick(tx)}
                    style={{ ...styles.tableRow, cursor: 'pointer' }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f9fafb'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
                  >
                    <td style={{ ...styles.td, textAlign: 'left', fontSize: 13, color: '#6b7280' }}>
                      {fmtDate(tx.createdAt)}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'left', fontWeight: 600 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <CryptoIcon symbol={tx.pairSymbol || tx.symbol} size={20} />
                        {tx.pairSymbol || tx.symbol}
                        {tx.source === 'TRADE' && (
                          <span style={{ marginLeft: 6, fontSize: 10, color: '#9ca3af', fontWeight: 400 }}>
                            SPOT
                          </span>
                        )}
                      </div>
                    </td>
                    <td style={{ ...styles.td, textAlign: 'center' }}>
                      <SideBadge side={tx.side} />
                    </td>
                    <td style={{ ...styles.td, textAlign: 'right', fontFamily: 'monospace' }}>
                      {fmtQty(tx.quantity)}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'right' }}>
                      {fmtQuote(tx.priceUsd, tx.quoteAsset)}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'right', fontWeight: 500 }}>
                      {fmtQuote(tx.totalUsd, tx.quoteAsset)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* ─── Pagination ─── */}
          <div style={styles.pagination}>
            <span style={{ fontSize: 13, color: '#6b7280' }}>
              Showing {page * pageSize + 1}–{Math.min((page + 1) * pageSize, total)} of {total}
            </span>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                style={{ ...styles.pageBtn, opacity: page === 0 ? 0.4 : 1 }}
              >
                ← Prev
              </button>
              <span style={{ padding: '6px 12px', fontSize: 13, color: '#374151' }}>
                Page {page + 1} of {totalPages || 1}
              </span>
              <button
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages - 1}
                style={{ ...styles.pageBtn, opacity: page >= totalPages - 1 ? 0.4 : 1 }}
              >
                Next →
              </button>
            </div>
          </div>
        </>
      )}

      {/* ─── Detail Modal ─── */}
      {selectedTx && (
        <DetailModal tx={selectedTx} loading={detailLoading} onClose={() => setSelectedTx(null)} />
      )}
    </div>
  );
}

/* ──────────────────── components ──────────────────── */

function SideBadge({ side }) {
  const isBuy = side === 'BUY';
  return (
    <span style={{
      display: 'inline-block', padding: '2px 10px', borderRadius: 4,
      fontSize: 11, fontWeight: 700,
      backgroundColor: isBuy ? '#d1fae5' : '#fee2e2',
      color: isBuy ? '#065f46' : '#991b1b',
    }}>
      {side}
    </span>
  );
}

function Th({ children, align = 'left', sortable, onClick }) {
  return (
    <th
      onClick={onClick}
      style={{
        padding: '10px 14px', textAlign: align, fontSize: 12, fontWeight: 600,
        color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em',
        borderBottom: '2px solid #e5e7eb', background: '#f9fafb',
        cursor: sortable ? 'pointer' : 'default', userSelect: 'none', whiteSpace: 'nowrap',
      }}
    >
      {children}
    </th>
  );
}

function DetailModal({ tx, loading, onClose }) {
  return (
    <div style={styles.overlay} onClick={onClose}>
      <div className="resp-modal" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h2 style={{ fontSize: 18, fontWeight: 600, margin: 0, color: '#111827' }}>Transaction Details</h2>
          <button onClick={onClose} style={styles.closeBtn}>×</button>
        </div>
        {loading ? (
          <Skeleton height={120} />
        ) : (
          <div className="resp-grid-detail">
            <DetailRow label="ID" value={tx.id} mono />
            <DetailRow label="Pair" value={tx.pairSymbol || tx.symbol} />
            <DetailRow label="Side" value={<SideBadge side={tx.side} />} />
            <DetailRow label="Quantity" value={fmtQty(tx.quantity)} mono />
            <DetailRow label="Price" value={fmtQuote(tx.priceUsd, tx.quoteAsset)} />
            <DetailRow label="Total" value={fmtQuote(tx.totalUsd, tx.quoteAsset)} />
            {tx.feeUsd != null && Number(tx.feeUsd) > 0 && (
              <DetailRow label="Fee" value={fmtUsd(tx.feeUsd)} />
            )}
            <DetailRow label="Date" value={fmtDate(tx.createdAt)} />
            {tx.source && (
              <DetailRow label="Source" value={tx.source === 'TRADE' ? 'Spot Trade' : 'Instant Buy/Sell'} />
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function DetailRow({ label, value, mono }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 2 }}>
        {label}
      </div>
      <div style={{
        fontSize: 14, fontWeight: 500, color: '#111827',
        fontFamily: mono ? 'monospace' : 'inherit',
        wordBreak: 'break-all',
      }}>
        {value}
      </div>
    </div>
  );
}

function EmptyState({ message }) {
  return (
    <div style={{
      padding: '48px 20px', textAlign: 'center', backgroundColor: '#f9fafb',
      borderRadius: 8, border: '1px dashed #d1d5db', color: '#6b7280', fontSize: 14,
    }}>
      <p>{message}</p>
    </div>
  );
}

function Skeleton({ height = 40 }) {
  return (
    <div style={{
      height, borderRadius: 8,
      background: 'linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%)',
      backgroundSize: '200% 100%', animation: 'shimmer 1.5s infinite',
    }}>
      <style>{`@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }`}</style>
    </div>
  );
}

/* ──────────────────── styles ──────────────────── */

const styles = {
  /* filterBar moved to CSS class resp-filter-bar */
  filterGroup: { display: 'flex', flexDirection: 'column', gap: 4 },
  filterLabel: { fontSize: 11, fontWeight: 600, color: '#6b7280', textTransform: 'uppercase' },
  filterInput: {
    padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: 4,
    fontSize: 13, width: 100, outline: 'none',
  },
  filterSelect: {
    padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: 4,
    fontSize: 13, minWidth: 80, outline: 'none', backgroundColor: '#fff',
  },
  filterBtn: {
    padding: '6px 16px', backgroundColor: '#3b82f6', color: '#fff',
    border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13, fontWeight: 500,
  },
  clearFilterBtn: {
    padding: '6px 16px', backgroundColor: '#f3f4f6', color: '#374151',
    border: '1px solid #d1d5db', borderRadius: 4, cursor: 'pointer', fontSize: 13, fontWeight: 500,
  },
  errorBox: {
    padding: 16, backgroundColor: '#fef2f2', borderRadius: 8, border: '1px solid #fecaca',
    display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16,
    color: '#dc2626', fontSize: 14,
  },
  retryBtn: {
    padding: '6px 14px', backgroundColor: '#dc2626', color: '#fff',
    border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13, fontWeight: 500,
  },
  table: {
    width: '100%', borderCollapse: 'collapse', backgroundColor: '#fff',
    borderRadius: 8, overflow: 'hidden', border: '1px solid #e5e7eb',
  },
  tableRow: {
    borderBottom: '1px solid #f3f4f6', transition: 'background-color 0.15s',
  },
  td: { padding: '10px 14px', fontSize: 14 },
  pagination: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '12px 0', flexWrap: 'wrap', gap: 8,
  },
  pageBtn: {
    padding: '6px 14px', backgroundColor: '#fff', color: '#374151',
    border: '1px solid #d1d5db', borderRadius: 4, cursor: 'pointer',
    fontSize: 13, fontWeight: 500,
  },
  overlay: {
    position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex',
    justifyContent: 'center', alignItems: 'center', zIndex: 1000,
  },
  /* modal moved to CSS class resp-modal */
  closeBtn: {
    background: 'none', border: 'none', fontSize: 24, cursor: 'pointer',
    color: '#6b7280', lineHeight: 1,
  },
};

export default TransactionsPage;
