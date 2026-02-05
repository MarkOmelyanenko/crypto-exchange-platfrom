import { useAuth } from '../shared/context/AuthContext';

function DashboardPage() {
  const { user, loading } = useAuth();

  if (loading) {
    return <div style={{ padding: '20px' }}>Loading...</div>;
  }

  return (
    <div style={{ padding: '20px' }}>
      <h1>Dashboard</h1>
      {user && (
        <div style={{ marginTop: '20px' }}>
          <p>Welcome, <strong>{user.login || user.email}</strong>!</p>
          <p>Email: {user.email}</p>
        </div>
      )}
    </div>
  );
}

export default DashboardPage;
