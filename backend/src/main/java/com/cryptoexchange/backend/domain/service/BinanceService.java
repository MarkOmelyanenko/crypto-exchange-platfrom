package com.cryptoexchange.backend.domain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for fetching real-time cryptocurrency prices from Binance API.
 * Includes short-TTL in-memory caching to avoid hitting rate limits.
 */
@Service
public class BinanceService {

    private static final Logger log = LoggerFactory.getLogger(BinanceService.class);
    private static final String BINANCE_API_BASE = "https://api.binance.com/api/v3";
    private static final long TICKER_CACHE_TTL_MS = 5_000; // 5 seconds
    private static final long KLINE_CACHE_TTL_MS = 10_000; // 10 seconds

    private final RestTemplate restTemplate;

    // In-memory caches
    private final ConcurrentHashMap<String, CacheEntry<BinanceTicker24h>> tickerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<PriceHistoryPoint>>> klinesCache = new ConcurrentHashMap<>();
    // Cache for ALL 24h tickers fetched in a single call
    private volatile CacheEntry<Map<String, BinanceTicker24h>> allTickersCache;

    public BinanceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static class CacheEntry<T> {
        final T value;
        final long timestamp;

        CacheEntry(T value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    /**
     * Get current price for a symbol (e.g., BTCUSDT).
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @return Current price or null if error
     */
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            String url = BINANCE_API_BASE + "/ticker/price?symbol=" + symbol.toUpperCase();
            ResponseEntity<BinancePriceResponse> response = restTemplate.getForEntity(
                url, BinancePriceResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return new BigDecimal(response.getBody().price);
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch price from Binance for {}: {}", symbol, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching price from Binance for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    /**
     * Get 24h ticker statistics for a symbol (with caching).
     * First checks the all-tickers cache (populated by getBatchTicker24h),
     * then falls back to a per-symbol Binance call.
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @return Ticker data or null if error
     */
    public BinanceTicker24h getTicker24h(String symbol) {
        String key = symbol.toUpperCase();

        // 1. Check per-symbol cache
        CacheEntry<BinanceTicker24h> cached = tickerCache.get(key);
        if (cached != null && !cached.isExpired(TICKER_CACHE_TTL_MS)) {
            return cached.value;
        }

        // 2. Check the all-tickers cache (may have been populated by a recent list call)
        CacheEntry<Map<String, BinanceTicker24h>> allCached = allTickersCache;
        if (allCached != null && !allCached.isExpired(TICKER_CACHE_TTL_MS)) {
            BinanceTicker24h fromAll = allCached.value.get(key);
            if (fromAll != null) {
                tickerCache.put(key, new CacheEntry<>(fromAll));
                return fromAll;
            }
        }

        // 3. Fetch from Binance per-symbol
        try {
            String url = BINANCE_API_BASE + "/ticker/24hr?symbol=" + key;
            ResponseEntity<BinanceTicker24h> response = restTemplate.getForEntity(
                url, BinanceTicker24h.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                BinanceTicker24h ticker = response.getBody();
                tickerCache.put(key, new CacheEntry<>(ticker));
                return ticker;
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch 24h ticker from Binance for {}: {}", symbol, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching 24h ticker from Binance for {}: {}", symbol, e.getMessage());
        }
        // Return stale cache if available
        return cached != null ? cached.value : null;
    }

    /**
     * Fetch ALL 24h tickers from Binance in a single API call and cache the result.
     * This is far more efficient than per-symbol calls when listing many assets.
     * The full ticker list is cached for TICKER_CACHE_TTL_MS (5 seconds).
     */
    private Map<String, BinanceTicker24h> fetchAllTickers() {
        CacheEntry<Map<String, BinanceTicker24h>> cached = allTickersCache;
        if (cached != null && !cached.isExpired(TICKER_CACHE_TTL_MS)) {
            return cached.value;
        }

        try {
            String url = BINANCE_API_BASE + "/ticker/24hr";
            ResponseEntity<BinanceTicker24h[]> response = restTemplate.getForEntity(
                url, BinanceTicker24h[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, BinanceTicker24h> allTickers = new HashMap<>();
                for (BinanceTicker24h ticker : response.getBody()) {
                    if (ticker.symbol != null) {
                        allTickers.put(ticker.symbol.toUpperCase(), ticker);
                    }
                }
                allTickersCache = new CacheEntry<>(allTickers);
                // Also populate the per-symbol cache
                allTickers.forEach((key, ticker) -> tickerCache.put(key, new CacheEntry<>(ticker)));
                log.debug("Fetched {} tickers from Binance (all)", allTickers.size());
                return allTickers;
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch all 24h tickers from Binance: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching all 24h tickers from Binance: {}", e.getMessage());
        }

        // Return stale cache if available
        return cached != null ? cached.value : Collections.emptyMap();
    }

    /**
     * Get 24h ticker statistics for multiple symbols.
     * Fetches ALL tickers in one Binance API call (cached 5s) and filters to requested symbols.
     * Much more efficient than individual calls when listing 100+ assets.
     */
    public Map<String, BinanceTicker24h> getBatchTicker24h(List<String> binanceSymbols) {
        Map<String, BinanceTicker24h> allTickers = fetchAllTickers();
        Map<String, BinanceTicker24h> result = new HashMap<>();

        for (String sym : binanceSymbols) {
            String key = sym.toUpperCase();
            BinanceTicker24h ticker = allTickers.get(key);
            if (ticker != null) {
                result.put(key, ticker);
            }
        }

        return result;
    }

    /**
     * Get kline (candlestick) data for price history.
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @param interval Kline interval (e.g., "1h", "4h", "1d")
     * @param limit Number of data points (max 1000)
     * @return List of price history points
     */
    public List<PriceHistoryPoint> getKlines(String symbol, String interval, int limit) {
        String cacheKey = symbol.toUpperCase() + ":" + interval + ":" + limit;
        CacheEntry<List<PriceHistoryPoint>> cached = klinesCache.get(cacheKey);
        if (cached != null && !cached.isExpired(KLINE_CACHE_TTL_MS)) {
            return cached.value;
        }

        List<PriceHistoryPoint> history = new ArrayList<>();
        
        try {
            String url = String.format("%s/klines?symbol=%s&interval=%s&limit=%d",
                BINANCE_API_BASE, symbol.toUpperCase(), interval, limit);
            
            ResponseEntity<Object[][]> response = restTemplate.getForEntity(url, Object[][].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object[][] klines = response.getBody();
                
                for (Object[] kline : klines) {
                    // Binance kline format: [openTime, open, high, low, close, volume, ...]
                    long openTime = Long.parseLong(kline[0].toString());
                    String closePrice = kline[4].toString(); // Close price
                    
                    OffsetDateTime timestamp = OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(openTime), ZoneOffset.UTC);
                    BigDecimal price = new BigDecimal(closePrice);
                    
                    history.add(new PriceHistoryPoint(timestamp, price));
                }
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch klines from Binance for {}: {}", symbol, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching klines from Binance for {}: {}", symbol, e.getMessage());
        }

        if (!history.isEmpty()) {
            klinesCache.put(cacheKey, new CacheEntry<>(history));
        } else if (cached != null) {
            // Return stale data when Binance is unavailable
            return cached.value;
        }
        
        return history;
    }

    /**
     * Convert asset symbol to Binance trading pair (e.g., "BTC" -> "BTCUSDT").
     */
    public String toBinanceSymbol(String assetSymbol) {
        return assetSymbol.toUpperCase() + "USDT";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BinancePriceResponse {
        @JsonProperty("symbol")
        public String symbol;
        
        @JsonProperty("price")
        public String price;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BinanceTicker24h {
        @JsonProperty("symbol")
        public String symbol;
        
        @JsonProperty("lastPrice")
        public String lastPrice;
        
        @JsonProperty("priceChangePercent")
        public String priceChangePercent;
        
        @JsonProperty("highPrice")
        public String highPrice;
        
        @JsonProperty("lowPrice")
        public String lowPrice;
        
        @JsonProperty("volume")
        public String volume;
    }

    public static class PriceHistoryPoint {
        public final OffsetDateTime timestamp;
        public final BigDecimal priceUsd;

        public PriceHistoryPoint(OffsetDateTime timestamp, BigDecimal priceUsd) {
            this.timestamp = timestamp;
            this.priceUsd = priceUsd;
        }
    }
}
