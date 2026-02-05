import { Outlet, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../shared/context/AuthContext';
import '../index.css';

function App() {
  const { user, logout, isAuthenticated } = useAuth();
  const location = useLocation();
  const isAuthPage = location.pathname === '/login' || location.pathname === '/register';

  return (
    <div className="app-container">
      <header className="app-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>Crypto Exchange Simulator</h1>
        {isAuthenticated && user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
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
              <NavLink to="/assets" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
                Assets
              </NavLink>
              <NavLink to="/transactions" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
                Transactions
              </NavLink>
            </nav>
          </aside>
          <main className="app-main">
            <Outlet />
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
