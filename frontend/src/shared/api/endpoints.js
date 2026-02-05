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
  dashboard: {
    summary: '/api/dashboard/summary',
    holdings: '/api/dashboard/holdings',
    recentTransactions: '/api/dashboard/recent-transactions',
  },
  prices: {
    snapshot: '/api/prices/snapshot',
    history: '/api/prices/history',
  },
  system: {
    health: '/api/system/health',
  },
};
