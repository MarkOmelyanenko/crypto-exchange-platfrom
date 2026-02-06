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

/**
 * Update user profile (login and email).
 * @param {{ login: string, email: string }} payload - Profile update data
 * @returns {Promise<import('../contracts').UserMeResponse>} Updated user data
 */
export async function updateProfile(payload) {
  const response = await apiClient.put(ENDPOINTS.users.updateProfile, payload);
  return response.data;
}

/**
 * Change user password.
 * @param {{ currentPassword: string, newPassword: string }} payload - Password change data
 * @returns {Promise<{ message: string }>} Success response
 */
export async function changePassword(payload) {
  const response = await apiClient.put(ENDPOINTS.users.changePassword, payload);
  return response.data;
}
