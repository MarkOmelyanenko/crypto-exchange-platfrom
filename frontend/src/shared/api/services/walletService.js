import apiClient from '../apiClient';
import { ENDPOINTS } from '../endpoints';

/**
 * Wallet service for cash balance, deposit, and balances operations.
 */

/**
 * Get current cash balance and 24h deposit limit info.
 * @returns {Promise<{ cashUsd: number, depositLimit24h: number, depositedLast24h: number, remainingLimit24h: number }>}
 */
export async function getCashBalance() {
  const response = await apiClient.get(ENDPOINTS.wallet.balance);
  return response.data;
}

/**
 * Deposit USD cash to account (via /api/wallet/cash-deposit).
 * @param {number} amountUsd - Amount in USD to deposit
 * @returns {Promise<{ cashUsd: number, depositLimit24h: number, depositedLast24h: number, remainingLimit24h: number }>}
 */
export async function cashDeposit(amountUsd) {
  const response = await apiClient.post(ENDPOINTS.wallet.cashDeposit, { amountUsd });
  return response.data;
}

/**
 * Deposit USDT to account (via /api/wallet/deposit).
 * @param {string|number} amount - Amount to deposit
 * @returns {Promise<{ cashUsd: number, depositLimit24h: number, depositedLast24h: number, remainingLimit24h: number }>}
 */
export async function deposit(amount) {
  const response = await apiClient.post(ENDPOINTS.wallet.deposit, { amount: String(amount) });
  return response.data;
}

/**
 * Get all wallet balances for the authenticated user.
 * @returns {Promise<Array<{ asset: string, available: string }>>}
 */
export async function getWalletBalances() {
  const response = await apiClient.get(ENDPOINTS.wallet.balances);
  return response.data;
}
