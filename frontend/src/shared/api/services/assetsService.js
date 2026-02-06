import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Assets service for asset-related operations.
 */

/**
 * Get paginated list of assets with prices.
 * @param {{ q?: string, sort?: string, dir?: string, page?: number, size?: number }} params
 * @returns {Promise<{ items: Array, total: number, page: number, size: number, totalPages: number }>}
 */
export async function list(params = {}) {
  const response = await apiClient.get(ENDPOINTS.assets.list, { params });
  return response.data;
}

/**
 * Get asset details by symbol (case-insensitive).
 * @param {string} symbol - Asset symbol (e.g., 'BTC')
 * @returns {Promise<Object>} Asset detail data with price info
 */
export async function getBySymbol(symbol) {
  const response = await apiClient.get(ENDPOINTS.assets.bySymbol(symbol));
  return response.data;
}

/**
 * Get current user's position for an asset.
 * @param {string} symbol - Asset symbol (e.g., 'BTC')
 * @returns {Promise<Object>} Position data (quantity, marketValue, etc.)
 */
export async function getMyPosition(symbol) {
  const response = await apiClient.get(ENDPOINTS.assets.myPosition(symbol));
  return response.data;
}
