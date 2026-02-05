import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Transactions service for transaction-related operations.
 */

/**
 * Get list of all transactions for the authenticated user.
 * @returns {Promise<import('../contracts').TransactionDto[]>} List of transactions
 */
export async function list() {
  const response = await apiClient.get(ENDPOINTS.transactions.list);
  return response.data;
}

/**
 * Get transaction by ID.
 * @param {string} id - Transaction ID (UUID)
 * @returns {Promise<import('../contracts').TransactionDto>} Transaction data
 */
export async function getById(id) {
  const response = await apiClient.get(ENDPOINTS.transactions.byId(id));
  return response.data;
}
