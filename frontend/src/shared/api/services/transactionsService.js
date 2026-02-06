import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Transactions service for creating and listing transactions.
 */

/**
 * Create a new BUY or SELL transaction.
 * @param {import('../contracts').CreateTransactionRequest} data
 * @returns {Promise<import('../contracts').TransactionDto>}
 */
export async function create(data) {
  const response = await apiClient.post(ENDPOINTS.transactions.create, data);
  return response.data;
}

/**
 * Get paginated list of transactions with optional filters.
 * @param {Object} params
 * @param {string} [params.symbol] - Filter by asset symbol
 * @param {string} [params.side] - Filter by BUY or SELL
 * @param {string} [params.from] - Filter from date (ISO)
 * @param {string} [params.to] - Filter to date (ISO)
 * @param {string} [params.sort] - Sort field: createdAt or totalUsd
 * @param {string} [params.dir] - Sort direction: asc or desc
 * @param {number} [params.page] - Page number (0-based)
 * @param {number} [params.size] - Page size
 * @returns {Promise<import('../contracts').TransactionPagedResponse>}
 */
export async function list(params = {}) {
  const response = await apiClient.get(ENDPOINTS.transactions.list, { params });
  return response.data;
}

/**
 * Get transaction by ID.
 * @param {string} id - Transaction ID (UUID)
 * @returns {Promise<import('../contracts').TransactionDto>}
 */
export async function getById(id) {
  const response = await apiClient.get(ENDPOINTS.transactions.byId(id));
  return response.data;
}
