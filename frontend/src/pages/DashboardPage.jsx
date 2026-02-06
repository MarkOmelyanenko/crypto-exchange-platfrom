import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { getSummary, getHoldings, getRecentTransactions } from '../shared/api/services/dashboardService';
import { getHistory, getSnapshot } from '../shared/api/services/priceService';
import { getHealth } from '../shared/api/services/systemService';
import { getWalletBalances, getCashBalance } from '../shared/api/services/walletService';
import { usePriceStream } from '../shared/hooks/usePriceStream';
import { PortfolioPieChart } from '../shared/components/PortfolioPieChart';
import CryptoIcon from '../shared/components/CryptoIcon';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
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

const fmtDate = (d) => {
  if (!d) return '—';
  return new Date(d).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
};

const fmtQty = (v, maxDecimals = 8) => {
  if (v == null || isNaN(Number(v))) return '—';
  const n = Number(v);
  if (n === 0) return '0';
  // Remove trailing zeros
  return n.toFixed(maxDecimals).replace(/\.?0+$/, '');
};

const pnlColor = (v) => Number(v) >= 0 ? '#10b981' : '#ef4444';

/* ──────────────────── main page ──────────────────── */

function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [holdings, setHoldings] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [priceHistory, setPriceHistory] = useState([]);
  const [systemHealth, setSystemHealth] = useState(null);
  const [balances, setBalances] = useState([]);
  const [cashBalance, setCashBalance] = useState(null);
  const [portfolioPrices, setPortfolioPrices] = useState({});
  const [portfolioLastUpdated, setPortfolioLastUpdated] = useState(null);
  const [loading, setLoading] = useState({
    summary: true, holdings: true, transactions: true, chart: true, health: true, portfolio: true,
  });
  const [errors, setErrors] = useState({});
  const holdingsRef = useRef([]);
  const priceCacheRef = useRef({ timestamp: 0, data: {} });

  // Live price stream for holdings symbols
  const holdingSymbols = useMemo(
    () => holdings.filter(h => h.symbol !== 'USDT').map(h => h.symbol),
    [holdings]
  );
  const { prices: livePrices, connected: liveConnected, error: liveError } = usePriceStream(holdingSymbols);

  // Load prices for portfolio chart (with caching)
  const loadPortfolioPrices = useCallback(async (balancesData) => {
    try {
      // Check cache (5 seconds)
      const now = Date.now();
      if (priceCacheRef.current.timestamp > 0 && (now - priceCacheRef.current.timestamp) < 5000) {
        setPortfolioPrices(priceCacheRef.current.data);
        setLoading(prev => ({ ...prev, portfolio: false }));
        return;
      }

      // Get unique asset symbols (excluding USDT)
      const symbols = [...new Set(
        balancesData
          .filter(b => b.asset !== 'USDT' && Number(b.available) > 0)
          .map(b => b.asset)
      )];

      if (symbols.length === 0) {
        setPortfolioPrices({});
        setLoading(prev => ({ ...prev, portfolio: false }));
        return;
      }

      const priceSnapshots = await getSnapshot(symbols);
      const priceMap = {};
      
      for (const snapshot of priceSnapshots || []) {
        if (snapshot && snapshot.symbol) {
          // Handle both null and valid price values
          const price = snapshot.priceUsd;
          if (price != null && !isNaN(Number(price)) && Number(price) > 0) {
            priceMap[snapshot.symbol] = Number(price);
          }
          // If price is null/undefined, it will be skipped (handled in portfolioUtils)
        }
      }

      // Update cache
      priceCacheRef.current = {
        timestamp: now,
        data: priceMap,
      };

      setPortfolioPrices(priceMap);
    } catch (err) {
      console.error('Failed to load portfolio prices:', err);
      setErrors(prev => ({ ...prev, portfolio: 'Failed to load prices' }));
    } finally {
      setLoading(prev => ({ ...prev, portfolio: false }));
    }
  }, []);

  const loadPriceData = useCallback(async (holdingsData) => {
    setLoading(prev => ({ ...prev, chart: true }));
    try {
      const symbols = holdingsData && holdingsData.length > 0
        ? [...holdingsData]
            .sort((a, b) => Number(b.marketValueUsd) - Number(a.marketValueUsd))
            .slice(0, 5)
            .map(h => h.symbol)
        : ['BTC', 'ETH', 'SOL'];

      const chartSymbols = symbols.slice(0, 3);
      const histories = await Promise.all(
        chartSymbols.map(sym =>
          getHistory(sym, '24h')
            .then(data => ({ symbol: sym, data: data || [] }))
            .catch(() => ({ symbol: sym, data: [] }))
        )
      );

      setPriceHistory(histories.filter(h => h.data.length > 0));
    } catch {
      // Silently fail; chart will show "no data"
    } finally {
      setLoading(prev => ({ ...prev, chart: false }));
    }
  }, []);

  const loadDashboardData = useCallback(async () => {
    setLoading({ summary: true, holdings: true, transactions: true, chart: true, health: true, portfolio: true });
    setErrors({});

    const results = await Promise.allSettled([
      getSummary(),
      getHoldings(),
      getRecentTransactions(10),
      getHealth(),
      getWalletBalances(),
      getCashBalance(),
    ]);

    const [summaryR, holdingsR, txR, healthR, balancesR, cashR] = results;

    if (summaryR.status === 'fulfilled') {
      setSummary(summaryR.value);
    } else {
      setErrors(prev => ({ ...prev, summary: summaryR.reason }));
    }
    setLoading(prev => ({ ...prev, summary: false }));

    if (holdingsR.status === 'fulfilled') {
      setHoldings(holdingsR.value);
      holdingsRef.current = holdingsR.value;
    } else {
      setErrors(prev => ({ ...prev, holdings: holdingsR.reason }));
    }
    setLoading(prev => ({ ...prev, holdings: false }));

    if (txR.status === 'fulfilled') {
      setTransactions(txR.value);
    } else {
      setErrors(prev => ({ ...prev, transactions: txR.reason }));
    }
    setLoading(prev => ({ ...prev, transactions: false }));

    if (healthR.status === 'fulfilled') {
      setSystemHealth(healthR.value);
    } else {
      setErrors(prev => ({ ...prev, health: healthR.reason }));
    }
    setLoading(prev => ({ ...prev, health: false }));

    if (balancesR.status === 'fulfilled') {
      setBalances(balancesR.value || []);
    } else {
      setErrors(prev => ({ ...prev, portfolio: balancesR.reason }));
    }

    if (cashR.status === 'fulfilled') {
      setCashBalance(cashR.value?.cashUsd || null);
    } else {
      setErrors(prev => ({ ...prev, portfolio: cashR.reason }));
    }

    // Load portfolio prices
    await loadPortfolioPrices(balancesR.status === 'fulfilled' ? balancesR.value || [] : []);
    
    // Update last updated timestamp when portfolio data is refreshed
    if (balancesR.status === 'fulfilled' || cashR.status === 'fulfilled') {
      setPortfolioLastUpdated(new Date());
    }

    // Load price charts
    const h = holdingsR.status === 'fulfilled' ? holdingsR.value : [];
    await loadPriceData(h);
  }, [loadPortfolioPrices, loadPriceData]);

  // Polling: refresh prices every 10s
  useEffect(() => {
    loadDashboardData();
    const interval = setInterval(() => {
      loadPriceData(holdingsRef.current);
    }, 10000);
    return () => clearInterval(interval);
  }, [loadDashboardData, loadPriceData]);

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, color: '#111827', margin: 0 }}>Dashboard</h1>
        <LiveIndicator connected={liveConnected} error={liveError} />
      </div>

      {/* ─── Portfolio Allocation Chart ─── */}
      <Section title="Portfolio Allocation">
        {(errors.summary || errors.portfolio) ? (
          <ErrorBox message="Failed to load portfolio data" onRetry={loadDashboardData} />
        ) : (
          <div style={styles.portfolioGrid}>
            {/* Left side: Summary Cards */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <SummaryCard label="Total Portfolio Value" value={fmt(summary?.totalValueUsd)} loading={loading.summary} />
              <SummaryCard label="USDT Balance" value={fmt(summary?.availableCashUsd)} loading={loading.summary} />
              <SummaryCard
                label="Unrealized PnL"
                value={summary ? `${fmt(summary.unrealizedPnlUsd)}  ${fmtPct(summary.unrealizedPnlPercent)}` : null}
                valueColor={summary ? pnlColor(summary.unrealizedPnlUsd) : undefined}
                loading={loading.summary}
              />
              <SummaryCard
                label="Realized PnL"
                value={summary ? fmt(summary.realizedPnlUsd) : null}
                valueColor={summary ? pnlColor(summary.realizedPnlUsd) : undefined}
                loading={loading.summary}
              />
            </div>

            {/* Right side: Chart with Legend */}
            <div style={styles.card}>
              <PortfolioPieChart
                balances={balances}
                cashUsd={cashBalance}
                prices={portfolioPrices}
                assetNames={holdings.reduce((acc, h) => {
                  acc[h.symbol] = h.name;
                  return acc;
                }, { USDT: 'Tether' })}
                lastUpdatedAt={portfolioLastUpdated}
                loading={loading.portfolio}
                error={errors.portfolio}
                showLegendAsList={false}
              />
            </div>
          </div>
        )}
      </Section>

      {/* ─── Holdings Table ─── */}
      <Section title="Holdings">
        {errors.holdings ? (
          <ErrorBox message="Failed to load holdings" onRetry={loadDashboardData} />
        ) : (
          <HoldingsTable holdings={holdings} loading={loading.holdings} livePrices={livePrices} />
        )}
      </Section>

      {/* ─── Price Charts + Recent Transactions (side by side) ─── */}
      <div style={styles.splitGrid}>
        <Section title="Price Trends (24h)">
          {loading.chart && priceHistory.length === 0 ? (
            <Skeleton height={300} />
          ) : priceHistory.length > 0 ? (
            <PriceCharts data={priceHistory} />
          ) : (
            <EmptyState message="No price data available yet. Data will appear once Binance prices are fetched." />
          )}
        </Section>

        <Section title="Recent Transactions">
          {errors.transactions ? (
            <ErrorBox message="Failed to load transactions" onRetry={loadDashboardData} />
          ) : (
            <TransactionsList transactions={transactions} loading={loading.transactions} />
          )}
        </Section>
      </div>

      {/* ─── System Status ─── */}
      <Section title="System Status">
        {errors.health ? (
          <ErrorBox message="Failed to load system status" onRetry={loadDashboardData} />
        ) : loading.health ? (
          <Skeleton height={60} />
        ) : systemHealth ? (
          <SystemStatusWidget health={systemHealth} />
        ) : (
          <EmptyState message="Status unavailable" />
        )}
      </Section>
    </div>
  );
}

/* ──────────────────── components ──────────────────── */

function Section({ title, children }) {
  return (
    <div style={{ marginBottom: 28 }}>
      <h2 style={{ fontSize: 18, fontWeight: 600, color: '#374151', marginBottom: 12 }}>{title}</h2>
      {children}
    </div>
  );
}

function SummaryCard({ label, value, valueColor, loading }) {
  return (
    <div style={styles.card}>
      <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 6, letterSpacing: '0.02em' }}>{label}</div>
      {loading ? (
        <div style={styles.skeletonBar} />
      ) : (
        <div style={{ fontSize: 22, fontWeight: 700, color: valueColor || '#111827', lineHeight: 1.3 }}>
          {value ?? '—'}
        </div>
      )}
    </div>
  );
}

function HoldingsTable({ holdings, loading, livePrices = {} }) {
  const [sortBy, setSortBy] = useState('marketValue');
  const [sortAsc, setSortAsc] = useState(false);

  const handleSort = (field) => {
    if (sortBy === field) setSortAsc(!sortAsc);
    else { setSortBy(field); setSortAsc(false); }
  };

  // Filter out holdings with zero or near-zero quantity
  const filteredHoldings = holdings.filter((h) => {
    const qty = Number(h.quantity) || 0;
    return qty > 0.00000001; // Filter out zero or very small quantities
  });

  // Enrich holdings with live prices — recalculate PnL with real-time data
  const enriched = filteredHoldings.map((h) => {
    const live = livePrices[h.symbol];
    if (live && live.priceUsd != null) {
      const currentPrice = Number(live.priceUsd);
      const qty = Number(h.quantity) || 0;
      const avgBuy = Number(h.avgBuyPriceUsd) || 0;
      const marketValue = qty * currentPrice;
      const costBasis = qty * avgBuy;
      const pnl = marketValue - costBasis;
      const pnlPct = costBasis > 0 ? (pnl / costBasis) * 100 : 0;
      return {
        ...h,
        currentPriceUsd: currentPrice,
        marketValueUsd: marketValue,
        unrealizedPnlUsd: pnl,
        unrealizedPnlPercent: pnlPct,
      };
    }
    return h;
  });

  const sorted = [...enriched].sort((a, b) => {
    const av = sortBy === 'marketValue' ? Number(a.marketValueUsd) : Number(a.unrealizedPnlUsd);
    const bv = sortBy === 'marketValue' ? Number(b.marketValueUsd) : Number(b.unrealizedPnlUsd);
    return sortAsc ? av - bv : bv - av;
  });

  if (loading && filteredHoldings.length === 0) return <Skeleton height={120} />;

  if (filteredHoldings.length === 0) {
    return (
      <EmptyState message="No holdings yet. Start trading to see your portfolio here.">
        <Link to="/assets" style={styles.primaryBtn}>Browse Assets</Link>
      </EmptyState>
    );
  }

  const sortIcon = (field) => sortBy === field ? (sortAsc ? ' ↑' : ' ↓') : '';

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={styles.table}>
        <thead>
          <tr>
            <Th align="left">Asset</Th>
            <Th align="right">Quantity</Th>
            <Th align="right">Avg Buy Price</Th>
            <Th align="right">Current Price</Th>
            <Th align="right" sortable onClick={() => handleSort('marketValue')}>
              Market Value{sortIcon('marketValue')}
            </Th>
            <Th align="right" sortable onClick={() => handleSort('pnl')}>
              Unrealized PnL{sortIcon('pnl')}
            </Th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((h) => (
            <tr key={h.assetId} style={styles.tableRow}>
              <td style={{ ...styles.td, textAlign: 'left' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <CryptoIcon symbol={h.symbol} size={24} />
                  <div>
                    <span style={{ fontWeight: 600 }}>{h.symbol}</span>
                    <span style={{ fontSize: 12, color: '#6b7280', marginLeft: 6 }}>{h.name}</span>
                  </div>
                </div>
              </td>
              <td style={{ ...styles.td, textAlign: 'right', fontFamily: 'monospace' }}>{fmtQty(h.quantity)}</td>
              <td style={{ ...styles.td, textAlign: 'right' }}>{fmt(h.avgBuyPriceUsd)}</td>
              <td style={{ ...styles.td, textAlign: 'right' }}>{fmt(h.currentPriceUsd)}</td>
              <td style={{ ...styles.td, textAlign: 'right', fontWeight: 500 }}>{fmt(h.marketValueUsd)}</td>
              <td style={{ ...styles.td, textAlign: 'right', color: pnlColor(h.unrealizedPnlUsd), fontWeight: 500 }}>
                {fmt(h.unrealizedPnlUsd)}{' '}
                <span style={{ fontSize: 12 }}>({fmtPct(h.unrealizedPnlPercent)})</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
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

function TransactionsList({ transactions, loading }) {
  if (loading && transactions.length === 0) return <Skeleton height={200} />;

  if (transactions.length === 0) {
    return <EmptyState message="No recent transactions" />;
  }

  return (
    <div style={styles.card}>
      {transactions.map((tx, i) => (
        <div key={tx.id || i} style={{
          padding: '10px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          borderBottom: i < transactions.length - 1 ? '1px solid #f3f4f6' : 'none',
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{
                display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700,
                backgroundColor: tx.type === 'BUY' ? '#d1fae5' : '#fee2e2',
                color: tx.type === 'BUY' ? '#065f46' : '#991b1b',
              }}>
                {tx.type}
              </span>
              <CryptoIcon symbol={tx.symbol} size={20} />
              <span style={{ fontWeight: 600, fontSize: 14 }}>{tx.symbol}</span>
              <StatusBadge status={tx.status} />
            </div>
            <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 2 }}>{fmtDate(tx.timestamp)}</div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontFamily: 'monospace', fontSize: 13 }}>{fmtQty(tx.quantity)}</div>
            <div style={{ fontSize: 11, color: '#6b7280' }}>@ {fmt(tx.priceUsd)}</div>
          </div>
        </div>
      ))}
      <div style={{ padding: '10px 14px', textAlign: 'center', borderTop: '1px solid #e5e7eb' }}>
        <Link to="/transactions" style={{ color: '#3b82f6', textDecoration: 'none', fontSize: 13, fontWeight: 500 }}>
          View all transactions →
        </Link>
      </div>
    </div>
  );
}

function StatusBadge({ status }) {
  const s = (status || '').toUpperCase();
  const map = {
    COMPLETED: { bg: '#d1fae5', color: '#065f46' },
    FILLED: { bg: '#d1fae5', color: '#065f46' },
    PENDING: { bg: '#fef3c7', color: '#92400e' },
    NEW: { bg: '#fef3c7', color: '#92400e' },
    PARTIALLY_FILLED: { bg: '#dbeafe', color: '#1e40af' },
    FAILED: { bg: '#fee2e2', color: '#991b1b' },
    CANCELLED: { bg: '#f3f4f6', color: '#6b7280' },
  };
  const colors = map[s] || { bg: '#f3f4f6', color: '#6b7280' };
  return (
    <span style={{
      fontSize: 10, fontWeight: 600, padding: '1px 6px', borderRadius: 3,
      backgroundColor: colors.bg, color: colors.color,
    }}>
      {s.replace('_', ' ')}
    </span>
  );
}

/**
 * Renders one mini line-chart per symbol so different price scales don't conflict.
 */
function PriceCharts({ data }) {
  const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444'];

  return (
    <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.min(data.length, 3)}, 1fr)`, gap: 16 }}>
      {data.map(({ symbol, data: history }, idx) => {
        const chartData = history.map(p => ({
          time: new Date(p.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          price: Number(p.priceUsd),
        }));

        const prices = chartData.map(d => d.price);
        const minP = Math.min(...prices);
        const maxP = Math.max(...prices);
        // Increase padding to ensure top value is visible (15% padding)
        const range = maxP - minP;
        const pad = range > 0 ? range * 0.15 : maxP * 0.02;

        return (
          <div key={symbol} style={styles.card}>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
              {symbol}
              {prices.length > 0 && (
                <span style={{ fontWeight: 400, color: '#6b7280', marginLeft: 8 }}>
                  {fmt(prices[prices.length - 1])}
                </span>
              )}
            </div>
            <ResponsiveContainer width="100%" height={180}>
              <LineChart 
                data={chartData}
                margin={{ top: 10, right: 10, bottom: 5, left: 5 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                <XAxis
                  dataKey="time"
                  tick={{ fontSize: 10, fill: '#9ca3af' }}
                  interval="preserveStartEnd"
                />
                <YAxis
                  domain={[minP - pad, maxP + pad]}
                  tick={{ fontSize: 10, fill: '#9ca3af' }}
                  tickFormatter={(v) => `${v.toLocaleString()} USDT`}
                  width={80}
                  allowDecimals={true}
                />
                <Tooltip
                  formatter={(v) => [fmt(v), symbol]}
                  labelStyle={{ fontSize: 11, color: '#6b7280' }}
                  contentStyle={{ fontSize: 12, borderRadius: 6, border: '1px solid #e5e7eb' }}
                />
                <Line
                  type="monotone"
                  dataKey="price"
                  stroke={COLORS[idx % COLORS.length]}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 3 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        );
      })}
    </div>
  );
}

function SystemStatusWidget({ health }) {
  const statusMap = health?.status || {};
  const entries = Object.entries(statusMap);

  if (entries.length === 0) return <EmptyState message="No status data" />;

  const icons = { OK: '●', Down: '●', 'N/A': '○', Unknown: '○' };
  const colors = { OK: '#10b981', Down: '#ef4444', 'N/A': '#9ca3af', Unknown: '#f59e0b' };

  return (
    <div style={{ ...styles.card, display: 'flex', gap: 32, flexWrap: 'wrap' }}>
      {entries.map(([key, value]) => (
        <div key={key} style={{ minWidth: 80 }}>
          <div style={{ fontSize: 11, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4 }}>
            {key}
          </div>
          <div style={{ fontSize: 16, fontWeight: 600, color: colors[value] || '#6b7280', display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 10 }}>{icons[value] || '○'}</span> {value}
          </div>
        </div>
      ))}
    </div>
  );
}

/* ──────────────────── shared components ──────────────────── */

function EmptyState({ message, children }) {
  return (
    <div style={{
      padding: '32px 20px', textAlign: 'center', backgroundColor: '#f9fafb',
      borderRadius: 8, border: '1px dashed #d1d5db', color: '#6b7280', fontSize: 14,
    }}>
      <p style={{ marginBottom: children ? 16 : 0 }}>{message}</p>
      {children}
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
      height, borderRadius: 8, background: 'linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%)',
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
  cardGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
    gap: 16,
    marginBottom: 28,
  },
  portfolioGrid: {
    display: 'grid',
    gridTemplateColumns: '1fr 2fr',
    gap: 24,
    alignItems: 'start',
  },
  splitGrid: {
    display: 'grid',
    gridTemplateColumns: '2fr 1fr',
    gap: 24,
    marginBottom: 8,
  },
  card: {
    padding: 16,
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e5e7eb',
    boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
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
    transition: 'background-color 0.15s',
  },
  td: {
    padding: '10px 14px',
    fontSize: 14,
  },
  primaryBtn: {
    display: 'inline-block',
    padding: '8px 20px',
    backgroundColor: '#3b82f6',
    color: '#fff',
    textDecoration: 'none',
    borderRadius: 6,
    fontWeight: 500,
    fontSize: 14,
  },
  retryBtn: {
    padding: '6px 14px',
    backgroundColor: '#dc2626',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 13,
    fontWeight: 500,
  },
  skeletonBar: {
    height: 28,
    width: '60%',
    borderRadius: 4,
    background: 'linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%)',
    backgroundSize: '200% 100%',
    animation: 'shimmer 1.5s infinite',
  },
};

export default DashboardPage;
