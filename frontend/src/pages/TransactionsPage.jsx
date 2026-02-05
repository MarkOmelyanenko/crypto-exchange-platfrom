import { useState, useEffect } from 'react';
import { list } from '../shared/api/services/transactionsService';

function TransactionsPage() {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchTransactions = async () => {
      try {
        const transactionsData = await list();
        setTransactions(Array.isArray(transactionsData) ? transactionsData : []);
      } catch (err) {
        setError(err.response?.data?.message || err.message || 'Failed to load transactions.');
      } finally {
        setLoading(false);
      }
    };

    fetchTransactions();
  }, []);

  if (loading) {
    return <div>Loading...</div>;
  }

  if (error) {
    return (
      <div>
        <h1>Transactions</h1>
        <div style={{ color: 'red' }}>{error}</div>
      </div>
    );
  }

  return (
    <div>
      <h1>Transactions</h1>
      {transactions.length === 0 ? (
        <p>No transactions found.</p>
      ) : (
        <ul>
          {transactions.map((transaction) => (
            <li key={transaction.id}>
              ID: {transaction.id} - Type: {transaction.type || 'N/A'} - Amount: {transaction.amount || 'N/A'}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default TransactionsPage;
