import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Dashboard service for portfolio data.
 */

/**
 * Get dashboard summary (total value, cash, PnL).
 * @returns {Promise<{totalValueUsd: number, availableCashUsd: number, unrealizedPnlUsd: number, unrealizedPnlPercent: number, realizedPnlUsd: number}>}
 */
export async function getSummary() {
  const response = await apiClient.get(ENDPOINTS.dashboard.summary);
  return response.data;
}

/**
 * Get user holdings with current prices and PnL.
 * @returns {Promise<Array<{assetId: string, symbol: string, name: string, quantity: number, avgBuyPriceUsd: number, currentPriceUsd: number, marketValueUsd: number, unrealizedPnlUsd: number, unrealizedPnlPercent: number}>>}
 */
export async function getHoldings() {
  const response = await apiClient.get(ENDPOINTS.dashboard.holdings);
  return response.data;
}

/**
 * Get recent transactions.
 * @param {number} limit - Maximum number of transactions to return
 * @returns {Promise<Array<{id: string, type: string, symbol: string, quantity: number, priceUsd: number, timestamp: string, status: string}>>}
 */
export async function getRecentTransactions(limit = 10) {
  const response = await apiClient.get(ENDPOINTS.dashboard.recentTransactions, {
    params: { limit }
  });
  return response.data;
}
