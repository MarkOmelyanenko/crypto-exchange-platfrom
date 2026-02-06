import { useMemo, memo } from 'react';
import { tokenIcons } from '@web3icons/react';

/**
 * Reusable component for displaying cryptocurrency icons
 * Uses @web3icons/react package for comprehensive icon coverage
 * @param {string} symbol - The cryptocurrency symbol (e.g., 'BTC', 'ETH')
 * @param {number} size - Icon size in pixels (default: 24)
 * @param {object} style - Additional inline styles
 */
function CryptoIcon({ symbol, size = 24, style = {} }) {
  // Memoize the icon lookup - only recalculates when symbol changes
  const IconComponent = useMemo(() => {
    if (!symbol) return null;

    // Normalize symbol to uppercase for the icon name
    const normalizedSymbol = symbol.toUpperCase();
    const iconName = `Token${normalizedSymbol}`;
    
    // Check if the icon exists in tokenIcons
    return tokenIcons[iconName] || null;
  }, [symbol]);

  // Memoize the fallback component
  const FallbackIcon = useMemo(() => (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        backgroundColor: '#e5e7eb',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: size * 0.4,
        fontWeight: 600,
        color: '#6b7280',
        verticalAlign: 'middle',
        flexShrink: 0,
        ...style,
      }}
      title={symbol}
    >
      {symbol?.charAt(0).toUpperCase() || '?'}
    </div>
  ), [size, symbol, style]);

  if (!symbol) return null;

  if (!IconComponent) {
    return FallbackIcon;
  }

  // Memoize the icon wrapper to prevent re-renders
  return useMemo(() => (
    <div
      style={{
        display: 'inline-block',
        verticalAlign: 'middle',
        flexShrink: 0,
        ...style,
      }}
    >
      <IconComponent size={size} />
    </div>
  ), [IconComponent, size, style]);
}

// Memoize the entire component to prevent re-renders when parent updates
// React.memo will do a shallow comparison of props by default
export default memo(CryptoIcon);
