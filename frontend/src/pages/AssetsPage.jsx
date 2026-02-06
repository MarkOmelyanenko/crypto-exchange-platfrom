import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { list } from '../shared/api/services/assetsService';
import { usePriceStream } from '../shared/hooks/usePriceStream';

/* ──────────────────── helpers ──────────────────── */

const fmt = (v, decimals = 2) => {
  if (v == null || isNaN(Number(v))) return '—';
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals, maximumFractionDigits: decimals,
  }).format(Number(v)) + ' USDT';
};

const fmtPct = (v) => {
  if (v == null || isNaN(Number(v))) return '—';
  const n = Number(v);
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`;
};

const pctColor = (v) => {
  if (v == null) return '#6b7280';
  const n = Number(v);
  if (n > 0) return '#10b981';
  if (n < 0) return '#ef4444';
  return '#6b7280';
};

const fmtDate = (d) => {
  if (!d) return '—';
  return new Date(d).toLocaleTimeString(undefined, {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
};

/* ──────────────────── main page ──────────────────── */

function AssetsPage() {
  const navigate = useNavigate();
  const [assets, setAssets] = useState([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState('symbol');
  const [dir, setDir] = useState('asc');
  const [page, setPage] = useState(0);
  const pageSize = 20;
  const debounceRef = useRef(null);

  // Live price stream: subscribe to symbols on the current page
  const symbols = useMemo(
    () => assets.filter(a => a.symbol !== 'USDT').map(a => a.symbol),
    [assets]
  );
  const { prices: livePrices, connected: liveConnected, error: liveError } = usePriceStream(symbols);

  const fetchAssets = useCallback(async (params = {}) => {
    setLoading(true);
    setError('');
    try {
      const data = await list({
        q: params.q || undefined,
        sort: params.sort || sort,
        dir: params.dir || dir,
        page: params.page ?? page,
        size: pageSize,
      });
      setAssets(data.items || []);
      setTotal(data.total || 0);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load assets.');
    } finally {
      setLoading(false);
    }
  }, [sort, dir, page]);

  // Initial load & reload when sort/dir/page changes
  useEffect(() => {
    fetchAssets({ q: search, sort, dir, page });
  }, [sort, dir, page]); // eslint-disable-line react-hooks/exhaustive-deps

  // Debounced search
  const handleSearchChange = (e) => {
    const value = e.target.value;
    setSearch(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setPage(0);
      fetchAssets({ q: value, sort, dir, page: 0 });
    }, 300);
  };

  const handleSort = (field) => {
    if (sort === field) {
      setDir(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSort(field);
      setDir('asc');
    }
    setPage(0);
  };

  const sortIcon = (field) => {
    if (sort !== field) return '';
    return dir === 'asc' ? ' ↑' : ' ↓';
  };

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <h1 style={{ fontSize: 28, fontWeight: 700, color: '#111827', margin: 0 }}>Assets</h1>
          <LiveIndicator connected={liveConnected} error={liveError} />
        </div>
        <div style={{ position: 'relative' }}>
          <input
            type="text"
            placeholder="Search by symbol or name…"
            value={search}
            onChange={handleSearchChange}
            style={styles.searchInput}
          />
          {search && (
            <button
              onClick={() => { setSearch(''); setPage(0); fetchAssets({ q: '', sort, dir, page: 0 }); }}
              style={styles.clearBtn}
              title="Clear search"
            >×</button>
          )}
        </div>
      </div>

      {error ? (
        <ErrorBox message={error} onRetry={() => fetchAssets({ q: search, sort, dir, page })} />
      ) : loading && assets.length === 0 ? (
        <Skeleton height={300} />
      ) : assets.length === 0 ? (
        <EmptyState message={search ? `No assets matching "${search}"` : 'No assets found.'} />
      ) : (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={styles.table}>
              <thead>
                <tr>
                  <Th align="left" sortable onClick={() => handleSort('symbol')}>
                    Symbol{sortIcon('symbol')}
                  </Th>
                  <Th align="left" sortable onClick={() => handleSort('name')}>
                    Name{sortIcon('name')}
                  </Th>
                  <Th align="right" sortable onClick={() => handleSort('price')}>
                    Price{sortIcon('price')}
                  </Th>
                  <Th align="right" sortable onClick={() => handleSort('change24h')}>
                    24h Change{sortIcon('change24h')}
                  </Th>
                  <Th align="right">Updated</Th>
                </tr>
              </thead>
              <tbody>
                {assets.map((asset) => {
                  const live = livePrices[asset.symbol];
                  const price = live?.priceUsd ?? asset.priceUsd;
                  const change = live?.change24hPercent ?? asset.change24hPercent;
                  const unavailable = !live && asset.priceUnavailable;
                  const ts = live?.ts ?? asset.updatedAt;
                  return (
                  <tr
                    key={asset.id}
                    style={styles.tableRow}
                    onClick={() => navigate(`/assets/${asset.symbol}`)}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f9fafb'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = ''}
                  >
                    <td style={{ ...styles.td, textAlign: 'left', fontWeight: 600, color: '#111827' }}>
                      {asset.symbol}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'left', color: '#6b7280' }}>
                      {asset.name}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'right', fontFamily: 'monospace' }}>
                      {unavailable
                        ? <span title="Price data temporarily unavailable" style={{ color: '#9ca3af' }}>—</span>
                        : fmt(price)
                      }
                    </td>
                    <td style={{
                      ...styles.td, textAlign: 'right', fontWeight: 500,
                      color: pctColor(change),
                    }}>
                      {unavailable
                        ? <span title="Price data temporarily unavailable" style={{ color: '#9ca3af' }}>—</span>
                        : fmtPct(change)
                      }
                    </td>
                    <td style={{ ...styles.td, textAlign: 'right', color: '#9ca3af', fontSize: 13 }}>
                      {fmtDate(ts)}
                    </td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div style={styles.pagination}>
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                style={{ ...styles.pageBtn, opacity: page === 0 ? 0.4 : 1 }}
              >
                ← Prev
              </button>
              <span style={{ fontSize: 14, color: '#6b7280' }}>
                Page {page + 1} of {totalPages} ({total} assets)
              </span>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                style={{ ...styles.pageBtn, opacity: page >= totalPages - 1 ? 0.4 : 1 }}
              >
                Next →
              </button>
            </div>
          )}

          {loading && (
            <div style={{ textAlign: 'center', padding: 8, color: '#9ca3af', fontSize: 13 }}>
              Refreshing…
            </div>
          )}
        </>
      )}
    </div>
  );
}

/* ──────────────────── shared components ──────────────────── */

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

function ErrorBox({ message, onRetry }) {
  return (
    <div style={{
      padding: 16, backgroundColor: '#fef2f2', borderRadius: 8, border: '1px solid #fecaca',
      display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16,
    }}>
      <span style={{ color: '#dc2626', fontSize: 14 }}>{message}</span>
      <button onClick={onRetry} style={styles.retryBtn}>Retry</button>
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

function LiveIndicator({ connected, error }) {
  if (error) {
    return (
      <span style={{ fontSize: 12, color: '#f59e0b', display: 'flex', alignItems: 'center', gap: 4 }}>
        <span style={{ width: 7, height: 7, borderRadius: '50%', backgroundColor: '#f59e0b', display: 'inline-block' }} />
        {error}
      </span>
    );
  }
  if (connected) {
    return (
      <span style={{ fontSize: 12, color: '#10b981', display: 'flex', alignItems: 'center', gap: 4 }}>
        <span style={{
          width: 7, height: 7, borderRadius: '50%', backgroundColor: '#10b981',
          display: 'inline-block', animation: 'livePulse 2s infinite',
        }} />
        Live
        <style>{`@keyframes livePulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }`}</style>
      </span>
    );
  }
  return (
    <span style={{ fontSize: 12, color: '#9ca3af', display: 'flex', alignItems: 'center', gap: 4 }}>
      <span style={{ width: 7, height: 7, borderRadius: '50%', backgroundColor: '#9ca3af', display: 'inline-block' }} />
      Connecting…
    </span>
  );
}

/* ──────────────────── styles ──────────────────── */

const styles = {
  searchInput: {
    padding: '8px 36px 8px 14px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    width: 260,
    outline: 'none',
    transition: 'border-color 0.15s',
  },
  clearBtn: {
    position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)',
    background: 'none', border: 'none', fontSize: 18, color: '#9ca3af',
    cursor: 'pointer', padding: '0 4px', lineHeight: 1,
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    backgroundColor: '#fff',
    borderRadius: 8,
    overflow: 'hidden',
    border: '1px solid #e5e7eb',
  },
  tableRow: {
    borderBottom: '1px solid #f3f4f6',
    cursor: 'pointer',
    transition: 'background-color 0.15s',
  },
  td: {
    padding: '12px 14px',
    fontSize: 14,
  },
  pagination: {
    display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 16,
    padding: '16px 0',
  },
  pageBtn: {
    padding: '6px 14px', backgroundColor: '#fff', border: '1px solid #d1d5db',
    borderRadius: 6, cursor: 'pointer', fontSize: 13, fontWeight: 500, color: '#374151',
  },
  retryBtn: {
    padding: '6px 14px', backgroundColor: '#dc2626', color: '#fff',
    border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13, fontWeight: 500,
  },
};

export default AssetsPage;
