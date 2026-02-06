import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useOutletContext } from 'react-router-dom';
import { getPairs, getPrice, getPairHistory } from '../shared/api/services/marketService';
import { placeMarketOrder, listTrades } from '../shared/api/services/orderService';
import { getWalletBalances } from '../shared/api/services/walletService';
import { usePriceStream } from '../shared/hooks/usePriceStream';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';

/* ──────────────────── helpers ──────────────────── */

const fmtUsd = (v, decimals = 2) => {
  if (v == null || isNaN(Number(v))) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD',
    minimumFractionDigits: decimals, maximumFractionDigits: decimals,
  }).format(Number(v));
};

const fmtQty = (v, maxDec = 8) => {
  if (v == null || isNaN(Number(v))) return '0';
  const n = Number(v);
  if (n === 0) return '0';
  return n.toFixed(maxDec).replace(/\.?0+$/, '');
};

/** Format a price with the quote asset label */
const fmtPairPrice = (v, quote) => {
  if (v == null || isNaN(Number(v))) return '—';
  const n = Number(v);
  if (quote === 'USDT' || quote === 'USDC') {
    // Dollar-like stablecoins → show $ format
    if (n >= 1) return fmtUsd(v);
    return '$' + n.toFixed(8);
  }
  // Non-USD quote (BTC, ETH, BNB, etc.) → show raw number + symbol
  if (n >= 1) return `${n.toFixed(4)} ${quote}`;
  return `${n.toFixed(8).replace(/0+$/, '')} ${quote}`;
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

const RANGES = [
  { label: '24h', value: '24h' },
  { label: '7d', value: '7d' },
  { label: '30d', value: '30d' },
];

function formatChartTime(ts, range) {
  const d = new Date(ts);
  if (range === '24h') {
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

/** Group pairs by quote asset for the dropdown */
function groupPairsByQuote(pairs) {
  const groups = {};
  for (const p of pairs) {
    const q = p.quote;
    if (!groups[q]) groups[q] = [];
    groups[q].push(p);
  }
  // Sort groups: USDT first, then BTC, ETH, others
  const order = ['USDT', 'BTC', 'ETH', 'BNB'];
  const sorted = {};
  for (const q of order) {
    if (groups[q]) sorted[q] = groups[q];
  }
  for (const q of Object.keys(groups).sort()) {
    if (!sorted[q]) sorted[q] = groups[q];
  }
  return sorted;
}

/* ──────────────────── main page ──────────────────── */

function TradingPage() {
  const { refreshCashBalance } = useOutletContext() || {};

  // State
  const [pairs, setPairs] = useState([]);
  const [selectedPairId, setSelectedPairId] = useState(null);
  const [side, setSide] = useState('BUY');
  const [amount, setAmount] = useState('');
  const [balances, setBalances] = useState([]);
  const [trades, setTrades] = useState([]);
  const [history, setHistory] = useState([]);
  const [range, setRange] = useState('24h');

  const [loadingPairs, setLoadingPairs] = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const selectedPair = pairs.find(p => p.id === selectedPairId);
  const quoteSymbol = selectedPair?.quote?.toUpperCase() || 'USDT';
  const baseSymbol = selectedPair?.base?.toUpperCase() || null;
  const isUsdtQuote = quoteSymbol === 'USDT' || quoteSymbol === 'USDC';

  // Live price stream — only useful for USDT-quoted pairs
  const streamSymbols = useMemo(
    () => (baseSymbol && isUsdtQuote) ? [baseSymbol] : [],
    [baseSymbol, isUsdtQuote],
  );
  const { prices: livePrices, connected: liveConnected, error: liveError } = usePriceStream(streamSymbols);
  const livePrice = (baseSymbol && isUsdtQuote) ? livePrices[baseSymbol] : null;
  const displayPrice = livePrice?.priceUsd ?? null;
  const displayChange = livePrice?.change24hPercent ?? null;

  // Fallback: poll REST price for ALL pairs
  const [restPrice, setRestPrice] = useState(null);
  const priceInterval = useRef(null);

  const fetchRestPrice = useCallback(async () => {
    if (!selectedPairId) return;
    try {
      const data = await getPrice(selectedPairId);
      setRestPrice(data?.price ? Number(data.price) : null);
    } catch {
      // silent
    }
  }, [selectedPairId]);

  useEffect(() => {
    if (!selectedPairId) return;
    setRestPrice(null);
    fetchRestPrice();
    priceInterval.current = setInterval(fetchRestPrice, 5000);
    return () => clearInterval(priceInterval.current);
  }, [selectedPairId, fetchRestPrice]);

  // Best available price: prefer live SSE (USDT pairs only), fall back to REST
  const currentPrice = displayPrice ?? restPrice;

  // Whether we consider this "live" — SSE connected for USDT pairs, REST polling for others
  const isLive = isUsdtQuote ? liveConnected : restPrice != null;
  const liveStatus = isUsdtQuote
    ? { connected: liveConnected, error: liveError }
    : { connected: restPrice != null, error: null };

  // 24h % change — from SSE for USDT pairs, computed from history for cross-pairs
  const historyChange = useMemo(() => {
    if (range !== '24h' || history.length < 2 || !currentPrice) return null;
    const first = Number(history[0]?.price ?? history[0]?.priceUsd);
    if (!first || first === 0) return null;
    return ((currentPrice - first) / first) * 100;
  }, [history, currentPrice, range]);

  const change24h = displayChange ?? historyChange;

  // Load pairs on mount
  useEffect(() => {
    (async () => {
      try {
        setLoadingPairs(true);
        const data = await getPairs();
        const enabledPairs = (data || []).filter(p => p.enabled);
        setPairs(enabledPairs);
        if (enabledPairs.length > 0) {
          setSelectedPairId(enabledPairs[0].id);
        }
      } catch {
        setError('Failed to load trading pairs');
      } finally {
        setLoadingPairs(false);
      }
    })();
  }, []);

  // Grouped pairs for the dropdown
  const groupedPairs = useMemo(() => groupPairsByQuote(pairs), [pairs]);

  // Load balances on mount and after trades
  const loadBalances = useCallback(async () => {
    try {
      const data = await getWalletBalances();
      setBalances(data || []);
    } catch {
      // silent
    }
  }, []);
  useEffect(() => { loadBalances(); }, [loadBalances]);

  // Load trades on mount
  const loadTrades = useCallback(async () => {
    try {
      const data = await listTrades(0, 10);
      setTrades(data?.items || []);
    } catch {
      // silent
    }
  }, []);
  useEffect(() => { loadTrades(); }, [loadTrades]);

  // Load price history when pair or range changes — uses pair-specific endpoint
  const loadHistory = useCallback(async (r) => {
    if (!selectedPairId) return;
    setLoadingChart(true);
    try {
      const data = await getPairHistory(selectedPairId, r);
      setHistory(Array.isArray(data) ? data : []);
    } catch {
      setHistory([]);
    } finally {
      setLoadingChart(false);
    }
  }, [selectedPairId]);

  useEffect(() => {
    loadHistory(range);
  }, [range, loadHistory]);

  // Clear amount on pair/side change
  useEffect(() => {
    setAmount('');
    setError(null);
    setSuccess(null);
  }, [selectedPairId, side]);

  // Estimated result
  const numAmount = parseFloat(amount) || 0;
  let estimated = null;
  if (currentPrice && numAmount > 0) {
    if (side === 'BUY') {
      estimated = numAmount / currentPrice;
    } else {
      estimated = numAmount * currentPrice;
    }
  }

  // Available balance for the selected side
  const availableForTrade = () => {
    if (!selectedPair) return null;
    if (side === 'BUY') {
      const quoteBal = balances.find(b => b.asset === selectedPair.quote);
      return quoteBal ? Number(quoteBal.available) : 0;
    } else {
      const baseBal = balances.find(b => b.asset === selectedPair.base);
      return baseBal ? Number(baseBal.available) : 0;
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    if (!selectedPairId || numAmount <= 0) {
      setError('Please enter a valid amount');
      return;
    }

    try {
      setSubmitting(true);
      const params = { pairId: selectedPairId, side };
      if (side === 'BUY') {
        params.quoteAmount = numAmount;
      } else {
        params.baseAmount = numAmount;
      }

      const trade = await placeMarketOrder(params);

      if (side === 'BUY') {
        setSuccess(
          `Bought ${fmtQty(trade.baseQty)} ${trade.baseAsset} for ${fmtPairPrice(trade.quoteQty, quoteSymbol)} at ${fmtPairPrice(trade.price, quoteSymbol)}`
        );
      } else {
        setSuccess(
          `Sold ${fmtQty(trade.baseQty)} ${trade.baseAsset} for ${fmtPairPrice(trade.quoteQty, quoteSymbol)} at ${fmtPairPrice(trade.price, quoteSymbol)}`
        );
      }

      setAmount('');
      loadBalances();
      loadTrades();
      if (refreshCashBalance) refreshCashBalance();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Order failed';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const avail = availableForTrade();

  const handlePairChange = (e) => {
    setSelectedPairId(e.target.value);
    setHistory([]);
    setRange('24h');
  };

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      <h1 style={{ fontSize: 28, fontWeight: 700, color: '#111827', marginBottom: 24 }}>Trade</h1>

      {/* ─── Pair Selector Header ─── */}
      <div style={styles.headerCard}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
              {loadingPairs ? (
                <Skeleton height={42} />
              ) : (
                <select
                  value={selectedPairId || ''}
                  onChange={handlePairChange}
                  style={styles.pairSelect}
                >
                  {Object.entries(groupedPairs).map(([quote, group]) => (
                    <optgroup key={quote} label={`── ${quote} pairs ──`}>
                      {group.map(p => (
                        <option key={p.id} value={p.id}>
                          {p.base}/{p.quote}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </select>
              )}
              <LiveIndicator connected={liveStatus.connected} error={liveStatus.error} />
            </div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, marginTop: 4 }}>
              <span style={{ fontSize: 32, fontWeight: 700, color: '#111827', fontFamily: 'monospace' }}>
                {currentPrice ? fmtPairPrice(currentPrice, quoteSymbol) : '—'}
              </span>
              {change24h != null && (
                <span style={{
                  fontSize: 16, fontWeight: 600,
                  color: pctColor(change24h),
                  backgroundColor: Number(change24h) >= 0 ? '#d1fae5' : '#fee2e2',
                  padding: '2px 8px', borderRadius: 4,
                }}>
                  {fmtPct(change24h)}
                </span>
              )}
              {!currentPrice && !loadingPairs && (
                <span style={{ fontSize: 14, color: '#9ca3af' }}>Price loading…</span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ─── Price Chart ─── */}
      <Section title="Price Chart">
        <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
          {RANGES.map(r => (
            <button
              key={r.value}
              onClick={() => setRange(r.value)}
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
        {loadingChart && history.length === 0 ? (
          <Skeleton height={300} />
        ) : history.length > 0 ? (
          <PriceChart data={history} range={range} quoteSymbol={quoteSymbol} />
        ) : (
          <EmptyState message="No price history available for this range. Data will appear once Binance prices are recorded." />
        )}
      </Section>

      {/* ─── Trade Widget + Balances + Recent Trades (2 columns) ─── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        {/* ── Left: Order Panel ── */}
        <Section title="Place Order">
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
              {currentPrice ? fmtPairPrice(currentPrice, quoteSymbol) : '—'}
            </div>

            {/* Available Balance */}
            {selectedPair && avail != null && (
              <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 12 }}>
                Available:{' '}
                <span style={{ fontWeight: 600, color: '#111827' }}>
                  {side === 'BUY'
                    ? `${fmtQty(avail, 2)} ${selectedPair.quote}`
                    : `${fmtQty(avail)} ${selectedPair.base}`}
                </span>
                {avail > 0 && (
                  <button
                    type="button"
                    onClick={() => setAmount(String(avail))}
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
                    ? `Amount (${selectedPair?.quote || 'Quote'}) to spend`
                    : `Amount (${selectedPair?.base || 'Base'}) to sell`}
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
                      ? `${fmtQty(estimated)} ${selectedPair?.base}`
                      : `${fmtQty(estimated, isUsdtQuote ? 2 : 8)} ${quoteSymbol}`}
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
                    ? `Buy ${selectedPair?.base || ''}`
                    : `Sell ${selectedPair?.base || ''}`}
              </button>
            </form>
          </div>
        </Section>

        {/* ── Right: Balances + Recent Trades ── */}
        <div>
          {/* Balances Card */}
          <Section title="Your Balances">
            <div style={styles.card}>
              {balances.length === 0 ? (
                <div style={{ color: '#9ca3af', fontSize: 13, padding: '12px 0' }}>
                  No balances yet. Deposit USDT to start trading.
                </div>
              ) : (
                <div style={{ maxHeight: 200, overflowY: 'auto' }}>
                  {balances
                    .filter(b => Number(b.available) > 0)
                    .sort((a, b) => {
                      if (a.asset === 'USDT') return -1;
                      if (b.asset === 'USDT') return 1;
                      return a.asset.localeCompare(b.asset);
                    })
                    .map(b => (
                      <div key={b.asset} style={styles.balanceRow}>
                        <span style={{ fontWeight: 600, fontSize: 13 }}>{b.asset}</span>
                        <span style={{ fontFamily: 'monospace', fontSize: 13 }}>
                          {fmtQty(b.available, b.asset === 'USDT' ? 2 : 8)}
                        </span>
                      </div>
                    ))}
                </div>
              )}
            </div>
          </Section>

          {/* Recent Trades Card */}
          <Section title="Recent Trades">
            <div style={styles.card}>
              {trades.length === 0 ? (
                <div style={{ color: '#9ca3af', fontSize: 13, padding: '12px 0' }}>
                  No trades yet. Place your first order!
                </div>
              ) : (
                <div style={{ maxHeight: 400, overflowY: 'auto' }}>
                  {trades.map(t => (
                    <div key={t.id} style={styles.tradeRow}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div>
                          <span style={{
                            display: 'inline-block', width: 40,
                            fontSize: 11, fontWeight: 700, textAlign: 'center',
                            padding: '2px 0', borderRadius: 3,
                            backgroundColor: t.side === 'BUY' ? '#d1fae5' : '#fee2e2',
                            color: t.side === 'BUY' ? '#065f46' : '#991b1b',
                          }}>
                            {t.side}
                          </span>
                          <span style={{ marginLeft: 8, fontWeight: 600, fontSize: 13 }}>
                            {t.pairSymbol}
                          </span>
                        </div>
                        <span style={{ fontSize: 11, color: '#9ca3af' }}>
                          {new Date(t.createdAt).toLocaleString()}
                        </span>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4, fontSize: 12, color: '#6b7280' }}>
                        <span>Qty: {fmtQty(t.baseQty)} {t.baseAsset}</span>
                        <span>@ {t.quoteAsset ? fmtPairPrice(t.price, t.quoteAsset) : fmtPairPrice(t.price, 'USDT')}</span>
                        <span>Total: {t.quoteAsset ? fmtPairPrice(t.quoteQty, t.quoteAsset) : fmtPairPrice(t.quoteQty, 'USDT')}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </Section>
        </div>
      </div>
    </div>
  );
}

/* ──────────────────── sub-components ──────────────────── */

function PriceChart({ data, range, quoteSymbol }) {
  const chartData = data.map(p => ({
    time: formatChartTime(p.timestamp, range),
    price: Number(p.price ?? p.priceUsd),
    fullTime: new Date(p.timestamp).toLocaleString(),
  }));

  const isUsd = quoteSymbol === 'USDT' || quoteSymbol === 'USDC';

  const prices = chartData.map(d => d.price);
  const minP = Math.min(...prices);
  const maxP = Math.max(...prices);
  const pad = (maxP - minP) * 0.1 || maxP * 0.01;

  const formatTickLabel = (v) => {
    if (isUsd) return `$${v.toLocaleString()}`;
    return `${v}`;
  };

  const formatTooltipValue = (v) => {
    if (isUsd) return [fmtUsd(v), 'Price'];
    return [`${Number(v).toFixed(8).replace(/0+$/, '')} ${quoteSymbol}`, 'Price'];
  };

  return (
    <div style={styles.card}>
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
          <XAxis
            dataKey="time"
            tick={{ fontSize: 11, fill: '#9ca3af' }}
            interval="preserveStartEnd"
          />
          <YAxis
            domain={[minP - pad, maxP + pad]}
            tick={{ fontSize: 11, fill: '#9ca3af' }}
            tickFormatter={formatTickLabel}
            width={90}
          />
          <Tooltip
            formatter={formatTooltipValue}
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

function Section({ title, children }) {
  return (
    <div style={{ marginBottom: 24 }}>
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
  headerCard: {
    padding: 20, backgroundColor: '#fff', borderRadius: 8,
    border: '1px solid #e5e7eb', boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
    marginBottom: 24,
  },
  pairSelect: {
    padding: '10px 14px',
    fontSize: 20,
    fontWeight: 700,
    border: '1px solid #d1d5db',
    borderRadius: 8,
    backgroundColor: '#fff',
    outline: 'none',
    cursor: 'pointer',
    color: '#111827',
    minWidth: 200,
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
  balanceRow: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '8px 0',
    borderBottom: '1px solid #f3f4f6',
  },
  tradeRow: {
    padding: '10px 0',
    borderBottom: '1px solid #f3f4f6',
  },
};

export default TradingPage;
