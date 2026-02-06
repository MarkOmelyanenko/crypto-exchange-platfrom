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

  const validatePassword = (pwd) => {
    const requirements = {
      minLength: pwd.length >= 8,
      hasNumber: /\d/.test(pwd),
      hasSpecialChar: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(pwd),
    };
    return requirements;
  };

  const passwordRequirements = validatePassword(password);

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
    } else {
      const reqs = validatePassword(password);
      if (!reqs.minLength) {
        newErrors.password = 'Password must be at least 8 characters';
      } else if (!reqs.hasNumber) {
        newErrors.password = 'Password must contain at least one number';
      } else if (!reqs.hasSpecialChar) {
        newErrors.password = 'Password must contain at least one special character';
      }
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
    <div className="form-container">
      <h1 style={{ marginBottom: '1.5rem', textAlign: 'center', color: 'var(--text-primary)' }}>Register</h1>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-label">
            Login:
          </label>
          <input
            type="text"
            value={login}
            onChange={(e) => setLogin(e.target.value)}
            required
            disabled={loading}
            className={`form-input ${errors.login ? 'error' : ''}`}
          />
          {errors.login && <div className="error-message">{errors.login}</div>}
        </div>
        
        <div className="form-group">
          <label className="form-label">
            Email:
          </label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            disabled={loading}
            className={`form-input ${errors.email ? 'error' : ''}`}
          />
          {errors.email && <div className="error-message">{errors.email}</div>}
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
          {password && (
            <div className="password-requirements">
              <div className="password-requirements-title">Password Requirements:</div>
              <div className={`password-requirement ${passwordRequirements.minLength ? 'valid' : 'invalid'}`}>
                At least 8 characters
              </div>
              <div className={`password-requirement ${passwordRequirements.hasNumber ? 'valid' : 'invalid'}`}>
                At least one number
              </div>
              <div className={`password-requirement ${passwordRequirements.hasSpecialChar ? 'valid' : 'invalid'}`}>
                At least one special character (!@#$%^&*...)
              </div>
            </div>
          )}
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
          {loading ? 'Registering...' : 'Register'}
        </button>
      </form>
      
      <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
        <Link to="/login" className="form-link">
          Already have an account? Login
        </Link>
      </div>
    </div>
  );
}

export default RegisterPage;
