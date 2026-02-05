import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../shared/context/AuthContext';

function RegisterPage() {
  const navigate = useNavigate();
  const { register, isAuthenticated, user } = useAuth();
  const [login, setLogin] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Redirect if already authenticated (but not from registration)
  useEffect(() => {
    if (isAuthenticated && user) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, user, navigate]);

  const validate = () => {
    const newErrors = {};
    
    if (!login.trim()) {
      newErrors.login = 'Login is required';
    } else if (login.length < 3) {
      newErrors.login = 'Login must be at least 3 characters';
    } else if (login.length > 50) {
      newErrors.login = 'Login must be at most 50 characters';
    }
    
    if (!email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Please enter a valid email address';
    }
    
    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < 8) {
      newErrors.password = 'Password must be at least 8 characters';
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
      await register({ login, email, password });
      setLoading(false);
      // Redirect to login page after successful registration
      navigate('/login', { replace: true });
    } catch (err) {
      const errorMessage = err.response?.data?.message || err.message || 'Registration failed. Please try again.';
      if (err.response?.status === 409) {
        setError(errorMessage);
      } else if (err.response?.data?.errors) {
        setErrors(err.response.data.errors);
      } else {
        setError(errorMessage);
      }
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '400px', margin: '50px auto', padding: '20px' }}>
      <h1>Register</h1>
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>
            Login:
          </label>
          <input
            type="text"
            value={login}
            onChange={(e) => setLogin(e.target.value)}
            required
            disabled={loading}
            style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
          />
          {errors.login && <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>{errors.login}</div>}
        </div>
        
        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>
            Email:
          </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={loading}
            style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          {errors.email && <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>{errors.email}</div>}
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
          {loading ? 'Registering...' : 'Register'}
        </button>
      </form>
      
      <div style={{ marginTop: '15px', textAlign: 'center' }}>
        <Link to="/login" style={{ color: '#007bff', textDecoration: 'none' }}>
          Already have an account? Login
        </Link>
      </div>
    </div>
  );
}

export default RegisterPage;
