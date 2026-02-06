package com.cryptoexchange.backend.domain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
 * Service for fetching real-time cryptocurrency prices from WhiteBit API.
 * Includes short-TTL in-memory caching to avoid hitting rate limits.
 *
 * WhiteBit API notes:
 * - Ticker endpoint (/api/v4/public/ticker) returns ALL tickers as a flat map.
 *   There is no per-symbol ticker endpoint; the ?market= param is ignored.
 * - Kline endpoint uses v1: /api/v1/public/kline?market=X&interval=Y&limit=Z
 *   Returns: {"success": true, "result": [[ts, open, close, high, low, vol, quoteVol], ...]}
 */
@Service
public class WhiteBitService {

    private static final Logger log = LoggerFactory.getLogger(WhiteBitService.class);
    private static final String WHITEBIT_TICKER_URL = "https://whitebit.com/api/v4/public/ticker";
    private static final String WHITEBIT_KLINE_URL = "https://whitebit.com/api/v1/public/kline";
    private static final long TICKER_CACHE_TTL_MS = 5_000; // 5 seconds
    private static final long KLINE_CACHE_TTL_MS = 10_000; // 10 seconds

    private final RestTemplate restTemplate;

    // In-memory caches
    private final ConcurrentHashMap<String, CacheEntry<List<PriceHistoryPoint>>> klinesCache = new ConcurrentHashMap<>();
    // Cache for ALL 24h tickers fetched in a single call
    private volatile CacheEntry<Map<String, WhiteBitTicker24h>> allTickersCache;

    public WhiteBitService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        log.info("WhiteBitService initialized — ticker: {}, kline: {}", WHITEBIT_TICKER_URL, WHITEBIT_KLINE_URL);
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
     * Get current price for a symbol (e.g., BTC_USDT).
     * Uses the cached all-tickers endpoint (one API call for all symbols).
     * @param symbol Trading pair symbol (e.g., "BTC_USDT")
     * @return Current price or null if error
     */
    public BigDecimal getCurrentPrice(String symbol) {
        String key = symbol.toUpperCase();
        WhiteBitTicker24h ticker = getTicker24h(key);
        if (ticker != null && ticker.lastPrice != null && !ticker.lastPrice.isEmpty()) {
            try {
                return new BigDecimal(ticker.lastPrice);
            } catch (NumberFormatException e) {
                log.warn("Invalid price format from WhiteBit for {}: {}", symbol, ticker.lastPrice);
            }
        }
        return null;
    }

    /**
     * Get 24h ticker statistics for a symbol (with caching).
     * Uses the all-tickers cache (populated by fetchAllTickers).
     * @param symbol Trading pair symbol (e.g., "BTC_USDT")
     * @return Ticker data or null if error
     */
    public WhiteBitTicker24h getTicker24h(String symbol) {
        String key = symbol.toUpperCase();
        Map<String, WhiteBitTicker24h> allTickers = fetchAllTickers();
        return allTickers.get(key);
    }

    /**
     * Fetch ALL 24h tickers from WhiteBit in a single API call and cache the result.
     * WhiteBit's /ticker endpoint always returns all tickers as a flat map:
     * {"BTC_USDT": {"last_price": "...", "change": "...", ...}, ...}
     * This is far more efficient than per-symbol calls.
     * The full ticker list is cached for TICKER_CACHE_TTL_MS (5 seconds).
     */
    private Map<String, WhiteBitTicker24h> fetchAllTickers() {
        CacheEntry<Map<String, WhiteBitTicker24h>> cached = allTickersCache;
        if (cached != null && !cached.isExpired(TICKER_CACHE_TTL_MS)) {
            return cached.value;
        }

        try {
            ParameterizedTypeReference<Map<String, WhiteBitTicker24h>> typeRef =
                new ParameterizedTypeReference<Map<String, WhiteBitTicker24h>>() {};
            ResponseEntity<Map<String, WhiteBitTicker24h>> response = restTemplate.exchange(
                WHITEBIT_TICKER_URL, HttpMethod.GET, null, typeRef);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, WhiteBitTicker24h> allTickers = new HashMap<>();
                for (Map.Entry<String, WhiteBitTicker24h> entry : response.getBody().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        allTickers.put(entry.getKey().toUpperCase(), entry.getValue());
                    }
                }
                allTickersCache = new CacheEntry<>(allTickers);
                log.debug("Fetched {} tickers from WhiteBit", allTickers.size());
                return allTickers;
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch tickers from WhiteBit: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching tickers from WhiteBit: {}", e.getMessage(), e);
        }

        // Return stale cache if available
        return cached != null ? cached.value : Collections.emptyMap();
    }

    /**
     * Get 24h ticker statistics for multiple symbols.
     * Fetches ALL tickers in one WhiteBit API call (cached 5s) and filters to requested symbols.
     * Much more efficient than individual calls when listing 100+ assets.
     */
    public Map<String, WhiteBitTicker24h> getBatchTicker24h(List<String> whiteBitSymbols) {
        Map<String, WhiteBitTicker24h> allTickers = fetchAllTickers();
        Map<String, WhiteBitTicker24h> result = new HashMap<>();

        for (String sym : whiteBitSymbols) {
            String key = sym.toUpperCase();
            WhiteBitTicker24h ticker = allTickers.get(key);
            if (ticker != null) {
                result.put(key, ticker);
            }
        }

        return result;
    }

    /**
     * Get kline (candlestick) data for price history.
     * Uses WhiteBit v1 API: /api/v1/public/kline?market=X&interval=Y&limit=Z
     * Response: {"success": true, "result": [[ts, open, close, high, low, vol, quoteVol], ...]}
     *
     * @param symbol   Trading pair symbol (e.g., "BTC_USDT")
     * @param interval Kline interval (e.g., "1h", "4h", "1d")
     * @param limit    Number of data points (max 1440)
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
            String whiteBitInterval = convertInterval(interval);
            String url = String.format("%s?market=%s&interval=%s&limit=%d",
                WHITEBIT_KLINE_URL, symbol.toUpperCase(), whiteBitInterval, limit);

            log.debug("Fetching klines from WhiteBit: {}", url);

            ResponseEntity<WhiteBitKlineResponse> response = restTemplate.getForEntity(
                url, WhiteBitKlineResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                WhiteBitKlineResponse body = response.getBody();
                if (Boolean.TRUE.equals(body.success) && body.result != null) {
                    for (List<Object> entry : body.result) {
                        if (entry == null || entry.size() < 3) continue;
                        try {
                            // Format: [timestamp, open, close, high, low, volume, quoteVolume]
                            long timestampSec = ((Number) entry.get(0)).longValue();
                            String closePrice = String.valueOf(entry.get(2));

                            OffsetDateTime timestamp = OffsetDateTime.ofInstant(
                                Instant.ofEpochSecond(timestampSec), ZoneOffset.UTC);
                            BigDecimal price = new BigDecimal(closePrice);

                            history.add(new PriceHistoryPoint(timestamp, price));
                        } catch (Exception e) {
                            log.debug("Skipping malformed kline entry: {}", entry);
                        }
                    }
                    log.debug("Parsed {} kline points for {}", history.size(), symbol);
                }
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch klines from WhiteBit for {}: {}", symbol, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching klines from WhiteBit for {}: {}", symbol, e.getMessage(), e);
        }

        if (!history.isEmpty()) {
            klinesCache.put(cacheKey, new CacheEntry<>(history));
        } else if (cached != null) {
            // Return stale data when WhiteBit is unavailable
            return cached.value;
        }

        return history;
    }

    /**
     * Convert interval format to WhiteBit v1 kline format.
     * WhiteBit v1 kline supports: 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M
     */
    private String convertInterval(String interval) {
        return interval.toLowerCase();
    }

    /**
     * Convert asset symbol to WhiteBit trading pair (e.g., "BTC" -> "BTC_USDT").
     * WhiteBit uses underscore separator.
     */
    public String toWhiteBitSymbol(String assetSymbol) {
        return assetSymbol.toUpperCase() + "_USDT";
    }

    // ----- DTOs -----

    /**
     * WhiteBit v4 ticker response for a single trading pair.
     * Example JSON: {"base_id":0,"quote_id":0,"last_price":"97234.54","quote_volume":"...",
     *                "base_volume":"...","isFrozen":false,"change":"1.23"}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WhiteBitTicker24h {
        @JsonProperty("last_price")
        public String lastPrice;

        /** 24h price change percentage (e.g., "1.23" means +1.23%) */
        @JsonProperty("change")
        public String priceChangePercent;

        /** Not provided by WhiteBit ticker — always null */
        public String highPrice;

        /** Not provided by WhiteBit ticker — always null */
        public String lowPrice;

        @JsonProperty("quote_volume")
        public String quoteVolume;

        @JsonProperty("base_volume")
        public String volume;

        @JsonProperty("isFrozen")
        public Boolean isFrozen;
    }

    /**
     * WhiteBit v1 kline response wrapper.
     * Response: {"success": true, "message": null, "result": [[ts, o, c, h, l, vol, qvol], ...]}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WhiteBitKlineResponse {
        @JsonProperty("success")
        public Boolean success;

        @JsonProperty("message")
        public String message;

        @JsonProperty("result")
        public List<List<Object>> result;
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
