import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../shared/context/AuthContext';

function LoginPage() {
  const navigate = useNavigate();
  const { login, isAuthenticated, user } = useAuth();
  const [loginOrEmail, setLoginOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated && user) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, user, navigate]);

  const validate = () => {
    const newErrors = {};
    
    if (!loginOrEmail.trim()) {
      newErrors.loginOrEmail = 'Login or email is required';
    }
    
    if (!password) {
      newErrors.password = 'Password is required';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setErrors({});
    
    if (!validate()) {
      return;
    }
    
    setLoading(true);

    try {
      await login({ loginOrEmail, password });
      // Navigation will happen via useEffect when user state updates
    } catch (err) {
      const errorMessage = err.response?.data?.message || err.message || 'Login failed. Please try again.';
      if (err.response?.data?.errors) {
        setErrors(err.response.data.errors);
      } else {
        setError(errorMessage);
      }
      setLoading(false);
    }
  };

  return (
    <div className="form-container">
      <h1 style={{ marginBottom: '1.5rem', textAlign: 'center', color: 'var(--text-primary)' }}>Login</h1>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-label">
            Login or Email:
          </label>
          <input
            type="text"
            value={loginOrEmail}
            onChange={(e) => setLoginOrEmail(e.target.value)}
            required
            disabled={loading}
            className={`form-input ${errors.loginOrEmail ? 'error' : ''}`}
          />
          {errors.loginOrEmail && <div className="error-message">{errors.loginOrEmail}</div>}
        </div>
        
        <div className="form-group">
          <label className="form-label">
            Password:
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            disabled={loading}
            className={`form-input ${errors.password ? 'error' : ''}`}
          />
          {errors.password && <div className="error-message">{errors.password}</div>}
        </div>
        
        {error && (
          <div className="error-banner">
            {error}
          </div>
        )}
        
        <button 
          type="submit" 
          disabled={loading}
          className="btn btn-primary"
        >
          {loading ? 'Logging in...' : 'Login'}
        </button>
      </form>
      
      <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
        <Link to="/register" className="form-link">
          Don't have an account? Register
        </Link>
      </div>
    </div>
  );
}

export default LoginPage;
