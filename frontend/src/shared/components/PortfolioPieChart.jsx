import { useMemo } from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { computePortfolioBreakdown, formatUsdtValue, formatPercentage } from '../utils/portfolioUtils';
import { LastUpdatedIndicator } from './LastUpdatedIndicator';
import CryptoIcon from './CryptoIcon';

/**
 * Portfolio allocation pie/donut chart component with custom legend.
 * 
 * @param {Object} props
 * @param {Array<{asset: string, available: string}>} props.balances - Wallet balances
 * @param {number|null} props.cashUsd - USDT cash balance
 * @param {Object<string, number>} props.prices - Map of symbol -> priceUsd
 * @param {Object<string, string>} props.assetNames - Map of symbol -> asset name
 * @param {Date|number|null} props.lastUpdatedAt - Timestamp of last update
 * @param {boolean} props.loading - Loading state
 * @param {string|null} props.error - Error message
 * @param {boolean} props.showLegendAsList - Show legend as vertical list instead of grid (deprecated, kept for compatibility)
 */
export function PortfolioPieChart({ 
  balances = [], 
  cashUsd = null, 
  prices = {}, 
  assetNames = {},
  lastUpdatedAt = null,
  loading = false, 
  error = null, 
  showLegendAsList = false 
}) {
  const breakdown = useMemo(() => {
    const result = computePortfolioBreakdown(balances, cashUsd, prices, assetNames, 1);
    // Filter out "Other" category
    return result.filter(item => item.name !== 'Other');
  }, [balances, cashUsd, prices, assetNames]);

  const totalUsdt = useMemo(() => {
    return breakdown.reduce((sum, item) => sum + item.valueUsdt, 0);
  }, [breakdown]);

  // Color palette for chart slices
  const COLORS = [
    '#3b82f6', // blue
    '#10b981', // green
    '#f59e0b', // amber
    '#8b5cf6', // purple
    '#ef4444', // red
    '#06b6d4', // cyan
    '#f97316', // orange
    '#ec4899', // pink
    '#84cc16', // lime
    '#6366f1', // indigo
  ];

  // Prepare chart data with full breakdown info
  const chartData = breakdown.map((item, idx) => ({
    name: item.name,
    value: item.valueUsdt,
    percentage: item.percentage,
    quantity: item.quantity,
    assetName: item.assetName,
    color: COLORS[idx % COLORS.length],
  }));

  // Sort legend items by value (descending) for better visual hierarchy
  const sortedLegendData = useMemo(() => {
    return [...chartData].sort((a, b) => b.value - a.value);
  }, [chartData]);

  // Format quantity for display
  const formatQuantity = (qty, symbol) => {
    if (qty == null || isNaN(Number(qty))) return '—';
    const n = Number(qty);
    if (n === 0) return '0';
    // For USDT, show 2 decimals; for others, show up to 8 decimals, strip trailing zeros
    const decimals = symbol === 'USDT' ? 2 : 8;
    return n.toFixed(decimals).replace(/\.?0+$/, '');
  };

  // Custom tooltip
  const CustomTooltip = ({ active, payload }) => {
    if (active && payload && payload.length) {
      const data = payload[0];
      // Find the corresponding chart data item
      const chartItem = chartData.find(item => item.name === data.name);
      if (!chartItem) return null;

      return (
        <div style={{
          backgroundColor: '#fff',
          padding: '8px 12px',
          border: '1px solid #e5e7eb',
          borderRadius: 6,
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#111827', marginBottom: 4 }}>
            {data.name}
          </div>
          <div style={{ fontSize: 12, color: '#6b7280' }}>
            Value: {formatUsdtValue(data.value)}
          </div>
          <div style={{ fontSize: 12, color: '#6b7280' }}>
            {formatPercentage(chartItem.percentage)}
          </div>
        </div>
      );
    }
    return null;
  };

  // Custom Legend Component
  const CustomLegend = () => {
    if (sortedLegendData.length === 0) return null;

      return (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
        gap: 10,
          }}>
        {sortedLegendData.map((item, index) => (
              <div
            key={item.name}
                style={{
                  display: 'flex',
              alignItems: 'flex-start',
                  gap: 12,
              padding: '10px 12px',
              borderRadius: 6,
              backgroundColor: index % 2 === 0 ? '#fafafa' : 'transparent',
              transition: 'background-color 0.15s',
              cursor: 'default',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = '#f3f4f6';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = index % 2 === 0 ? '#fafafa' : 'transparent';
            }}
          >
            {/* Crypto icon */}
            <div style={{ flexShrink: 0, marginTop: 2 }}>
              <CryptoIcon symbol={item.name} size={20} />
            </div>
            
            {/* Content */}
              <div style={{ flex: 1, minWidth: 0 }}>
              {/* Ticker and Asset Name */}
                <div style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  gap: 8,
                marginBottom: 4,
                  flexWrap: 'wrap',
                }}>
                  <span style={{
                  fontSize: 15,
                  fontWeight: 700,
                    color: '#111827',
                  letterSpacing: '0.02em',
                  }}>
                  {item.name}
                  </span>
                {item.assetName && item.assetName !== item.name && (
                  <span style={{
                    fontSize: 12,
                    color: '#6b7280',
                    fontWeight: 400,
                  }}>
                    {item.assetName}
                  </span>
                )}
                <span style={{
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#3b82f6',
                  marginLeft: 'auto',
                }}>
                  {formatPercentage(item.percentage)}
                </span>
              </div>

              {/* Value in USDT */}
              <div style={{
                fontSize: 14,
                fontWeight: 600,
                color: '#111827',
                marginBottom: 2,
                fontFamily: 'monospace',
              }}>
                {formatUsdtValue(item.value)}
                </div>

              {/* Quantity (smaller text) */}
                <div style={{
                fontSize: 11,
                  color: '#9ca3af',
                  fontFamily: 'monospace',
                }}>
                {formatQuantity(item.quantity, item.name)} {item.name}
                </div>
              </div>
            </div>
          ))}
      </div>
    );
  };

  if (loading) {
    return (
      <div style={{
        padding: '40px 20px',
        textAlign: 'center',
        backgroundColor: '#f9fafb',
        borderRadius: 8,
        border: '1px dashed #d1d5db',
      }}>
        <div style={{
          width: 40,
          height: 40,
          border: '3px solid #e5e7eb',
          borderTopColor: '#3b82f6',
          borderRadius: '50%',
          margin: '0 auto 16px',
          animation: 'spin 1s linear infinite',
        }}>
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        </div>
        <div style={{ fontSize: 14, color: '#6b7280' }}>Loading portfolio data...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{
        padding: '20px',
        backgroundColor: '#fef2f2',
        borderRadius: 8,
        border: '1px solid #fecaca',
        color: '#dc2626',
        fontSize: 14,
        textAlign: 'center',
      }}>
        {error}
      </div>
    );
  }

  if (totalUsdt === 0 || chartData.length === 0) {
    return (
      <div style={{
        padding: '40px 20px',
        textAlign: 'center',
        backgroundColor: '#f9fafb',
        borderRadius: 8,
        border: '1px dashed #d1d5db',
        color: '#6b7280',
      }}>
        <div style={{ fontSize: 16, marginBottom: 8 }}>No portfolio data</div>
        <div style={{ fontSize: 13 }}>Start trading to see your portfolio allocation</div>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px 16px' }}>
      <style>{`
        @media (max-width: 768px) {
          .portfolio-chart-container {
            flex-direction: column !important;
          }
          .portfolio-chart-area {
            min-width: 0 !important;
            width: 100% !important;
          }
          .portfolio-legend {
            min-width: 100% !important;
            max-width: 100% !important;
            padding-left: 0 !important;
            margin-top: 16px;
          }
        }
      `}</style>
      
      {/* Last Updated Indicator */}
      {lastUpdatedAt && (
        <div style={{ 
          display: 'flex', 
          justifyContent: 'flex-end', 
          marginBottom: 12,
        }}>
          <LastUpdatedIndicator lastUpdatedAt={lastUpdatedAt} />
        </div>
      )}

      {/* Chart and Legend Container - Side by side on desktop, stacked on mobile */}
      <div 
        className="portfolio-chart-container"
        style={{
          display: 'flex',
          flexDirection: showLegendAsList ? 'column' : 'row',
          gap: 24,
          alignItems: 'flex-start',
        }}
      >
      {/* Pie Chart */}
        <div 
          className="portfolio-chart-area"
          style={{
          flex: showLegendAsList ? '1 1 auto' : '1 1 0',
          minWidth: showLegendAsList ? 0 : 200,
          width: showLegendAsList ? '100%' : '100%',
          maxWidth: showLegendAsList ? undefined : 400,
          height: 280,
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
        }}>
          <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={chartData}
              cx="50%"
              cy="50%"
              innerRadius={70}
              outerRadius={110}
              paddingAngle={3}
              dataKey="value"
              label={false}
            >
              {chartData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip content={<CustomTooltip />} />
          </PieChart>
        </ResponsiveContainer>
      </div>

        {/* Custom Legend - Right side on desktop, below on mobile */}
        {!showLegendAsList && (
          <div 
            className="portfolio-legend"
            style={{
              flex: '1 1 auto',
              minWidth: 220,
              maxWidth: 340,
              width: '100%',
            }}
          >
            <CustomLegend />
          </div>
        )}

        {/* Fallback: Show legend below chart if showLegendAsList is true (for backward compatibility) */}
        {showLegendAsList && (
          <div style={{ width: '100%', marginTop: 16 }}>
            <CustomLegend />
          </div>
        )}
      </div>
    </div>
  );
}

export default PortfolioPieChart;
