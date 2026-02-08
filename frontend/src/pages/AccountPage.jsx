import { useState, useEffect } from 'react';
import { useAuth } from '../shared/context/AuthContext';
import { updateProfile, changePassword } from '../shared/api/services/userService';

function AccountPage() {
  const { user, loadUser } = useAuth();
  const [profileLoading, setProfileLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);
  
  // Profile form state
  const [login, setLogin] = useState('');
  const [email, setEmail] = useState('');
  const [profileErrors, setProfileErrors] = useState({});
  const [profileError, setProfileError] = useState('');
  const [profileSuccess, setProfileSuccess] = useState('');

  // Password form state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordErrors, setPasswordErrors] = useState({});
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');

  // Initialize form with user data
  useEffect(() => {
    if (user) {
      setLogin(user.login || '');
      setEmail(user.email || '');
    }
  }, [user]);

  const validateProfile = () => {
    const newErrors = {};
    
    if (!login.trim()) {
      newErrors.login = 'Login is required';
    } else if (login.length < 3 || login.length > 50) {
      newErrors.login = 'Login must be between 3 and 50 characters';
    }
    
    if (!email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Email must be valid';
    }
    
    setProfileErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const validatePasswordStrength = (pwd) => {
    const requirements = {
      minLength: pwd.length >= 8,
      hasNumber: /\d/.test(pwd),
      hasSpecialChar: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(pwd),
    };
    return requirements;
  };

  const newPasswordRequirements = validatePasswordStrength(newPassword);

  const validatePassword = () => {
    const newErrors = {};
    
    if (!currentPassword) {
      newErrors.currentPassword = 'Current password is required';
    }
    
    if (!newPassword) {
      newErrors.newPassword = 'New password is required';
    } else {
      const reqs = validatePasswordStrength(newPassword);
      if (!reqs.minLength) {
        newErrors.newPassword = 'New password must be at least 8 characters';
      } else if (!reqs.hasNumber) {
        newErrors.newPassword = 'New password must contain at least one number';
      } else if (!reqs.hasSpecialChar) {
        newErrors.newPassword = 'New password must contain at least one special character';
      }
    }
    
    if (!confirmPassword) {
      newErrors.confirmPassword = 'Please confirm your new password';
    } else if (newPassword !== confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }
    
    setPasswordErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleProfileSubmit = async (e) => {
    e.preventDefault();
    setProfileError('');
    setProfileSuccess('');
    setProfileErrors({});
    
    if (!validateProfile()) {
      return;
    }
    
    setProfileLoading(true);

    try {
      await updateProfile({ login, email });
      setProfileSuccess('Profile updated successfully!');
      // Reload user data to reflect changes
      await loadUser();
      // Clear success message after 3 seconds
      setTimeout(() => setProfileSuccess(''), 3000);
    } catch (err) {
      const errorMessage = err.response?.data?.errors || err.response?.data?.message || err.message || 'Failed to update profile. Please try again.';
      if (err.response?.data?.details) {
        // Handle validation errors from backend
        const details = err.response.data.details;
        const fieldErrors = {};
        Object.keys(details).forEach(key => {
          fieldErrors[key] = details[key];
        });
        setProfileErrors(fieldErrors);
      } else {
        setProfileError(errorMessage);
      }
    } finally {
      setProfileLoading(false);
    }
  };

  const handlePasswordSubmit = async (e) => {
    e.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');
    setPasswordErrors({});
    
    if (!validatePassword()) {
      return;
    }
    
    setPasswordLoading(true);

    try {
      await changePassword({ currentPassword, newPassword });
      setPasswordSuccess('Password changed successfully!');
      // Clear form
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      // Clear success message after 3 seconds
      setTimeout(() => setPasswordSuccess(''), 3000);
    } catch (err) {
      const errorMessage = err.response?.data?.errors || err.response?.data?.message || err.message || 'Failed to change password. Please try again.';
      if (err.response?.data?.details) {
        // Handle validation errors from backend
        const details = err.response.data.details;
        const fieldErrors = {};
        Object.keys(details).forEach(key => {
          fieldErrors[key] = details[key];
        });
        setPasswordErrors(fieldErrors);
      } else {
        setPasswordError(errorMessage);
      }
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div className="account-page-container" style={{ maxWidth: '700px', margin: '2rem auto', padding: '0 1rem' }}>
      <h1 className="resp-page-title" style={{ marginBottom: '2rem', color: 'var(--text-primary)' }}>Account Settings</h1>

      {/* Profile Section */}
      <div className="account-section responsive-card" style={{ marginBottom: '2.5rem' }}>
        <h2 style={{ marginTop: 0, marginBottom: '1.5rem', color: 'var(--text-primary)' }}>Profile Information</h2>
        <form onSubmit={handleProfileSubmit}>
          <div className="form-group">
            <label className="form-label">
              Login:
            </label>
            <input
              type="text"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              required
              disabled={profileLoading}
              className={`form-input ${profileErrors.login ? 'error' : ''}`}
            />
            {profileErrors.login && (
              <div className="error-message">
                {profileErrors.login}
              </div>
            )}
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
              disabled={profileLoading}
              className={`form-input ${profileErrors.email ? 'error' : ''}`}
            />
            {profileErrors.email && (
              <div className="error-message">
                {profileErrors.email}
              </div>
            )}
          </div>

          {profileError && (
            <div className="error-banner">
              {profileError}
            </div>
          )}

          {profileSuccess && (
            <div className="success-message">
              {profileSuccess}
            </div>
          )}

          <button
            type="submit"
            disabled={profileLoading}
            className="btn btn-primary"
            style={{ width: 'auto', padding: '0.75rem 2rem' }}
          >
            {profileLoading ? 'Saving...' : 'Save Changes'}
          </button>
        </form>
      </div>

      {/* Password Section */}
      <div className="account-section responsive-card">
        <h2 style={{ marginTop: 0, marginBottom: '1.5rem', color: 'var(--text-primary)' }}>Change Password</h2>
        <form onSubmit={handlePasswordSubmit}>
          <div className="form-group">
            <label className="form-label">
              Current Password:
            </label>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
              disabled={passwordLoading}
              className={`form-input ${passwordErrors.currentPassword ? 'error' : ''}`}
            />
            {passwordErrors.currentPassword && (
              <div className="error-message">
                {passwordErrors.currentPassword}
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label">
              New Password:
            </label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              disabled={passwordLoading}
              className={`form-input ${passwordErrors.newPassword ? 'error' : ''}`}
            />
            {passwordErrors.newPassword && (
              <div className="error-message">
                {passwordErrors.newPassword}
              </div>
            )}
            {newPassword && (
              <div className="password-requirements">
                <div className="password-requirements-title">Password Requirements:</div>
                <div className={`password-requirement ${newPasswordRequirements.minLength ? 'valid' : 'invalid'}`}>
                  At least 8 characters
                </div>
                <div className={`password-requirement ${newPasswordRequirements.hasNumber ? 'valid' : 'invalid'}`}>
                  At least one number
                </div>
                <div className={`password-requirement ${newPasswordRequirements.hasSpecialChar ? 'valid' : 'invalid'}`}>
                  At least one special character (!@#$%^&*...)
                </div>
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label">
              Confirm New Password:
            </label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              disabled={passwordLoading}
              className={`form-input ${passwordErrors.confirmPassword ? 'error' : ''}`}
            />
            {passwordErrors.confirmPassword && (
              <div className="error-message">
                {passwordErrors.confirmPassword}
              </div>
            )}
            {confirmPassword && newPassword && confirmPassword === newPassword && (
              <div className="success-message" style={{ marginTop: '0.5rem', padding: '0.5rem' }}>
                Passwords match
              </div>
            )}
          </div>

          {passwordError && (
            <div className="error-banner">
              {passwordError}
            </div>
          )}

          {passwordSuccess && (
            <div className="success-message" style={{ margin: '15px 15px 15px 0'}}>
              {passwordSuccess}
            </div>
          )}

          <button
            type="submit"
            disabled={passwordLoading}
            className="btn btn-success"
            style={{ width: 'auto', padding: '0.75rem 2rem' }}
          >
            {passwordLoading ? 'Changing...' : 'Change Password'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default AccountPage;
