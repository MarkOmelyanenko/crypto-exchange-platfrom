import { useState, useEffect, useRef, useCallback } from 'react';

// Use empty string for production (same domain), localhost for development
// VITE_API_BASE_URL is set to empty string in production build
const API_BASE = import.meta.env.VITE_API_BASE_URL !== undefined 
  ? import.meta.env.VITE_API_BASE_URL 
  : 'http://localhost:8080';

/**
 * React hook for real-time price streaming via SSE.
 *
 * @param {string[]} symbols - Array of asset symbols to subscribe to (e.g. ['BTC', 'ETH'])
 * @returns {{ prices: Object, connected: boolean, error: string|null }}
 *   - prices: { [symbol]: { priceUsd, change24hPercent, ts } }
 *   - connected: true when SSE stream is active
 *   - error: error message if stream failed (null when OK)
 */
export function usePriceStream(symbols) {
  const [prices, setPrices] = useState({});
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState(null);
  const eventSourceRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectAttemptRef = useRef(0);
  const fallbackIntervalRef = useRef(null);
  const symbolsKeyRef = useRef('');

  // Stable symbols key to avoid unnecessary reconnects
  const symbolsKey = [...symbols].sort().join(',');

  const cleanup = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    if (fallbackIntervalRef.current) {
      clearInterval(fallbackIntervalRef.current);
      fallbackIntervalRef.current = null;
    }
  }, []);

  const startFallbackPolling = useCallback(() => {
    // Fallback: poll REST every 10s when SSE fails
    if (fallbackIntervalRef.current) return;

    const poll = async () => {
      try {
        const symbolsParam = symbols.join(',');
        const response = await fetch(
          `${API_BASE}/api/prices/snapshot?symbols=${encodeURIComponent(symbolsParam)}`
        );
        if (response.ok) {
          const data = await response.json();
          setPrices(prev => {
            const next = { ...prev };
            for (const item of data) {
              if (item.symbol && item.priceUsd != null) {
                next[item.symbol] = {
                  priceUsd: item.priceUsd,
                  change24hPercent: null,
                  ts: item.timestamp,
                };
              }
            }
            return next;
          });
          // Mark as connected when fallback polling successfully receives data
          setConnected(true);
          setError(null);
        }
      } catch {
        // Silently fail; will retry on next interval
      }
    };

    poll(); // Immediate first fetch
    fallbackIntervalRef.current = setInterval(poll, 10_000);
  }, [symbols]);

  const connect = useCallback(() => {
    if (!symbols || symbols.length === 0) return;

    cleanup();

    const url = `${API_BASE}/api/stream/prices?symbols=${encodeURIComponent(symbols.join(','))}`;
    const es = new EventSource(url);
    eventSourceRef.current = es;

    es.addEventListener('price', (event) => {
      try {
        const data = JSON.parse(event.data);
        setPrices(prev => ({
          ...prev,
          [data.symbol]: {
            priceUsd: data.priceUsd,
            change24hPercent: data.change24hPercent,
            ts: data.ts,
          },
        }));
      } catch {
        // Ignore malformed events
      }
    });

    es.addEventListener('heartbeat', () => {
      // Heartbeat received — connection is healthy
    });

    es.onopen = () => {
      setConnected(true);
      setError(null);
      reconnectAttemptRef.current = 0;
      // Stop fallback polling if SSE reconnected
      if (fallbackIntervalRef.current) {
        clearInterval(fallbackIntervalRef.current);
        fallbackIntervalRef.current = null;
      }
    };

    es.onerror = () => {
      setConnected(false);
      es.close();
      eventSourceRef.current = null;

      // Exponential backoff: 1s, 2s, 4s, 8s, 10s max
      const attempt = reconnectAttemptRef.current;
      const delay = Math.min(1000 * Math.pow(2, attempt), 10_000);
      reconnectAttemptRef.current = attempt + 1;

      setError(`Live disconnected — retrying in ${Math.round(delay / 1000)}s…`);

      // Start fallback polling after 1 failed attempt
      if (attempt >= 1) {
        startFallbackPolling();
      }

      reconnectTimeoutRef.current = setTimeout(() => {
        connect();
      }, delay);
    };
  }, [symbols, cleanup, startFallbackPolling]);

  useEffect(() => {
    // Only reconnect if symbols actually changed
    if (symbolsKey === symbolsKeyRef.current) return;
    symbolsKeyRef.current = symbolsKey;

    if (symbols.length === 0) {
      cleanup();
      setConnected(false);
      return;
    }

    connect();

    return () => {
      cleanup();
    };
  }, [symbolsKey]); // eslint-disable-line react-hooks/exhaustive-deps

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cleanup();
    };
  }, [cleanup]);

  return { prices, connected, error };
}
