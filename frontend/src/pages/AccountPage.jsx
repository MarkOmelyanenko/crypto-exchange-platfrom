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

  const validatePassword = () => {
    const newErrors = {};
    
    if (!currentPassword) {
      newErrors.currentPassword = 'Current password is required';
    }
    
    if (!newPassword) {
      newErrors.newPassword = 'New password is required';
    } else if (newPassword.length < 8) {
      newErrors.newPassword = 'New password must be at least 8 characters';
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
    <div style={{ maxWidth: '600px', margin: '50px auto', padding: '20px' }}>
      <h1>Account Settings</h1>

      {/* Profile Section */}
      <div style={{ marginBottom: '40px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px' }}>
        <h2 style={{ marginTop: 0 }}>Profile Information</h2>
        <form onSubmit={handleProfileSubmit}>
          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>
              Login:
            </label>
            <input
              type="text"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              required
              disabled={profileLoading}
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
            {profileErrors.login && (
              <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>
                {profileErrors.login}
              </div>
            )}
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>
              Email:
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={profileLoading}
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
            {profileErrors.email && (
              <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>
                {profileErrors.email}
              </div>
            )}
          </div>

          {profileError && (
            <div style={{ color: 'red', marginBottom: '15px', padding: '10px', backgroundColor: '#ffe6e6', borderRadius: '4px' }}>
              {profileError}
            </div>
          )}

          {profileSuccess && (
            <div style={{ color: 'green', marginBottom: '15px', padding: '10px', backgroundColor: '#e6ffe6', borderRadius: '4px' }}>
              {profileSuccess}
            </div>
          )}

          <button
            type="submit"
            disabled={profileLoading}
            style={{
              padding: '10px 20px',
              backgroundColor: '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: profileLoading ? 'not-allowed' : 'pointer',
              opacity: profileLoading ? 0.6 : 1
            }}
          >
            {profileLoading ? 'Saving...' : 'Save Changes'}
          </button>
        </form>
      </div>

      {/* Password Section */}
      <div style={{ padding: '20px', border: '1px solid #ddd', borderRadius: '8px' }}>
        <h2 style={{ marginTop: 0 }}>Change Password</h2>
        <form onSubmit={handlePasswordSubmit}>
          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>
              Current Password:
            </label>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
              disabled={passwordLoading}
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
            {passwordErrors.currentPassword && (
              <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>
                {passwordErrors.currentPassword}
              </div>
            )}
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>
              New Password:
            </label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              disabled={passwordLoading}
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
            {passwordErrors.newPassword && (
              <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>
                {passwordErrors.newPassword}
              </div>
            )}
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>
              Confirm New Password:
            </label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              disabled={passwordLoading}
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
            {passwordErrors.confirmPassword && (
              <div style={{ color: 'red', fontSize: '12px', marginTop: '5px' }}>
                {passwordErrors.confirmPassword}
              </div>
            )}
          </div>

          {passwordError && (
            <div style={{ color: 'red', marginBottom: '15px', padding: '10px', backgroundColor: '#ffe6e6', borderRadius: '4px' }}>
              {passwordError}
            </div>
          )}

          {passwordSuccess && (
            <div style={{ color: 'green', marginBottom: '15px', padding: '10px', backgroundColor: '#e6ffe6', borderRadius: '4px' }}>
              {passwordSuccess}
            </div>
          )}

          <button
            type="submit"
            disabled={passwordLoading}
            style={{
              padding: '10px 20px',
              backgroundColor: '#28a745',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: passwordLoading ? 'not-allowed' : 'pointer',
              opacity: passwordLoading ? 0.6 : 1
            }}
          >
            {passwordLoading ? 'Changing...' : 'Change Password'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default AccountPage;
