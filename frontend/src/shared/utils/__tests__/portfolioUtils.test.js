/**
 * Unit tests for portfolio utility functions.
 */

import { computePortfolioBreakdown, formatUsdtValue, formatPercentage } from '../portfolioUtils';

describe('computePortfolioBreakdown', () => {
  it('should include USDT from cash balance', () => {
    const balances = [];
    const cashUsd = 1000;
    const prices = {};

    const result = computePortfolioBreakdown(balances, cashUsd, prices);

    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('USDT');
    expect(result[0].valueUsdt).toBe(1000);
    expect(result[0].percentage).toBe(100);
  });

  it('should include USDT even when other assets are 0', () => {
    const balances = [
      { asset: 'BTC', available: '0' },
      { asset: 'ETH', available: '0' },
    ];
    const cashUsd = 500;
    const prices = { BTC: 50000, ETH: 3000 };

    const result = computePortfolioBreakdown(balances, cashUsd, prices);

    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('USDT');
    expect(result[0].valueUsdt).toBe(500);
  });

  it('should calculate values for multiple assets', () => {
    const balances = [
      { asset: 'BTC', available: '0.1' },
      { asset: 'ETH', available: '2' },
    ];
    const cashUsd = 1000;
    const prices = { BTC: 50000, ETH: 3000 };

    const result = computePortfolioBreakdown(balances, cashUsd, prices);

    expect(result).toHaveLength(3);
    
    const usdt = result.find(r => r.name === 'USDT');
    expect(usdt.valueUsdt).toBe(1000);
    
    const btc = result.find(r => r.name === 'BTC');
    expect(btc.valueUsdt).toBe(5000); // 0.1 * 50000
    
    const eth = result.find(r => r.name === 'ETH');
    expect(eth.valueUsdt).toBe(6000); // 2 * 3000

    // Total: 1000 + 5000 + 6000 = 12000
    expect(usdt.percentage).toBeCloseTo(83.33, 1);
    expect(btc.percentage).toBeCloseTo(41.67, 1);
    expect(eth.percentage).toBeCloseTo(50.0, 1);
  });

  it('should skip assets without prices', () => {
    const balances = [
      { asset: 'BTC', available: '0.1' },
      { asset: 'UNKNOWN', available: '100' },
    ];
    const cashUsd = 1000;
    const prices = { BTC: 50000 };

    const result = computePortfolioBreakdown(balances, cashUsd, prices);

    expect(result).toHaveLength(2);
    expect(result.find(r => r.name === 'UNKNOWN')).toBeUndefined();
  });

  it('should skip zero balances', () => {
    const balances = [
      { asset: 'BTC', available: '0' },
      { asset: 'ETH', available: '2' },
    ];
    const cashUsd = 1000;
    const prices = { BTC: 50000, ETH: 3000 };

    const result = computePortfolioBreakdown(balances, cashUsd, prices);

    expect(result).toHaveLength(2);
    expect(result.find(r => r.name === 'BTC')).toBeUndefined();
  });

  it('should group small slices into Other when minPercentage is set', () => {
    const balances = [
      { asset: 'BTC', available: '0.1' }, // 5000 USDT
      { asset: 'ETH', available: '0.01' }, // 30 USDT (small)
      { asset: 'SOL', available: '0.01' }, // ~1 USDT (small)
    ];
    const cashUsd = 1000;
    const prices = { BTC: 50000, ETH: 3000, SOL: 100 };

    const result = computePortfolioBreakdown(balances, cashUsd, prices, 1);

    // Should have USDT, BTC, and Other (grouping ETH and SOL)
    expect(result.length).toBeGreaterThanOrEqual(2);
    
    const other = result.find(r => r.name === 'Other');
    if (other) {
      expect(other.isGrouped).toBe(true);
      expect(other.groupedAssets).toContain('ETH');
      expect(other.groupedAssets).toContain('SOL');
    }
  });

  it('should return empty array when total is 0', () => {
    const balances = [];
    const cashUsd = 0;
    const prices = {};

    const result = computePortfolioBreakdown(balances, cashUsd, prices);

    expect(result).toHaveLength(0);
  });

  it('should handle null/undefined inputs gracefully', () => {
    const result = computePortfolioBreakdown(null, null, null);

    expect(result).toHaveLength(0);
  });
});

describe('formatUsdtValue', () => {
  it('should format USDT values correctly', () => {
    expect(formatUsdtValue(1234.56)).toBe('1,234.56 USDT');
    expect(formatUsdtValue(0)).toBe('0.00 USDT');
    expect(formatUsdtValue(null)).toBe('0.00 USDT');
    expect(formatUsdtValue(undefined)).toBe('0.00 USDT');
  });
});

describe('formatPercentage', () => {
  it('should format percentages correctly', () => {
    expect(formatPercentage(50.123)).toBe('50.1%');
    expect(formatPercentage(0)).toBe('0.0%');
    expect(formatPercentage(null)).toBe('0%');
  });
});
