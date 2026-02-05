import { useState, useEffect } from 'react';
import { list } from '../shared/api/services/assetsService';

function AssetsPage() {
  const [assets, setAssets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchAssets = async () => {
      try {
        const assetsData = await list();
        setAssets(Array.isArray(assetsData) ? assetsData : []);
      } catch (err) {
        setError(err.response?.data?.message || err.message || 'Failed to load assets.');
      } finally {
        setLoading(false);
      }
    };

    fetchAssets();
  }, []);

  if (loading) {
    return <div>Loading...</div>;
  }

  if (error) {
    return (
      <div>
        <h1>Assets</h1>
        <div style={{ color: 'red' }}>{error}</div>
      </div>
    );
  }

  return (
    <div>
      <h1>Assets</h1>
      {assets.length === 0 ? (
        <p>No assets found.</p>
      ) : (
        <ul>
          {assets.map((asset) => (
            <li key={asset.id}>
              ID: {asset.id} - {asset.symbol || asset.name || 'Unknown'} ({asset.name || asset.symbol || 'N/A'})
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default AssetsPage;
