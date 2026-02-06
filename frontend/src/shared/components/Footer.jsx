import { Link } from 'react-router-dom';
import packageJson from '../../../package.json';

// Get version from package.json, fallback to v0.1.0 if version is 0.0.0
const APP_VERSION = packageJson.version && packageJson.version !== '0.0.0' 
  ? packageJson.version 
  : '0.1.0';

function Footer() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="app-footer">
      <div className="footer-content">
        <div className="footer-left">
          <span>© {currentYear} Crypto Exchange Simulator</span>
        </div>
        <div className="footer-center">
          <nav className="footer-nav">
            <Link to="/dashboard" className="footer-link">Dashboard</Link>
            <Link to="/wallet" className="footer-link">Wallet</Link>
            <Link to="/trade" className="footer-link">Trade</Link>
            <Link to="/transactions" className="footer-link">Transactions</Link>
          </nav>
        </div>
        <div className="footer-right">
          <span>v{APP_VERSION}</span>
        </div>
      </div>
    </footer>
  );
}

export default Footer;
