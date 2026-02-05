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
    <div style={{ maxWidth: '400px', margin: '50px auto', padding: '20px' }}>
      <h1>Login</h1>
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>
            Login or Email:
          </label>
            <input
            type="text"
            value={loginOrEmail}
            onChange={(e) => setLoginOrEmail(e.target.value)}
              required
              disabled={loading}
            style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          {errors.loginOrEmail && <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>{errors.loginOrEmail}</div>}
        </div>
        
        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>
            Password:
          </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={loading}
            style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          {errors.password && <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>{errors.password}</div>}
        </div>
        
        {error && (
          <div style={{ color: 'red', marginBottom: '15px', padding: '10px', backgroundColor: '#ffe6e6', borderRadius: '4px' }}>
            {error}
          </div>
        )}
        
        <button 
          type="submit" 
          disabled={loading}
          style={{ 
            width: '100%', 
            padding: '10px', 
            backgroundColor: '#007bff', 
            color: 'white', 
            border: 'none', 
            borderRadius: '4px',
            cursor: loading ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.6 : 1
          }}
        >
          {loading ? 'Logging in...' : 'Login'}
        </button>
      </form>
      
      <div style={{ marginTop: '15px', textAlign: 'center' }}>
        <Link to="/register" style={{ color: '#007bff', textDecoration: 'none' }}>
          Don't have an account? Register
        </Link>
      </div>
    </div>
  );
}

export default LoginPage;
