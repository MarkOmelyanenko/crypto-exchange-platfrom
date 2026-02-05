import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Price service for market data.
 */

/**
 * Get price snapshot for multiple symbols.
 * @param {string[]} symbols - Array of asset symbols (e.g., ['BTC', 'ETH', 'SOL'])
 * @returns {Promise<Array<{symbol: string, priceUsd: number, timestamp: string}>>}
 */
export async function getSnapshot(symbols) {
  const symbolsParam = Array.isArray(symbols) ? symbols.join(',') : symbols;
  const response = await apiClient.get(ENDPOINTS.prices.snapshot, {
    params: { symbols: symbolsParam }
  });
  return response.data;
}

/**
 * Get price history for a symbol.
 * @param {string} symbol - Asset symbol (e.g., 'BTC')
 * @param {string} range - Time range ('24h', '7d', '30d')
 * @returns {Promise<Array<{timestamp: string, priceUsd: number}>>}
 */
export async function getHistory(symbol, range = '24h') {
  const response = await apiClient.get(ENDPOINTS.prices.history, {
    params: { symbol, range }
  });
  return response.data;
}
