/**
 * Portfolio utility functions for computing portfolio breakdown and allocation.
 */

/**
 * Compute portfolio breakdown in USDT value.
 * 
 * @param {Array<{asset: string, available: string}>} balances - Wallet balances
 * @param {number|null} cashUsd - USDT cash balance (from getCashBalance)
 * @param {Object<string, number>} prices - Map of symbol -> priceUsd (from getSnapshot)
 * @param {Object<string, string>} assetNames - Map of symbol -> asset name (optional)
 * @param {number} [minPercentage=1] - Minimum percentage to show individually (others grouped as "Other")
 * @returns {Array<{name: string, valueUsdt: number, percentage: number, quantity?: number, assetName?: string}>}
 */
export function computePortfolioBreakdown(balances = [], cashUsd = null, prices = {}, assetNames = {}, minPercentage = 1) {
  const items = [];

  // Always include USDT if available
  const usdtValue = cashUsd != null ? Number(cashUsd) : 0;
  if (usdtValue > 0) {
    items.push({
      name: 'USDT',
      valueUsdt: usdtValue,
      quantity: usdtValue,
      assetName: assetNames['USDT'] || 'Tether',
    });
  }

  // Process other assets
  for (const balance of balances) {
    const asset = balance.asset;
    const available = Number(balance.available) || 0;

    // Skip USDT (already handled) and zero balances
    if (asset === 'USDT' || available <= 0) {
      continue;
    }

    // Get price for this asset
    const price = prices[asset];
    if (price == null || isNaN(Number(price))) {
      // Skip assets without price (log warning in console)
      console.warn(`No price available for ${asset}, skipping from portfolio breakdown`);
      continue;
    }

    const valueUsdt = available * Number(price);
    if (valueUsdt > 0) {
      items.push({
        name: asset,
        valueUsdt,
        quantity: available,
        assetName: assetNames[asset],
      });
    }
  }

  // Calculate total and percentages
  const totalUsdt = items.reduce((sum, item) => sum + item.valueUsdt, 0);
  
  if (totalUsdt === 0) {
    return [];
  }

  // Add percentages
  const itemsWithPct = items.map(item => ({
    ...item,
    percentage: (item.valueUsdt / totalUsdt) * 100,
  }));

  // Group small slices into "Other" if needed
  if (minPercentage > 0) {
    const mainItems = [];
    const otherItems = [];
    let otherValue = 0;

    for (const item of itemsWithPct) {
      if (item.percentage >= minPercentage) {
        mainItems.push(item);
      } else {
        otherItems.push(item);
        otherValue += item.valueUsdt;
      }
    }

    if (otherItems.length > 0 && otherValue > 0) {
      mainItems.push({
        name: 'Other',
        valueUsdt: otherValue,
        percentage: (otherValue / totalUsdt) * 100,
        isGrouped: true,
        groupedAssets: otherItems.map(i => i.name),
      });
    }

    return mainItems;
  }

  return itemsWithPct;
}

/**
 * Format value in USDT for display.
 * @param {number} value - Value in USDT
 * @returns {string}
 */
export function formatUsdtValue(value) {
  if (value == null || isNaN(Number(value))) return '0.00 USDT';
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value)) + ' USDT';
}

/**
 * Format percentage for display.
 * @param {number} percentage - Percentage value
 * @returns {string}
 */
export function formatPercentage(percentage) {
  if (percentage == null || isNaN(Number(percentage))) return '0%';
  return `${Number(percentage).toFixed(1)}%`;
}
