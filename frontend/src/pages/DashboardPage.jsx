import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getSummary, getHoldings, getRecentTransactions } from '../shared/api/services/dashboardService';
import { getSnapshot, getHistory } from '../shared/api/services/priceService';
import { getHealth } from '../shared/api/services/systemService';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [holdings, setHoldings] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [priceHistory, setPriceHistory] = useState([]);
  const [systemHealth, setSystemHealth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    loadDashboardData();
    // Poll for price updates every 10 seconds
    const interval = setInterval(() => {
      loadPriceData();
    }, 10000);
    return () => clearInterval(interval);
  }, []);

  const loadDashboardData = async () => {
    setLoading(true);
    setErrors({});

    try {
      const [summaryData, holdingsData, transactionsData, healthData] = await Promise.all([
        getSummary().catch(err => ({ error: err })),
        getHoldings().catch(err => ({ error: err })),
        getRecentTransactions(10).catch(err => ({ error: err })),
        getHealth().catch(err => ({ error: err }))
      ]);

      if (!summaryData.error) setSummary(summaryData);
      else setErrors(prev => ({ ...prev, summary: summaryData.error }));

      if (!holdingsData.error) setHoldings(holdingsData);
      else setErrors(prev => ({ ...prev, holdings: holdingsData.error }));

      if (!transactionsData.error) setTransactions(transactionsData);
      else setErrors(prev => ({ ...prev, transactions: transactionsData.error }));

      if (!healthData.error) setSystemHealth(healthData);
      else setErrors(prev => ({ ...prev, health: healthData.error }));

      // Load price data for top holdings
      if (!holdingsData.error && holdingsData.length > 0) {
        loadPriceData(holdingsData);
      }
    } catch (err) {
      setErrors({ general: err.message || 'Failed to load dashboard data' });
    } finally {
      setLoading(false);
    }
  };

  const loadPriceData = async (holdingsData = holdings) => {
    try {
      // Get top 3-5 assets by market value, or fallback to BTC, ETH, SOL
      const topSymbols = holdingsData.length > 0
        ? holdingsData
            .sort((a, b) => b.marketValueUsd - a.marketValueUsd)
            .slice(0, 5)
            .map(h => h.symbol)
        : ['BTC', 'ETH', 'SOL'];

      const historyPromises = topSymbols.slice(0, 3).map(symbol =>
        getHistory(symbol, '24h').then(data => ({ symbol, data }))
      );

      const histories = await Promise.all(historyPromises);
      setPriceHistory(histories);
    } catch (err) {
      console.error('Failed to load price history:', err);
    }
  };

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  };

  const formatPercent = (value) => {
    return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString();
  };

  if (loading && !summary) {
    return (
      <div style={{ padding: '20px' }}>
        <h1>Dashboard</h1>
        <div>Loading...</div>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px', maxWidth: '1400px', margin: '0 auto' }}>
      <h1>Dashboard</h1>

      {/* Portfolio Summary Cards */}
      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', 
        gap: '20px', 
        marginBottom: '30px' 
      }}>
        <SummaryCard
          title="Total Portfolio Value"
          value={summary ? formatCurrency(summary.totalValueUsd) : '—'}
          loading={loading && !summary}
        />
        <SummaryCard
          title="Available Cash"
          value={summary ? formatCurrency(summary.availableCashUsd) : '—'}
          loading={loading && !summary}
        />
        <SummaryCard
          title="Unrealized PnL"
          value={summary ? `${formatCurrency(summary.unrealizedPnlUsd)} (${formatPercent(summary.unrealizedPnlPercent)})` : '—'}
          color={summary && summary.unrealizedPnlUsd >= 0 ? '#10b981' : '#ef4444'}
          loading={loading && !summary}
        />
        <SummaryCard
          title="Realized PnL"
          value={summary ? formatCurrency(summary.realizedPnlUsd) : '—'}
          color={summary && summary.realizedPnlUsd >= 0 ? '#10b981' : '#ef4444'}
          loading={loading && !summary}
        />
      </div>

      {/* Holdings Table */}
      <div style={{ marginBottom: '30px' }}>
        <h2>Holdings</h2>
        {errors.holdings ? (
          <ErrorBox error={errors.holdings} onRetry={() => loadDashboardData()} />
        ) : (
          <HoldingsTable holdings={holdings} loading={loading} />
        )}
      </div>

      {/* Price Chart and Recent Transactions */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', marginBottom: '30px' }}>
        <div>
          <h2>Price Trends (24h)</h2>
          {priceHistory.length > 0 ? (
            <PriceChart data={priceHistory} />
          ) : (
            <div style={{ padding: '40px', textAlign: 'center', backgroundColor: '#f3f4f6', borderRadius: '8px' }}>
              {loading ? 'Loading chart...' : 'No price data available'}
            </div>
          )}
        </div>
        <div>
          <h2>Recent Transactions</h2>
          {errors.transactions ? (
            <ErrorBox error={errors.transactions} onRetry={() => loadDashboardData()} />
          ) : (
            <TransactionsList transactions={transactions} loading={loading} />
          )}
        </div>
      </div>

      {/* System Status */}
      <div style={{ marginBottom: '30px' }}>
        <h2>System Status</h2>
        {systemHealth ? (
          <SystemStatusWidget health={systemHealth} />
        ) : (
          <div style={{ padding: '20px', backgroundColor: '#f3f4f6', borderRadius: '8px' }}>
            {loading ? 'Loading...' : 'Status unavailable'}
          </div>
        )}
      </div>
    </div>
  );
}

function SummaryCard({ title, value, color, loading }) {
  return (
    <div style={{
      padding: '20px',
      backgroundColor: '#fff',
      borderRadius: '8px',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      border: '1px solid #e5e7eb'
    }}>
      <div style={{ fontSize: '14px', color: '#6b7280', marginBottom: '8px' }}>{title}</div>
      <div style={{ fontSize: '24px', fontWeight: 'bold', color: color || '#111827' }}>
        {loading ? '...' : value}
      </div>
    </div>
  );
}

function HoldingsTable({ holdings, loading }) {
  const [sortBy, setSortBy] = useState('marketValue');
  const [sortOrder, setSortOrder] = useState('desc');

  const sortedHoldings = [...holdings].sort((a, b) => {
    const aVal = sortBy === 'marketValue' ? a.marketValueUsd : a.unrealizedPnlUsd;
    const bVal = sortBy === 'marketValue' ? b.marketValueUsd : b.unrealizedPnlUsd;
    return sortOrder === 'desc' ? bVal - aVal : aVal - bVal;
  });

  const handleSort = (field) => {
    if (sortBy === field) {
      setSortOrder(sortOrder === 'desc' ? 'asc' : 'desc');
    } else {
      setSortBy(field);
      setSortOrder('desc');
    }
  };

  if (loading && holdings.length === 0) {
    return <div style={{ padding: '40px', textAlign: 'center' }}>Loading holdings...</div>;
  }

  if (holdings.length === 0) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', backgroundColor: '#f3f4f6', borderRadius: '8px' }}>
        <p style={{ marginBottom: '20px' }}>No holdings yet</p>
        <Link to="/assets" style={{
          display: 'inline-block',
          padding: '10px 20px',
          backgroundColor: '#007bff',
          color: 'white',
          textDecoration: 'none',
          borderRadius: '4px'
        }}>
          Buy your first asset
        </Link>
      </div>
    );
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: '#fff', borderRadius: '8px' }}>
        <thead>
          <tr style={{ backgroundColor: '#f9fafb', borderBottom: '2px solid #e5e7eb' }}>
            <th style={{ padding: '12px', textAlign: 'left' }}>Asset</th>
            <th style={{ padding: '12px', textAlign: 'right' }}>Quantity</th>
            <th style={{ padding: '12px', textAlign: 'right' }}>Avg Buy Price</th>
            <th style={{ padding: '12px', textAlign: 'right' }}>Current Price</th>
            <th 
              style={{ padding: '12px', textAlign: 'right', cursor: 'pointer' }}
              onClick={() => handleSort('marketValue')}
            >
              Market Value {sortBy === 'marketValue' && (sortOrder === 'desc' ? '↓' : '↑')}
            </th>
            <th 
              style={{ padding: '12px', textAlign: 'right', cursor: 'pointer' }}
              onClick={() => handleSort('pnl')}
            >
              Unrealized PnL {sortBy === 'pnl' && (sortOrder === 'desc' ? '↓' : '↑')}
            </th>
          </tr>
        </thead>
        <tbody>
          {sortedHoldings.map((holding) => (
            <tr key={holding.assetId} style={{ borderBottom: '1px solid #e5e7eb' }}>
              <td style={{ padding: '12px' }}>
                <div style={{ fontWeight: 'bold' }}>{holding.symbol}</div>
                <div style={{ fontSize: '12px', color: '#6b7280' }}>{holding.name}</div>
              </td>
              <td style={{ padding: '12px', textAlign: 'right' }}>{holding.quantity.toFixed(8)}</td>
              <td style={{ padding: '12px', textAlign: 'right' }}>
                ${holding.avgBuyPriceUsd.toFixed(2)}
              </td>
              <td style={{ padding: '12px', textAlign: 'right' }}>
                ${holding.currentPriceUsd.toFixed(2)}
              </td>
              <td style={{ padding: '12px', textAlign: 'right' }}>
                ${holding.marketValueUsd.toFixed(2)}
              </td>
              <td style={{ 
                padding: '12px', 
                textAlign: 'right',
                color: holding.unrealizedPnlUsd >= 0 ? '#10b981' : '#ef4444'
              }}>
                ${holding.unrealizedPnlUsd.toFixed(2)} ({formatPercent(holding.unrealizedPnlPercent)})
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TransactionsList({ transactions, loading }) {
  if (loading && transactions.length === 0) {
    return <div style={{ padding: '20px' }}>Loading transactions...</div>;
  }

  if (transactions.length === 0) {
    return (
      <div style={{ padding: '20px', backgroundColor: '#f3f4f6', borderRadius: '8px', textAlign: 'center' }}>
        No recent transactions
      </div>
    );
  }

  return (
    <div style={{ backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #e5e7eb' }}>
      {transactions.map((tx) => (
        <div key={tx.id} style={{ 
          padding: '12px', 
          borderBottom: '1px solid #e5e7eb',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div>
            <div style={{ fontWeight: 'bold', color: tx.type === 'BUY' ? '#10b981' : '#ef4444' }}>
              {tx.type} {tx.symbol}
            </div>
            <div style={{ fontSize: '12px', color: '#6b7280' }}>
              {formatDate(tx.timestamp)}
            </div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div>{tx.quantity.toFixed(8)}</div>
            <div style={{ fontSize: '12px', color: '#6b7280' }}>
              @ ${tx.priceUsd.toFixed(2)}
            </div>
          </div>
        </div>
      ))}
      <div style={{ padding: '12px', textAlign: 'center', borderTop: '1px solid #e5e7eb' }}>
        <Link to="/transactions" style={{ color: '#007bff', textDecoration: 'none' }}>
          View all →
        </Link>
      </div>
    </div>
  );
}

function PriceChart({ data }) {
  // Transform data for Recharts - combine all symbols into one chart
  const chartData = [];
  const maxLength = Math.max(...data.map(d => d.data.length));

  for (let i = 0; i < maxLength; i++) {
    const point = { index: i };
    data.forEach(({ symbol, data: history }) => {
      if (history[i]) {
        point[symbol] = history[i].priceUsd;
        if (!point.time) {
          point.time = new Date(history[i].timestamp).toLocaleTimeString();
        }
      }
    });
    if (Object.keys(point).length > 1) {
      chartData.push(point);
    }
  }

  const colors = ['#3b82f6', '#10b981', '#f59e0b'];

  return (
    <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #e5e7eb' }}>
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="time" />
          <YAxis />
          <Tooltip formatter={(value) => `$${value.toFixed(2)}`} />
          {data.map(({ symbol }, index) => (
            <Line 
              key={symbol}
              type="monotone" 
              dataKey={symbol} 
              stroke={colors[index % colors.length]}
              strokeWidth={2}
              dot={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function SystemStatusWidget({ health }) {
  const getStatusColor = (status) => {
    if (status === 'OK') return '#10b981';
    if (status === 'Down') return '#ef4444';
    return '#6b7280';
  };

  return (
    <div style={{ 
      display: 'flex', 
      gap: '20px',
      backgroundColor: '#fff',
      padding: '20px',
      borderRadius: '8px',
      border: '1px solid #e5e7eb'
    }}>
      {Object.entries(health.status || {}).map(([key, value]) => (
        <div key={key} style={{ flex: 1 }}>
          <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '4px' }}>
            {key.toUpperCase()}
          </div>
          <div style={{ 
            fontSize: '18px', 
            fontWeight: 'bold',
            color: getStatusColor(value)
          }}>
            {value}
          </div>
        </div>
      ))}
    </div>
  );
}

function ErrorBox({ error, onRetry }) {
  const errorMessage = error?.response?.data?.message || error?.message || 'An error occurred';
  
  return (
    <div style={{ 
      padding: '20px', 
      backgroundColor: '#fee2e2', 
      borderRadius: '8px',
      border: '1px solid #fecaca'
    }}>
      <div style={{ color: '#dc2626', marginBottom: '10px' }}>{errorMessage}</div>
      <button 
        onClick={onRetry}
        style={{
          padding: '8px 16px',
          backgroundColor: '#dc2626',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer'
        }}
      >
        Retry
      </button>
    </div>
  );
}

function formatPercent(value) {
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

export default DashboardPage;
