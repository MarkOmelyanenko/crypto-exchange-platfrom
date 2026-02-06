import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getWalletBalances, getCashBalance } from '../shared/api/services/walletService';

const fmt = (v, decimals = 2) => {
  if (v == null || isNaN(Number(v))) return '0';
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: Math.max(decimals, 8),
  }).format(Number(v));
};

const fmtUsd = (v) => {
  if (v == null || isNaN(Number(v))) return '0.00 USDT';
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(Number(v)) + ' USDT';
};

function WalletPage() {
  const [balances, setBalances] = useState([]);
  const [cashInfo, setCashInfo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [bals, cash] = await Promise.all([getWalletBalances(), getCashBalance()]);
      setBalances(bals || []);
      setCashInfo(cash);
    } catch (err) {
      setError('Failed to load wallet data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Split USDT out to show prominently
  const usdtBalance = balances.find(b => b.asset === 'USDT');
  const otherBalances = balances.filter(b => b.asset !== 'USDT' && Number(b.available) > 0);

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, color: '#111827', margin: 0 }}>Wallet</h1>
        <Link to="/deposit" style={styles.depositBtn}>+ Deposit USDT</Link>
      </div>

      {error && <div style={styles.errorBox}>{error}</div>}

      {loading ? (
        <div style={styles.skeleton} />
      ) : (
        <>
          {/* USDT / Cash Card */}
          <div style={{ ...styles.card, borderLeft: '4px solid #10b981', marginBottom: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 4 }}>USDT (Cash)</div>
                <div style={{ fontSize: 32, fontWeight: 700, color: '#111827' }}>
                  {fmtUsd(usdtBalance?.available || cashInfo?.cashUsd || 0)}
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                {cashInfo && (
                  <>
                    <div style={{ fontSize: 12, color: '#6b7280' }}>
                      24h Deposit Limit: {fmtUsd(cashInfo.depositLimit24h)}
                    </div>
                    <div style={{ fontSize: 12, color: '#6b7280' }}>
                      Remaining: <span style={{ color: '#10b981', fontWeight: 600 }}>
                        {fmtUsd(cashInfo.remainingLimit24h)}
                      </span>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>

          {/* Other Assets */}
          {otherBalances.length > 0 && (
            <div style={styles.card}>
              <h2 style={{ fontSize: 16, fontWeight: 600, color: '#374151', marginBottom: 16 }}>
                Asset Holdings
              </h2>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid #e5e7eb' }}>
                    <th style={styles.th}>Asset</th>
                    <th style={{ ...styles.th, textAlign: 'right' }}>Available</th>
                  </tr>
                </thead>
                <tbody>
                  {otherBalances.map(b => (
                    <tr key={b.asset} style={{ borderBottom: '1px solid #f3f4f6' }}>
                      <td style={styles.td}>
                        <span style={{ fontWeight: 600 }}>{b.asset}</span>
                      </td>
                      <td style={{ ...styles.td, textAlign: 'right', fontFamily: 'monospace' }}>
                        {fmt(b.available, 8)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {otherBalances.length === 0 && !loading && (
            <div style={{ ...styles.card, textAlign: 'center', color: '#9ca3af', padding: 40 }}>
              <p style={{ fontSize: 16, marginBottom: 8 }}>No asset holdings yet</p>
              <p style={{ fontSize: 13 }}>
                <Link to="/deposit" style={{ color: '#3b82f6' }}>Deposit USDT</Link> and{' '}
                <Link to="/trade" style={{ color: '#3b82f6' }}>start trading</Link> to build your portfolio.
              </p>
            </div>
          )}
        </>
      )}
    </div>
  );
}

const styles = {
  card: {
    padding: 20,
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e5e7eb',
    boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
  },
  depositBtn: {
    padding: '10px 20px',
    backgroundColor: '#10b981',
    color: '#fff',
    borderRadius: 6,
    textDecoration: 'none',
    fontSize: 14,
    fontWeight: 600,
  },
  th: {
    padding: '10px 12px',
    textAlign: 'left',
    fontSize: 12,
    fontWeight: 600,
    color: '#6b7280',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  td: {
    padding: '12px',
    fontSize: 14,
    color: '#111827',
  },
  skeleton: {
    height: 200,
    borderRadius: 8,
    background: 'linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%)',
    backgroundSize: '200% 100%',
    animation: 'shimmer 1.5s infinite',
  },
  errorBox: {
    padding: '10px 14px',
    backgroundColor: '#fef2f2',
    border: '1px solid #fecaca',
    borderRadius: 6,
    color: '#dc2626',
    fontSize: 13,
    marginBottom: 16,
  },
};

export default WalletPage;
