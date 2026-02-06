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
  stream: {
    prices: (symbols) => `/api/stream/prices?symbols=${encodeURIComponent(symbols.join(','))}`,
    status: '/api/stream/status',
  },
  wallet: {
    balance: '/api/wallet/balance',
    balances: '/api/wallet/balances',
    cashDeposit: '/api/wallet/cash-deposit',
    deposit: '/api/wallet/deposit',
  },
  markets: {
    pairs: '/api/markets/pairs',
    price: (pairId) => `/api/markets/price?pairId=${pairId}`,
    history: (pairId, range) => `/api/markets/history?pairId=${pairId}&range=${range}`,
  },
  orders: {
    market: '/api/orders/market',
    trades: '/api/orders/trades',
  },
  system: {
    health: '/api/system/health',
  },
};
