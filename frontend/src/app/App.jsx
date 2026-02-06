import { useState, useEffect, useCallback } from 'react';
import { Outlet, NavLink, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../shared/context/AuthContext';
import { getCashBalance } from '../shared/api/services/walletService';
import Footer from '../shared/components/Footer';
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
      <header className="app-header">
        <div className="header-left">
          <h1 className="header-logo">Crypto Exchange Simulator</h1>
          {!isAuthPage && isAuthenticated && (
            <nav className="header-nav">
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
          )}
        </div>
        {isAuthenticated && user && (
          <div className="header-right">
            {/* Cash Balance Badge */}
            <div className="balance-badge">
              <span className="balance-label">USDT Balance</span>
              <span className="balance-value">
                {fmtUsdt(cashBalance?.cashUsd)}
              </span>
            </div>
            <div className="user-info">
              <div className="user-details">
                <span className="user-name">{user.login || user.email}</span>
                <span className="user-email">{user.email}</span>
              </div>
              <Link
                to="/account"
                title="Account Settings"
                className="account-link"
              >
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
              </Link>
            </div>
            <button
              onClick={logout}
              className="logout-btn"
            >
              Logout
            </button>
          </div>
        )}
      </header>
      {!isAuthPage && (
        <div className="app-body">
          <div className="app-main-wrapper">
            <main className="app-main">
              <Outlet context={{ cashBalance, refreshCashBalance: loadCashBalance }} />
            </main>
            <Footer />
          </div>
        </div>
      )}
      {isAuthPage && (
        <main className="auth-main">
          <Outlet />
        </main>
      )}
    </div>
  );
}

export default App;
