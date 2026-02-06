import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Order service for placing market orders and listing trades.
 */

/**
 * Execute a market order.
 * @param {{ pairId: string, side: 'BUY'|'SELL', quoteAmount?: string, baseAmount?: string }} params
 * @returns {Promise<Object>} Trade DTO
 */
export async function placeMarketOrder({ pairId, side, quoteAmount, baseAmount }) {
  const body = { pairId, side };
  if (quoteAmount) body.quoteAmount = String(quoteAmount);
  if (baseAmount) body.baseAmount = String(baseAmount);
  const response = await apiClient.post(ENDPOINTS.orders.market, body);
  return response.data;
}

/**
 * Get recent trades for the authenticated user.
 * @param {number} [page=0]
 * @param {number} [size=20]
 * @returns {Promise<{ items: Array, total: number, page: number, size: number, totalPages: number }>}
 */
export async function listTrades(page = 0, size = 20) {
  const response = await apiClient.get(ENDPOINTS.orders.trades, { params: { page, size } });
  return response.data;
}
