import { useState, useEffect, useCallback } from 'react';
import { useOutletContext } from 'react-router-dom';
import { getCashBalance, cashDeposit } from '../shared/api/services/walletService';

const fmtUsdt = (v) => {
  if (v == null || isNaN(Number(v))) return '0.00 USDT';
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(Number(v)) + ' USDT';
};

function DepositPage() {
  const { refreshCashBalance } = useOutletContext() || {};
  const [balance, setBalance] = useState(null);
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const loadBalance = useCallback(async () => {
    try {
      setLoading(true);
      const data = await getCashBalance();
      setBalance(data);
    } catch (err) {
      setError('Failed to load balance information');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadBalance();
  }, [loadBalance]);

  const handleDeposit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setError('Please enter a valid positive amount');
      return;
    }

    try {
      setSubmitting(true);
      const data = await cashDeposit(numAmount);
      setBalance(data);
      setSuccess(`Successfully deposited ${fmtUsdt(numAmount)}!`);
      setAmount('');
      // Refresh the header balance display
      if (refreshCashBalance) refreshCashBalance();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Deposit failed';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const quickAmounts = [50, 100, 250, 500];

  return (
    <div style={{ maxWidth: 600, margin: '0 auto', overflow: 'hidden' }}>
      <h1 className="resp-page-title" style={{ fontSize: 28, fontWeight: 700, color: '#111827', marginBottom: 24 }}>
        Deposit USDT
      </h1>

      {/* Balance Card */}
      <div style={styles.card}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#374151', marginBottom: 16 }}>
          USDT Balance
        </h2>
        {loading ? (
          <div style={styles.skeleton} />
        ) : balance ? (
          <div>
            <div className="resp-price-large" style={{ fontSize: 32, fontWeight: 700, color: '#111827', marginBottom: 16 }}>
              {fmtUsdt(balance.cashUsd)}
            </div>
            <div className="resp-grid-limits">
              <div>
                <div style={styles.limitLabel}>24h Deposit Limit</div>
                <div style={styles.limitValue}>{fmtUsdt(balance.depositLimit24h)}</div>
              </div>
              <div>
                <div style={styles.limitLabel}>Deposited (24h)</div>
                <div style={styles.limitValue}>{fmtUsdt(balance.depositedLast24h)}</div>
              </div>
              <div>
                <div style={styles.limitLabel}>Remaining</div>
                <div style={{ ...styles.limitValue, color: '#10b981', fontWeight: 700 }}>
                  {fmtUsdt(balance.remainingLimit24h)}
                </div>
              </div>
            </div>
            {/* Progress bar */}
            <div style={styles.progressContainer}>
              <div
                style={{
                  ...styles.progressBar,
                  width: `${Math.min(100, (Number(balance.depositedLast24h) / Number(balance.depositLimit24h)) * 100)}%`,
                  backgroundColor: Number(balance.depositedLast24h) >= Number(balance.depositLimit24h) ? '#ef4444' : '#3b82f6',
                }}
              />
            </div>
            <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 4, textAlign: 'right' }}>
              {fmtUsdt(balance.depositedLast24h)} / {fmtUsdt(balance.depositLimit24h)} used
            </div>
          </div>
        ) : null}
      </div>

      {/* Deposit Form */}
      <div style={{ ...styles.card, marginTop: 20 }}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#374151', marginBottom: 16 }}>
          Make a Deposit
        </h2>

        {/* Quick amount buttons */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginBottom: 16 }}>
          {quickAmounts.map((qa) => (
            <button
              key={qa}
              onClick={() => { setAmount(String(qa)); setError(null); setSuccess(null); }}
              style={{
                ...styles.quickBtn,
                backgroundColor: amount === String(qa) ? '#3b82f6' : '#f3f4f6',
                color: amount === String(qa) ? '#fff' : '#374151',
              }}
            >
              {qa} USDT
            </button>
          ))}
        </div>

        <form onSubmit={handleDeposit}>
          <div style={{ marginBottom: 16 }}>
            <label style={styles.label}>Amount (USDT)</label>
            <div style={{ position: 'relative' }}>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={amount}
                onChange={(e) => { setAmount(e.target.value); setError(null); setSuccess(null); }}
                placeholder="0.00"
                style={styles.input}
                disabled={submitting}
              />
              <span style={styles.inputSuffix}>USDT</span>
            </div>
          </div>

          {error && (
            <div style={styles.errorBox}>
              {error}
            </div>
          )}

          {success && (
            <div style={styles.successBox}>
              {success}
            </div>
          )}

          <button
            type="submit"
            disabled={submitting || !amount}
            style={{
              ...styles.submitBtn,
              opacity: submitting || !amount ? 0.6 : 1,
              cursor: submitting || !amount ? 'not-allowed' : 'pointer',
            }}
          >
            {submitting ? 'Processing...' : 'Deposit USDT'}
          </button>
        </form>

        <p style={{ fontSize: 12, color: '#9ca3af', marginTop: 12 }}>
          Deposits are limited to 1,000 USDT within any rolling 24-hour window. This is a simulated deposit — no real money is involved.
        </p>
      </div>
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
  skeleton: {
    height: 80,
    borderRadius: 8,
    background: 'linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%)',
    backgroundSize: '200% 100%',
    animation: 'shimmer 1.5s infinite',
  },
  /* limitGrid moved to CSS class resp-grid-limits */
  limitLabel: {
    fontSize: 12,
    color: '#6b7280',
    marginBottom: 2,
  },
  limitValue: {
    fontSize: 15,
    fontWeight: 600,
    color: '#111827',
  },
  progressContainer: {
    height: 6,
    backgroundColor: '#e5e7eb',
    borderRadius: 3,
    overflow: 'hidden',
    marginTop: 8,
  },
  progressBar: {
    height: '100%',
    borderRadius: 3,
    transition: 'width 0.3s ease',
  },
  quickBtn: {
    padding: '8px 12px',
    border: '1px solid #e5e7eb',
    borderRadius: 6,
    cursor: 'pointer',
    fontSize: 13,
    fontWeight: 500,
    transition: 'all 0.15s',
    textAlign: 'center',
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
    padding: '10px 70px 10px 12px',
    fontSize: 16,
    border: '1px solid #d1d5db',
    borderRadius: 6,
    outline: 'none',
    boxSizing: 'border-box',
  },
  inputSuffix: {
    position: 'absolute',
    right: 12,
    top: '50%',
    transform: 'translateY(-50%)',
    fontSize: 14,
    color: '#6b7280',
    fontWeight: 600,
    pointerEvents: 'none',
  },
  submitBtn: {
    width: '100%',
    padding: '12px 20px',
    backgroundColor: '#10b981',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    fontSize: 16,
    fontWeight: 600,
    transition: 'opacity 0.15s',
    cursor: 'pointer',
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

export default DepositPage;
