import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Authentication service for login and registration.
 */

/**
 * Login with login or email and password.
 * @param {{ loginOrEmail: string, password: string }} payload - Login credentials
 * @returns {Promise<{ accessToken: string, tokenType: string }>} Login response with token
 */
export async function login(payload) {
  const response = await apiClient.post(ENDPOINTS.auth.login, payload);
  const data = response.data;
  
  // Store token in localStorage
  if (data.accessToken) {
    localStorage.setItem('auth_token', data.accessToken);
  }
  
  return data;
}

/**
 * Register a new user account.
 * @param {{ login: string, email: string, password: string }} payload - Registration data
 * @returns {Promise<{ accessToken: string, tokenType: string }>} Registration response
 */
export async function register(payload) {
  const response = await apiClient.post(ENDPOINTS.auth.register, payload);
  const data = response.data;
  
  // Store token in localStorage
  if (data.accessToken) {
    localStorage.setItem('auth_token', data.accessToken);
  }
  
  return data;
}

/**
 * Logout - clears authentication token
 */
export function logout() {
  localStorage.removeItem('auth_token');
}
