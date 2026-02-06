import { useState, useEffect, useCallback } from 'react';
import { Outlet, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../shared/context/AuthContext';
import { getCashBalance } from '../shared/api/services/walletService';
import '../index.css';

function App() {
  const { user, logout, isAuthenticated } = useAuth();
  const location = useLocation();
  const isAuthPage = location.pathname === '/login' || location.pathname === '/register';
  const [cashBalance, setCashBalance] = useState(null);

  const loadCashBalance = useCallback(async () => {
    try {
      const data = await getCashBalance();
      setCashBalance(data);
    } catch {
      // silently fail — balance will show as loading
    }
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      loadCashBalance();
    } else {
      setCashBalance(null);
    }
  }, [isAuthenticated, loadCashBalance]);

  // Refresh balance when navigating back from deposit page
  useEffect(() => {
    if (isAuthenticated && location.pathname !== '/deposit') {
      loadCashBalance();
    }
  }, [location.pathname, isAuthenticated, loadCashBalance]);

  const fmtUsdt = (v) => {
    if (v == null || isNaN(Number(v))) return '...';
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    }).format(Number(v)) + ' USDT';
  };

  return (
    <div className="app-container">
      <header className="app-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>Crypto Exchange Simulator</h1>
        {isAuthenticated && user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
            {/* Cash Balance Badge */}
            <div style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center',
              backgroundColor: 'rgba(255,255,255,0.1)', padding: '6px 14px', borderRadius: 6,
            }}>
              <span style={{ fontSize: 11, color: '#9ca3af', letterSpacing: '0.03em' }}>USDT Balance</span>
              <span style={{ fontSize: 16, fontWeight: 700, color: '#10b981' }}>
                {fmtUsdt(cashBalance?.cashUsd)}
              </span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
              <span style={{ fontSize: '14px', fontWeight: 'bold' }}>{user.login || user.email}</span>
              <span style={{ fontSize: '12px', color: '#666' }}>{user.email}</span>
            </div>
            <button
              onClick={logout}
              style={{
                padding: '8px 16px',
                backgroundColor: '#dc3545',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '14px'
              }}
            >
              Logout
            </button>
          </div>
        )}
      </header>
      {!isAuthPage && (
      <div className="app-body">
        <aside className="app-sidebar">
          <nav className="sidebar-nav">
            <NavLink to="/dashboard" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              Dashboard
            </NavLink>
            <NavLink to="/wallet" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              Wallet
            </NavLink>
            <NavLink to="/assets" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              Assets
            </NavLink>
            <NavLink to="/deposit" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              Deposit USDT
            </NavLink>
            <NavLink to="/trade" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              Trade
            </NavLink>
            <NavLink to="/transactions" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              Transactions
            </NavLink>
          </nav>
        </aside>
        <main className="app-main">
          <Outlet context={{ cashBalance, refreshCashBalance: loadCashBalance }} />
        </main>
      </div>
      )}
      {isAuthPage && (
        <main style={{ minHeight: 'calc(100vh - 80px)' }}>
          <Outlet />
        </main>
      )}
    </div>
  );
}

export default App;
