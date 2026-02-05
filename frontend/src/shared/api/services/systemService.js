import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * System service for health checks.
 */

/**
 * Get system health status.
 * @returns {Promise<{status: {api: string, db: string, kafka: string}}>}
 */
export async function getHealth() {
  const response = await apiClient.get(ENDPOINTS.system.health);
  return response.data;
}
