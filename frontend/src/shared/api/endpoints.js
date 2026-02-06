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
    bySymbol: (symbol) => `/api/assets/${encodeURIComponent(symbol)}`,
    myPosition: (symbol) => `/api/assets/${encodeURIComponent(symbol)}/my-position`,
  },
  transactions: {
    list: '/api/transactions',
    create: '/api/transactions',
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
