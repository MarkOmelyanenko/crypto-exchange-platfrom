import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * User service for user-related operations.
 */

/**
 * Get current authenticated user information.
 * @returns {Promise<import('../contracts').UserMeResponse>} Current user data
 */
export async function me() {
  const response = await apiClient.get(ENDPOINTS.users.me);
  return response.data;
}
