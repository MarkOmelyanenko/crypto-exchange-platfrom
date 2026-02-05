import { useState, useEffect } from 'react';
import { me } from '../shared/api/services/userService';

function DashboardPage() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const userData = await me();
        setUser(userData);
      } catch (err) {
        setError(err.response?.data?.message || err.message || 'Failed to load user data.');
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, []);

  if (loading) {
    return <div>Loading...</div>;
  }

  if (error) {
    return (
      <div>
        <h1>Dashboard</h1>
        <div style={{ color: 'red' }}>{error}</div>
      </div>
    );
  }

  return (
    <div>
      <h1>Dashboard</h1>
      {user && (
        <div>
          <p>Hello, {user.email || user.username || 'User'}!</p>
          {user.id && <p>User ID: {user.id}</p>}
        </div>
      )}
    </div>
  );
}

export default DashboardPage;
