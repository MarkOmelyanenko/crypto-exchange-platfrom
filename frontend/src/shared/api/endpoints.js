/**
 * Single source of truth for backend API endpoints.
 * All endpoint paths should match the backend controller mappings.
 */
export const ENDPOINTS = {
  auth: {
    login: '/api/auth/login',
    register: '/api/auth/register',
  },
  users: {
    me: '/api/users/me',
  },
  assets: {
    list: '/api/assets',
    byId: (id) => `/api/assets/${id}`,
  },
  transactions: {
    list: '/api/transactions',
    byId: (id) => `/api/transactions/${id}`,
  },
};
