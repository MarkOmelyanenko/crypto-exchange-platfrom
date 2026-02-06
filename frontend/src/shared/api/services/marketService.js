import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Market/pairs service for trading pair data and prices.
 */

/**
 * Get list of active trading pairs.
 * @returns {Promise<Array<{ id: string, base: string, quote: string, symbol: string, enabled: boolean }>>}
 */
export async function getPairs() {
  const response = await apiClient.get(ENDPOINTS.markets.pairs);
  return response.data;
}

/**
 * Get current price for a trading pair.
 * @param {string} pairId - UUID of the trading pair
 * @returns {Promise<{ pairId: string, price: string, timestamp: string }>}
 */
export async function getPrice(pairId) {
  const response = await apiClient.get(ENDPOINTS.markets.price(pairId));
  return response.data;
}

/**
 * Get price history (klines) for a trading pair.
 * Works for any pair (USDT, BTC, ETH denominated).
 * @param {string} pairId - UUID of the trading pair
 * @param {string} range - Time range ('24h', '7d', '30d')
 * @returns {Promise<Array<{ timestamp: string, price: number }>>}
 */
export async function getPairHistory(pairId, range = '24h') {
  const response = await apiClient.get(ENDPOINTS.markets.history(pairId, range));
  return response.data;
}
