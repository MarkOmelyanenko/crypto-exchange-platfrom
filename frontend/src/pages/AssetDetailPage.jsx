import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, Link, useOutletContext } from 'react-router-dom';
import { getBySymbol, getMyPosition } from '../shared/api/services/assetsService';
import { getHistory } from '../shared/api/services/priceService';
import { create as createTransaction } from '../shared/api/services/transactionsService';
import { getWalletBalances, getCashBalance } from '../shared/api/services/walletService';
import { usePriceStream } from '../shared/hooks/usePriceStream';
import CryptoIcon from '../shared/components/CryptoIcon';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';

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

const fmtQty = (v, maxDecimals = 8) => {
  if (v == null || isNaN(Number(v))) return '0';
  const n = Number(v);
  if (n === 0) return '0';
  return n.toFixed(maxDecimals).replace(/\.?0+$/, '');
};

const RANGES = [
  { label: '24h', value: '24h' },
  { label: '7d', value: '7d' },
  { label: '30d', value: '30d' },
];

/* ──────────────────── main page ──────────────────── */

function AssetDetailPage() {
  const { symbol } = useParams();
  const { refreshCashBalance } = useOutletContext() || {};
  const [asset, setAsset] = useState(null);
  const [position, setPosition] = useState(null);
  const [history, setHistory] = useState([]);
  const [range, setRange] = useState('24h');
  const [loading, setLoading] = useState({ asset: true, chart: true, position: true });
  const [errors, setErrors] = useState({});

  // Live price stream for this single asset
  const streamSymbols = useMemo(() => symbol ? [symbol.toUpperCase()] : [], [symbol]);
  const { prices: livePrices, connected: liveConnected, error: liveError } = usePriceStream(streamSymbols);
  const livePrice = livePrices[symbol?.toUpperCase()];

  // Merge live price into asset data for display
  const displayPrice = livePrice?.priceUsd ?? asset?.priceUsd;
  const displayChange = livePrice?.change24hPercent ?? asset?.change24hPercent;
  const displayUnavailable = !livePrice && asset?.priceUnavailable;

  const loadAsset = useCallback(async () => {
    setLoading(prev => ({ ...prev, asset: true }));
    setErrors(prev => ({ ...prev, asset: null }));
    try {
      const data = await getBySymbol(symbol);
      setAsset(data);
    } catch (err) {
      if (err.response?.status === 404) {
        setErrors(prev => ({ ...prev, asset: `Asset "${symbol}" not found` }));
      } else {
        setErrors(prev => ({ ...prev, asset: err.response?.data?.message || 'Failed to load asset details' }));
      }
    } finally {
      setLoading(prev => ({ ...prev, asset: false }));
    }
  }, [symbol]);

  const loadHistory = useCallback(async (r) => {
    setLoading(prev => ({ ...prev, chart: true }));
    try {
      const data = await getHistory(symbol, r);
      setHistory(Array.isArray(data) ? data : []);
    } catch {
      setHistory([]);
    } finally {
      setLoading(prev => ({ ...prev, chart: false }));
    }
  }, [symbol]);

  const loadPosition = useCallback(async () => {
    setLoading(prev => ({ ...prev, position: true }));
    try {
      const data = await getMyPosition(symbol);
      setPosition(data);
    } catch {
      setPosition(null);
    } finally {
      setLoading(prev => ({ ...prev, position: false }));
    }
  }, [symbol]);

  useEffect(() => {
    loadAsset();
    loadPosition();
  }, [loadAsset, loadPosition]);

  useEffect(() => {
    loadHistory(range);
  }, [range, loadHistory]);

  const handleRangeChange = (r) => {
    setRange(r);
  };

  const handleTradeSuccess = () => {
    // Refresh position and asset data after a successful trade
    loadPosition();
    loadAsset();
    // Refresh the header USDT balance
    if (refreshCashBalance) refreshCashBalance();
  };

  if (errors.asset) {
    return (
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <Link to="/assets" style={styles.backLink}>← Back to Assets</Link>
        <ErrorBox message={errors.asset} onRetry={loadAsset} />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <Link to="/assets" style={styles.backLink}>← Back to Assets</Link>

      {/* ─── Asset Header ─── */}
      {loading.asset && !asset ? (
        <Skeleton height={80} />
      ) : asset ? (
        <div style={styles.headerCard}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
                <CryptoIcon symbol={asset.symbol} size={32} />
                <h1 style={{ fontSize: 28, fontWeight: 700, color: '#111827', margin: 0 }}>{asset.symbol}</h1>
                <span style={{ fontSize: 16, color: '#6b7280', fontWeight: 400 }}>{asset.name}</span>
                <LiveIndicator connected={liveConnected} error={liveError} />
              </div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, marginTop: 8 }}>
                {displayUnavailable ? (
                  <span style={{ fontSize: 14, color: '#9ca3af' }}>Price temporarily unavailable</span>
                ) : (
                  <>
                    <span style={{ fontSize: 32, fontWeight: 700, color: '#111827', fontFamily: 'monospace' }}>
                      {fmt(displayPrice)}
                    </span>
                    <span style={{
                      fontSize: 16, fontWeight: 600,
                      color: pctColor(displayChange),
                      backgroundColor: Number(displayChange) >= 0 ? '#d1fae5' : '#fee2e2',
                      padding: '2px 8px', borderRadius: 4,
                    }}>
                      {fmtPct(displayChange)}
                    </span>
                  </>
                )}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {asset.highPrice24h && (
                <StatBadge label="24h High" value={fmt(asset.highPrice24h)} />
              )}
              {asset.lowPrice24h && (
                <StatBadge label="24h Low" value={fmt(asset.lowPrice24h)} />
              )}
              {asset.volume24h && (
                <StatBadge label="24h Volume" value={Number(asset.volume24h).toLocaleString()} />
              )}
            </div>
          </div>
        </div>
      ) : null}

      {/* ─── Trade Widget + Position (side by side) ─── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 28 }}>
        <Section title="Trade">
          {asset && !displayUnavailable ? (
            <TradeWidget
              symbol={asset.symbol}
              currentPrice={displayPrice}
              position={position}
              onSuccess={handleTradeSuccess}
            />
          ) : asset && displayUnavailable ? (
            <EmptyState message="Trading unavailable — price data is not available right now." />
          ) : (
            <Skeleton height={200} />
          )}
        </Section>

        <Section title="My Position">
          {loading.position ? (
            <Skeleton height={80} />
          ) : position ? (
            <PositionCard position={position} />
          ) : (
            <EmptyState message="Unable to load position data." />
          )}
        </Section>
      </div>

      {/* ─── Price Chart ─── */}
      <Section title="Price History">
        <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
          {RANGES.map(r => (
            <button
              key={r.value}
              onClick={() => handleRangeChange(r.value)}
              style={{
                ...styles.rangeBtn,
                backgroundColor: range === r.value ? '#3b82f6' : '#f3f4f6',
                color: range === r.value ? '#fff' : '#374151',
              }}
            >
              {r.label}
            </button>
          ))}
        </div>
        {loading.chart && history.length === 0 ? (
          <Skeleton height={300} />
        ) : history.length > 0 ? (
          <PriceChart data={history} range={range} />
        ) : (
          <EmptyState message="No price history available for this range. Data will appear once Binance prices are recorded." />
        )}
      </Section>
    </div>
  );
}

/* ──────────────────── Trade Widget ──────────────────── */

function TradeWidget({ symbol, currentPrice, position, onSuccess }) {
  const [side, setSide] = useState('BUY');
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [balances, setBalances] = useState([]);
  const [cashBalance, setCashBalance] = useState(null);

  // Load balances
  const loadBalances = useCallback(async () => {
    try {
      const [walletBalances, cash] = await Promise.all([
        getWalletBalances(),
        getCashBalance(),
      ]);
      setBalances(walletBalances || []);
      setCashBalance(cash);
    } catch {
      setBalances([]);
      setCashBalance(null);
    }
  }, []);

  useEffect(() => {
    loadBalances();
  }, [loadBalances]);

  const numAmount = amount ? Number(amount) : 0;

  // Calculate estimated result
  let estimated = null;
  if (numAmount > 0 && currentPrice) {
    if (side === 'BUY') {
      estimated = numAmount / Number(currentPrice);
    } else {
      estimated = numAmount * Number(currentPrice);
    }
  }

  // Available balance for the selected side
  const availableForTrade = () => {
    if (side === 'BUY') {
      // For BUY, use USDT balance
      if (cashBalance?.cashUsd != null) {
        return Number(cashBalance.cashUsd);
      }
      const usdtBal = balances.find(b => b.asset === 'USDT');
      return usdtBal ? Number(usdtBal.available) : 0;
    } else {
      // For SELL, use position available quantity
      return position && position.availableQuantity
        ? Number(position.availableQuantity)
        : 0;
    }
  };

  const avail = availableForTrade();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    if (!amount || numAmount <= 0) {
      setError('Please enter a valid amount');
      return;
    }

    setSubmitting(true);
    try {
      let result;
      if (side === 'BUY') {
        // For BUY, amount is in USDT, convert to quantity
        const qty = numAmount / Number(currentPrice);
        result = await createTransaction({
          symbol,
          side,
          quantity: qty,
        });
      } else {
        // For SELL, amount is the quantity to sell
        result = await createTransaction({
          symbol,
          side,
          quantity: numAmount,
        });
      }

      if (side === 'BUY') {
        setSuccess(
          `Bought ${fmtQty(result.quantity)} ${result.symbol} for ${fmt(result.totalUsd)} at ${fmt(result.priceUsd)}`
        );
      } else {
        setSuccess(
          `Sold ${fmtQty(result.quantity)} ${result.symbol} for ${fmt(result.totalUsd)} at ${fmt(result.priceUsd)}`
        );
      }

      setAmount('');
      onSuccess();
      // Refresh local balances so "Available" updates immediately
      loadBalances();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Transaction failed';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={styles.card}>
      {/* BUY / SELL Tabs */}
      <div style={{ display: 'flex', gap: 0, marginBottom: 20, borderRadius: 6, overflow: 'hidden', border: '1px solid #e5e7eb' }}>
        <button
          onClick={() => setSide('BUY')}
          style={{
            flex: 1, padding: '10px 0', border: 'none', fontSize: 15, fontWeight: 700, cursor: 'pointer',
            backgroundColor: side === 'BUY' ? '#10b981' : '#f9fafb',
            color: side === 'BUY' ? '#fff' : '#6b7280',
            transition: 'all 0.15s',
          }}
        >
          Buy
        </button>
        <button
          onClick={() => setSide('SELL')}
          style={{
            flex: 1, padding: '10px 0', border: 'none', fontSize: 15, fontWeight: 700, cursor: 'pointer',
            backgroundColor: side === 'SELL' ? '#ef4444' : '#f9fafb',
            color: side === 'SELL' ? '#fff' : '#6b7280',
            transition: 'all 0.15s',
          }}
        >
          Sell
        </button>
      </div>

      {/* Market Price */}
      <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 4 }}>Market Price</div>
      <div style={{ fontSize: 20, fontWeight: 700, color: '#111827', fontFamily: 'monospace', marginBottom: 16 }}>
        {fmt(currentPrice)}
      </div>

      {/* Available Balance */}
      {avail != null && (
        <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 12 }}>
          Available:{' '}
          <span style={{ fontWeight: 600, color: '#111827' }}>
            {side === 'BUY'
              ? `${fmtQty(avail, 2)} USDT`
              : `${fmtQty(avail)} ${symbol}`}
          </span>
          {avail > 0 && (
            <button
              type="button"
              onClick={() => {
                if (side === 'BUY') {
                  setAmount(String(avail.toFixed(2)));
                } else {
                  setAmount(String(avail));
                }
              }}
              style={styles.maxBtn}
            >
              MAX
            </button>
          )}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 16 }}>
          <label style={styles.label}>
            {side === 'BUY'
              ? `Amount (USDT) to spend`
              : `Amount (${symbol}) to sell`}
          </label>
          <input
            type="number"
            step="any"
            min="0"
            value={amount}
            onChange={(e) => { setAmount(e.target.value); setError(null); setSuccess(null); }}
            placeholder="0.00"
            style={styles.input}
            disabled={submitting}
          />
        </div>

        {/* Quick amounts for BUY / percentages for SELL */}
        {side === 'BUY' && avail > 0 && (
          <div style={{ display: 'flex', gap: 4, marginBottom: 12 }}>
            {[0.25, 0.5, 0.75, 1].map((pct) => (
              <button
                key={pct}
                type="button"
                onClick={() => setAmount(String((avail * pct).toFixed(2)))}
                style={styles.quickBtn}
              >
                {pct === 1 ? 'Max' : `${pct * 100}%`}
              </button>
            ))}
          </div>
        )}
        {side === 'SELL' && avail > 0 && (
          <div style={{ display: 'flex', gap: 4, marginBottom: 12 }}>
            {[0.25, 0.5, 0.75, 1].map((pct) => (
              <button
                key={pct}
                type="button"
                onClick={() => setAmount(String(avail * pct))}
                style={styles.quickBtn}
              >
                {pct === 1 ? 'Max' : `${pct * 100}%`}
              </button>
            ))}
          </div>
        )}

        {/* Estimated result */}
        {estimated != null && estimated > 0 && (
          <div style={styles.estimateBox}>
            <span style={{ fontSize: 13, color: '#6b7280' }}>You will receive ≈</span>
            <span style={{ fontSize: 16, fontWeight: 700, color: '#111827' }}>
              {side === 'BUY'
                ? `${fmtQty(estimated)} ${symbol}`
                : `${fmtQty(estimated, 2)} USDT`}
            </span>
          </div>
        )}

        {error && <div style={styles.errorBox}>{error}</div>}
        {success && <div style={styles.successBox}>{success}</div>}

        <button
          type="submit"
          disabled={submitting || !amount || numAmount <= 0 || !currentPrice}
          style={{
            ...styles.submitBtn,
            backgroundColor: side === 'BUY' ? '#10b981' : '#ef4444',
            opacity: submitting || !amount || numAmount <= 0 || !currentPrice ? 0.5 : 1,
            cursor: submitting || !amount || numAmount <= 0 || !currentPrice ? 'not-allowed' : 'pointer',
          }}
        >
          {submitting
            ? 'Executing...'
            : side === 'BUY'
              ? `Buy ${symbol}`
              : `Sell ${symbol}`}
        </button>
      </form>
    </div>
  );
}

/* ──────────────────── sub-components ──────────────────── */

function PriceChart({ data, range }) {
  const chartData = data.map(p => ({
    time: formatChartTime(p.timestamp, range),
    price: Number(p.priceUsd),
    fullTime: new Date(p.timestamp).toLocaleString(),
  }));

  const prices = chartData.map(d => d.price);
  const minP = Math.min(...prices);
  const maxP = Math.max(...prices);
  // Increase padding to ensure top value is visible (15% padding)
  const priceRange = maxP - minP;
  const pad = priceRange > 0 ? priceRange * 0.15 : maxP * 0.02;

  return (
    <div style={styles.card}>
      <ResponsiveContainer width="100%" height={320}>
        <LineChart 
          data={chartData}
          margin={{ top: 10, right: 10, bottom: 5, left: 5 }}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
          <XAxis
            dataKey="time"
            tick={{ fontSize: 11, fill: '#9ca3af' }}
            interval="preserveStartEnd"
          />
          <YAxis
            domain={[minP - pad, maxP + pad]}
            tick={{ fontSize: 11, fill: '#9ca3af' }}
            tickFormatter={(v) => `${v.toLocaleString()} USDT`}
            width={90}
            allowDecimals={true}
          />
          <Tooltip
            formatter={(v) => [fmt(v), 'Price']}
            labelFormatter={(_, payload) => payload?.[0]?.payload?.fullTime || ''}
            contentStyle={{ fontSize: 12, borderRadius: 6, border: '1px solid #e5e7eb' }}
          />
          <Line
            type="monotone"
            dataKey="price"
            stroke="#3b82f6"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function formatChartTime(ts, range) {
  const d = new Date(ts);
  if (range === '24h') {
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

function PositionCard({ position }) {
  const hasPosition = position.quantity && Number(position.quantity) > 0;

  return (
    <div style={styles.card}>
      {hasPosition ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 16 }}>
          <PositionStat label="Quantity" value={fmtQty(position.quantity)} />
          <PositionStat label="Available" value={fmtQty(position.availableQuantity)} />
          <PositionStat label="Locked" value={fmtQty(position.lockedQuantity)} />
          <PositionStat label="Current Price" value={fmt(position.currentPriceUsd)} />
          <PositionStat label="Market Value" value={fmt(position.marketValueUsd)} />
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '16px 0', color: '#6b7280', fontSize: 14 }}>
          You don't hold any <strong>{position.symbol}</strong> yet.
        </div>
      )}
    </div>
  );
}

function PositionStat({ label, value }) {
  return (
    <div>
      <div style={{ fontSize: 12, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 18, fontWeight: 600, color: '#111827', fontFamily: 'monospace' }}>
        {value}
      </div>
    </div>
  );
}

function StatBadge({ label, value }) {
  return (
    <div style={{
      padding: '6px 12px', backgroundColor: '#f9fafb', borderRadius: 6,
      border: '1px solid #e5e7eb', textAlign: 'center', minWidth: 90,
    }}>
      <div style={{ fontSize: 11, color: '#6b7280', marginBottom: 2 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', fontFamily: 'monospace' }}>{value}</div>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div style={{ marginBottom: 28 }}>
      <h2 style={{ fontSize: 18, fontWeight: 600, color: '#374151', marginBottom: 12 }}>{title}</h2>
      {children}
    </div>
  );
}

function EmptyState({ message }) {
  return (
    <div style={{
      padding: '32px 20px', textAlign: 'center', backgroundColor: '#f9fafb',
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
  backLink: {
    display: 'inline-block', marginBottom: 16, color: '#3b82f6',
    textDecoration: 'none', fontSize: 14, fontWeight: 500,
  },
  headerCard: {
    padding: 20, backgroundColor: '#fff', borderRadius: 8,
    border: '1px solid #e5e7eb', boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
    marginBottom: 28,
  },
  card: {
    padding: 16, backgroundColor: '#fff', borderRadius: 8,
    border: '1px solid #e5e7eb', boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
  },
  rangeBtn: {
    padding: '6px 14px', border: 'none', borderRadius: 6,
    cursor: 'pointer', fontSize: 13, fontWeight: 500,
    transition: 'all 0.15s',
  },
  retryBtn: {
    padding: '6px 14px', backgroundColor: '#dc2626', color: '#fff',
    border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13, fontWeight: 500,
  },
  tradeInput: {
    width: '100%', padding: '8px 10px', border: '1px solid #d1d5db',
    borderRadius: 6, fontSize: 16, fontFamily: 'monospace',
    outline: 'none', boxSizing: 'border-box',
  },
  label: {
    display: 'block',
    fontSize: 13,
    fontWeight: 500,
    color: '#374151',
    marginBottom: 6,
  },
  input: {
    width: '100%',
    padding: '10px 12px',
    fontSize: 16,
    border: '1px solid #d1d5db',
    borderRadius: 6,
    outline: 'none',
    boxSizing: 'border-box',
    fontFamily: 'monospace',
  },
  maxBtn: {
    marginLeft: 8,
    padding: '2px 8px',
    fontSize: 11,
    fontWeight: 600,
    border: '1px solid #d1d5db',
    borderRadius: 3,
    backgroundColor: '#f9fafb',
    cursor: 'pointer',
    color: '#3b82f6',
  },
  quickBtn: {
    flex: 1, padding: '5px 0', fontSize: 12, fontWeight: 500,
    border: '1px solid #d1d5db', borderRadius: 4, cursor: 'pointer',
    backgroundColor: '#f9fafb', color: '#374151',
  },
  estimateBox: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '10px 14px',
    backgroundColor: '#f9fafb',
    borderRadius: 6,
    marginBottom: 16,
    border: '1px solid #e5e7eb',
  },
  submitBtn: {
    width: '100%',
    padding: '12px 20px',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    fontSize: 16,
    fontWeight: 600,
    transition: 'opacity 0.15s',
  },
  errorBox: {
    padding: '10px 14px',
    backgroundColor: '#fef2f2',
    border: '1px solid #fecaca',
    borderRadius: 6,
    color: '#dc2626',
    fontSize: 13,
    marginBottom: 12,
  },
  successBox: {
    padding: '10px 14px',
    backgroundColor: '#f0fdf4',
    border: '1px solid #bbf7d0',
    borderRadius: 6,
    color: '#16a34a',
    fontSize: 13,
    marginBottom: 12,
  },
};

export default AssetDetailPage;
