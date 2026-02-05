package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.MarketTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic market simulation engine.
 * Uses seeded random number generator for reproducible results.
 */
@Component
public class MarketSimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(MarketSimulationEngine.class);
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);

    private final Clock clock;

    // Per-market state
    private static class MarketState {
        BigDecimal lastPrice;
        BigDecimal rollingVolume24h;
        OffsetDateTime lastTs;
        Random rng;

        MarketState(BigDecimal initialPrice, long seed, Clock clock) {
            this.lastPrice = initialPrice;
            this.rollingVolume24h = BigDecimal.ZERO;
            this.lastTs = OffsetDateTime.now(clock);
            this.rng = new Random(seed);
        }
    }

    private final Map<String, MarketState> marketStates = new ConcurrentHashMap<>();

    public MarketSimulationEngine() {
        this(Clock.systemUTC());
    }

    public MarketSimulationEngine(Clock clock) {
        this.clock = clock;
    }

    /**
     * Initialize or reset market state.
     */
    public void initializeMarket(String marketSymbol, BigDecimal initialPrice, long seed) {
        marketStates.put(marketSymbol, new MarketState(initialPrice, seed, clock));
        log.debug("Initialized market {} with price {} and seed {}", marketSymbol, initialPrice, seed);
    }

    /**
     * Generate next tick for a market.
     * Returns tick data: lastPrice, bid, ask, volume.
     */
    public TickData generateTick(String marketSymbol, BigDecimal volatility, BigDecimal spreadBps) {
        MarketState state = marketStates.get(marketSymbol);
        if (state == null) {
            throw new IllegalStateException("Market not initialized: " + marketSymbol);
        }

        // Geometric random walk: newPrice = lastPrice * exp(drift + volatility * normalRandom)
        // For simplicity, drift = 0 (can be made configurable)
        double normalRandom = generateNormalRandom(state.rng);
        double changeFactor = Math.exp(volatility.doubleValue() * normalRandom);
        
        BigDecimal newPrice = state.lastPrice.multiply(BigDecimal.valueOf(changeFactor), MC);
        
        // Ensure price stays positive and reasonable
        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            newPrice = state.lastPrice; // Keep previous price if calculation goes wrong
        }

        // Calculate bid/ask from spread
        BigDecimal mid = newPrice;
        BigDecimal spreadFactor = spreadBps.divide(BigDecimal.valueOf(20000), MC);
        BigDecimal bid = mid.multiply(BigDecimal.ONE.subtract(spreadFactor), MC);
        BigDecimal ask = mid.multiply(BigDecimal.ONE.add(spreadFactor), MC);

        // Update rolling volume approximation (simple exponential decay)
        // volume24h = volume24h * 0.999 + newVolume * 0.001 (approximate)
        // For simplicity, we'll add a small random volume increment
        BigDecimal volumeIncrement = BigDecimal.valueOf(state.rng.nextDouble() * 1000 + 100); // 100-1100
        state.rollingVolume24h = state.rollingVolume24h
            .multiply(BigDecimal.valueOf(0.999), MC)
            .add(volumeIncrement, MC);

        // Update state
        state.lastPrice = newPrice;
        state.lastTs = OffsetDateTime.now(clock);

        return new TickData(newPrice, bid, ask, state.rollingVolume24h, state.lastTs);
    }

    /**
     * Generate trades for a tick.
     */
    public List<TradeData> generateTrades(String marketSymbol, BigDecimal midPrice, int avgTradesPerTick, 
                                         BigDecimal minQty, BigDecimal maxQty, Random rng) {
        MarketState state = marketStates.get(marketSymbol);
        if (state == null) {
            throw new IllegalStateException("Market not initialized: " + marketSymbol);
        }

        // Generate number of trades (Poisson-like approximation)
        int numTrades = Math.max(0, (int) Math.round(avgTradesPerTick + (rng.nextGaussian() * Math.sqrt(avgTradesPerTick))));
        numTrades = Math.min(numTrades, avgTradesPerTick * 3); // Cap at 3x average

        List<TradeData> trades = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(clock);

        for (int i = 0; i < numTrades; i++) {
            // Random side
            MarketTrade.TradeSide side = rng.nextBoolean() ? MarketTrade.TradeSide.BUY : MarketTrade.TradeSide.SELL;
            
            // Price near mid (within 0.1% range)
            double priceOffset = (rng.nextDouble() - 0.5) * 0.002; // -0.1% to +0.1%
            BigDecimal price = midPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(priceOffset)), MC);
            
            // Random quantity
            double qtyFactor = minQty.doubleValue() + rng.nextDouble() * (maxQty.doubleValue() - minQty.doubleValue());
            BigDecimal qty = BigDecimal.valueOf(qtyFactor);

            trades.add(new TradeData(price, qty, side, now.plusNanos(i))); // Slight time offset for ordering
        }

        return trades;
    }

    /**
     * Box-Muller transform for normal distribution.
     */
    private double generateNormalRandom(Random rng) {
        if (rng == null) {
            rng = new Random();
        }
        // Use Box-Muller transform
        double u1 = rng.nextDouble();
        double u2 = rng.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    // Data classes
    public static class TickData {
        public final BigDecimal lastPrice;
        public final BigDecimal bid;
        public final BigDecimal ask;
        public final BigDecimal volume;
        public final OffsetDateTime ts;

        public TickData(BigDecimal lastPrice, BigDecimal bid, BigDecimal ask, BigDecimal volume, OffsetDateTime ts) {
            this.lastPrice = lastPrice;
            this.bid = bid;
            this.ask = ask;
            this.volume = volume;
            this.ts = ts;
        }
    }

    public static class TradeData {
        public final BigDecimal price;
        public final BigDecimal qty;
        public final MarketTrade.TradeSide side;
        public final OffsetDateTime ts;

        public TradeData(BigDecimal price, BigDecimal qty, MarketTrade.TradeSide side, OffsetDateTime ts) {
            this.price = price;
            this.qty = qty;
            this.side = side;
            this.ts = ts;
        }
    }
}
