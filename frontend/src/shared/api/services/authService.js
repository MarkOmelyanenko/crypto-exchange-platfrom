import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Authentication service for login and registration.
 */

/**
 * Login with email and password.
 * @param {import('../contracts').AuthLoginRequest} payload - Login credentials
 * @returns {Promise<import('../contracts').AuthLoginResponse>} Login response with token
 */
export async function login(payload) {
  const response = await apiClient.post(ENDPOINTS.auth.login, payload);
  const data = response.data;
  
  // Store token in localStorage if present
  // Handle different possible token field names from backend
  const token = data.token || data.accessToken || data.jwt;
  if (token) {
    localStorage.setItem('auth_token', token);
  }
  
  // Store userId if present
  if (data.userId) {
    localStorage.setItem('user_id', data.userId);
  }
  
  return data;
}

/**
 * Register a new user account.
 * @param {import('../contracts').AuthRegisterRequest} payload - Registration data
 * @returns {Promise<import('../contracts').AuthRegisterResponse>} Registration response
 */
export async function register(payload) {
  const response = await apiClient.post(ENDPOINTS.auth.register, payload);
  const data = response.data;
  
  // Store token in localStorage if backend returns it on registration
  const token = data.token || data.accessToken || data.jwt;
  if (token) {
    localStorage.setItem('auth_token', token);
  }
  
  // Store userId if present
  if (data.userId) {
    localStorage.setItem('user_id', data.userId);
  }
  
  return data;
}
