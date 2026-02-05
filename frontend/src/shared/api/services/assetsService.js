import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Assets service for asset-related operations.
 */

/**
 * Get list of all assets.
 * @returns {Promise<import('../contracts').AssetDto[]>} List of assets
 */
export async function list() {
  const response = await apiClient.get(ENDPOINTS.assets.list);
  return response.data;
}

/**
 * Get asset by ID.
 * @param {string} id - Asset ID (UUID)
 * @returns {Promise<import('../contracts').AssetDto>} Asset data
 */
export async function getById(id) {
  const response = await apiClient.get(ENDPOINTS.assets.byId(id));
  return response.data;
}
