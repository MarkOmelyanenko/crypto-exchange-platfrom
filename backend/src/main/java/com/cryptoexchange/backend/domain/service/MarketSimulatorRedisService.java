package com.cryptoexchange.backend.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis service for storing latest market ticker and recent trades.
 * Provides fast read access for market data endpoints.
 */
@Service
public class MarketSimulatorRedisService {

    private static final Logger log = LoggerFactory.getLogger(MarketSimulatorRedisService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.simulator.redis.ticker-ttl:60}")
    private long tickerTtlSeconds;

    @Value("${app.simulator.redis.trades-ttl:60}")
    private long tradesTtlSeconds;

    @Value("${app.simulator.redis.max-recent-trades:50}")
    private int maxRecentTrades;

    public MarketSimulatorRedisService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Update ticker snapshot in Redis.
     */
    public void updateTicker(String marketSymbol, MarketSimulationEngine.TickData tickData) {
        try {
            String key = "ticker:" + marketSymbol;
            TickerSnapshot snapshot = new TickerSnapshot(
                tickData.ts,
                tickData.lastPrice,
                tickData.bid,
                tickData.ask,
                tickData.volume
            );
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(tickerTtlSeconds));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ticker snapshot for {}: {}", marketSymbol, e.getMessage());
        }
    }

    /**
     * Get ticker snapshot from Redis.
     */
    public TickerSnapshot getTicker(String marketSymbol) {
        try {
            String key = "ticker:" + marketSymbol;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, TickerSnapshot.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ticker snapshot for {}: {}", marketSymbol, e.getMessage());
            return null;
        }
    }

    /**
     * Update recent trades list in Redis.
     */
    public void updateRecentTrades(String marketSymbol, List<MarketSimulationEngine.TradeData> trades) {
        try {
            String key = "trades:" + marketSymbol;
            
            // Push new trades to the list (left push = newest first)
            for (MarketSimulationEngine.TradeData trade : trades) {
                TradeSnapshot snapshot = new TradeSnapshot(
                    trade.ts,
                    trade.price,
                    trade.qty,
                    trade.side.name()
                );
                String json = objectMapper.writeValueAsString(snapshot);
                redisTemplate.opsForList().leftPush(key, json);
            }
            
            // Trim to max size
            redisTemplate.opsForList().trim(key, 0, maxRecentTrades - 1);
            
            // Set TTL
            redisTemplate.expire(key, Duration.ofSeconds(tradesTtlSeconds));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize trades for {}: {}", marketSymbol, e.getMessage());
        }
    }

    /**
     * Get recent trades from Redis.
     */
    public List<TradeSnapshot> getRecentTrades(String marketSymbol, int limit) {
        try {
            String key = "trades:" + marketSymbol;
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, limit - 1);
            
            if (jsonList == null || jsonList.isEmpty()) {
                return new ArrayList<>();
            }
            
            List<TradeSnapshot> trades = new ArrayList<>();
            for (String json : jsonList) {
                trades.add(objectMapper.readValue(json, TradeSnapshot.class));
            }
            return trades;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize trades for {}: {}", marketSymbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    // Data classes
    public static class TickerSnapshot {
        public OffsetDateTime ts;
        public BigDecimal last;
        public BigDecimal bid;
        public BigDecimal ask;
        public BigDecimal volume;

        public TickerSnapshot() {
        }

        public TickerSnapshot(OffsetDateTime ts, BigDecimal last, BigDecimal bid, BigDecimal ask, BigDecimal volume) {
            this.ts = ts;
            this.last = last;
            this.bid = bid;
            this.ask = ask;
            this.volume = volume;
        }
    }

    public static class TradeSnapshot {
        public OffsetDateTime ts;
        public BigDecimal price;
        public BigDecimal qty;
        public String side;

        public TradeSnapshot() {
        }

        public TradeSnapshot(OffsetDateTime ts, BigDecimal price, BigDecimal qty, String side) {
            this.ts = ts;
            this.price = price;
            this.qty = qty;
            this.side = side;
        }
    }
}
