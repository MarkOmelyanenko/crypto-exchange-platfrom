package com.cryptoexchange.backend.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MarketSimulationEngine.
 * Tests deterministic behavior with seeded RNG.
 */
class MarketSimulationEngineTest {

    private MarketSimulationEngine engine;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        engine = new MarketSimulationEngine(fixedClock);
    }

    @Test
    void testDeterministicPriceGeneration() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed = 42;
        BigDecimal volatility = BigDecimal.valueOf(0.012);
        BigDecimal spreadBps = BigDecimal.valueOf(8);

        // When - initialize and generate first tick
        engine.initializeMarket(marketSymbol, initialPrice, seed);
        MarketSimulationEngine.TickData tick1 = engine.generateTick(marketSymbol, volatility, spreadBps);

        // Then - price should be close to initial (within reasonable range)
        assertThat(tick1.lastPrice).isNotNull();
        assertThat(tick1.lastPrice.compareTo(BigDecimal.ZERO)).isPositive();
        assertThat(tick1.bid).isLessThan(tick1.lastPrice);
        assertThat(tick1.ask).isGreaterThan(tick1.lastPrice);
        assertThat(tick1.volume).isNotNull();
    }

    @Test
    void testPriceEvolution() {
        // Given
        String marketSymbol = "ETH-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(3500);
        long seed = 100;
        BigDecimal volatility = BigDecimal.valueOf(0.01);
        BigDecimal spreadBps = BigDecimal.valueOf(10);

        engine.initializeMarket(marketSymbol, initialPrice, seed);

        // When - generate multiple ticks
        MarketSimulationEngine.TickData tick1 = engine.generateTick(marketSymbol, volatility, spreadBps);
        MarketSimulationEngine.TickData tick2 = engine.generateTick(marketSymbol, volatility, spreadBps);
        MarketSimulationEngine.TickData tick3 = engine.generateTick(marketSymbol, volatility, spreadBps);

        // Then - prices should evolve (not all the same)
        // Note: Due to randomness, we can't guarantee they're all different,
        // but we can check they're reasonable
        assertThat(tick1.lastPrice).isNotNull();
        assertThat(tick2.lastPrice).isNotNull();
        assertThat(tick3.lastPrice).isNotNull();
        
        // All prices should be positive
        assertThat(tick1.lastPrice.compareTo(BigDecimal.ZERO)).isPositive();
        assertThat(tick2.lastPrice.compareTo(BigDecimal.ZERO)).isPositive();
        assertThat(tick3.lastPrice.compareTo(BigDecimal.ZERO)).isPositive();
    }

    @Test
    void testSpreadCalculation() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed = 42;
        BigDecimal volatility = BigDecimal.valueOf(0.01);
        BigDecimal spreadBps = BigDecimal.valueOf(10); // 10 bps = 0.1%

        engine.initializeMarket(marketSymbol, initialPrice, seed);
        MarketSimulationEngine.TickData tick = engine.generateTick(marketSymbol, volatility, spreadBps);

        // Then - spread should be approximately 0.1% of mid (10 bps = 0.1%)
        BigDecimal mid = tick.lastPrice;
        BigDecimal expectedSpread = mid.multiply(BigDecimal.valueOf(0.001)); // 0.1%
        BigDecimal actualSpread = tick.ask.subtract(tick.bid);
        
        // Allow tolerance due to rounding and random variation
        // The spread might vary slightly, so we check it's in a reasonable range
        assertThat(actualSpread).isBetween(
            expectedSpread.multiply(BigDecimal.valueOf(0.5)), // At least 50% of expected
            expectedSpread.multiply(BigDecimal.valueOf(2.0))  // At most 200% of expected
        );
    }

    @Test
    void testTradeGeneration() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed = 42;
        BigDecimal midPrice = BigDecimal.valueOf(65000);
        int avgTradesPerTick = 2;
        BigDecimal minQty = BigDecimal.valueOf(0.001);
        BigDecimal maxQty = BigDecimal.valueOf(1.0);

        engine.initializeMarket(marketSymbol, initialPrice, seed);
        java.util.Random rng = new java.util.Random(seed);

        // When
        var trades = engine.generateTrades(marketSymbol, midPrice, avgTradesPerTick, minQty, maxQty, rng);

        // Then
        assertThat(trades).isNotNull();
        // Should generate some trades (may be 0 to 3x average)
        assertThat(trades.size()).isLessThanOrEqualTo(avgTradesPerTick * 3);
        
        // If trades exist, validate them
        for (var trade : trades) {
            assertThat(trade.price).isNotNull();
            assertThat(trade.price.compareTo(BigDecimal.ZERO)).isPositive();
            assertThat(trade.qty).isNotNull();
            assertThat(trade.qty.compareTo(BigDecimal.ZERO)).isPositive();
            assertThat(trade.side).isNotNull();
            assertThat(trade.ts).isNotNull();
        }
    }

    @Test
    void shouldGenerateIdenticalTicks_whenSameSeedAndInitialState() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed = 42L;
        BigDecimal volatility = BigDecimal.valueOf(0.012);
        BigDecimal spreadBps = BigDecimal.valueOf(8);

        // When - first run
        MarketSimulationEngine engine1 = new MarketSimulationEngine(fixedClock);
        engine1.initializeMarket(marketSymbol, initialPrice, seed);
        MarketSimulationEngine.TickData tick1a = engine1.generateTick(marketSymbol, volatility, spreadBps);
        MarketSimulationEngine.TickData tick2a = engine1.generateTick(marketSymbol, volatility, spreadBps);
        MarketSimulationEngine.TickData tick3a = engine1.generateTick(marketSymbol, volatility, spreadBps);

        // When - second run with same seed
        MarketSimulationEngine engine2 = new MarketSimulationEngine(fixedClock);
        engine2.initializeMarket(marketSymbol, initialPrice, seed);
        MarketSimulationEngine.TickData tick1b = engine2.generateTick(marketSymbol, volatility, spreadBps);
        MarketSimulationEngine.TickData tick2b = engine2.generateTick(marketSymbol, volatility, spreadBps);
        MarketSimulationEngine.TickData tick3b = engine2.generateTick(marketSymbol, volatility, spreadBps);

        // Then - results must be identical
        assertThat(tick1b.lastPrice).isEqualByComparingTo(tick1a.lastPrice);
        assertThat(tick1b.bid).isEqualByComparingTo(tick1a.bid);
        assertThat(tick1b.ask).isEqualByComparingTo(tick1a.ask);
        assertThat(tick1b.volume).isEqualByComparingTo(tick1a.volume);

        assertThat(tick2b.lastPrice).isEqualByComparingTo(tick2a.lastPrice);
        assertThat(tick3b.lastPrice).isEqualByComparingTo(tick3a.lastPrice);
    }

    @Test
    void shouldGenerateDifferentTicks_whenDifferentSeed() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed1 = 42L;
        long seed2 = 100L;
        BigDecimal volatility = BigDecimal.valueOf(0.012);
        BigDecimal spreadBps = BigDecimal.valueOf(8);

        // When
        MarketSimulationEngine engine1 = new MarketSimulationEngine(fixedClock);
        engine1.initializeMarket(marketSymbol, initialPrice, seed1);
        MarketSimulationEngine.TickData tick1 = engine1.generateTick(marketSymbol, volatility, spreadBps);

        MarketSimulationEngine engine2 = new MarketSimulationEngine(fixedClock);
        engine2.initializeMarket(marketSymbol, initialPrice, seed2);
        MarketSimulationEngine.TickData tick2 = engine2.generateTick(marketSymbol, volatility, spreadBps);

        // Then - results should differ
        assertThat(tick2.lastPrice).isNotEqualByComparingTo(tick1.lastPrice);
    }

    @Test
    void shouldMaintainInvariants_whenGeneratingTicks() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed = 42L;
        BigDecimal volatility = BigDecimal.valueOf(0.012);
        BigDecimal spreadBps = BigDecimal.valueOf(8);

        engine.initializeMarket(marketSymbol, initialPrice, seed);

        // When - generate multiple ticks
        for (int i = 0; i < 10; i++) {
            MarketSimulationEngine.TickData tick = engine.generateTick(marketSymbol, volatility, spreadBps);

            // Then - validate invariants
            assertThat(tick.lastPrice).isNotNull();
            assertThat(tick.lastPrice.compareTo(BigDecimal.ZERO)).isPositive();
            assertThat(tick.bid).isNotNull();
            assertThat(tick.ask).isNotNull();
            assertThat(tick.bid.compareTo(tick.ask)).isLessThan(0); // bid < ask
            assertThat(tick.lastPrice.compareTo(tick.bid)).isGreaterThanOrEqualTo(0); // last >= bid
            assertThat(tick.lastPrice.compareTo(tick.ask)).isLessThanOrEqualTo(0); // last <= ask
            assertThat(tick.volume).isNotNull();
            assertThat(tick.volume.compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0);
            assertThat(tick.ts).isNotNull();
        }
    }

    @Test
    void shouldGenerateTradesWithValidInvariants() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed = 42L;
        BigDecimal midPrice = BigDecimal.valueOf(65000);
        int avgTradesPerTick = 2;
        BigDecimal minQty = BigDecimal.valueOf(0.001);
        BigDecimal maxQty = BigDecimal.valueOf(1.0);

        engine.initializeMarket(marketSymbol, initialPrice, seed);
        java.util.Random rng = new java.util.Random(seed);

        // When
        List<MarketSimulationEngine.TradeData> trades = engine.generateTrades(
            marketSymbol, midPrice, avgTradesPerTick, minQty, maxQty, rng);

        // Then - validate invariants
        assertThat(trades).isNotNull();
        for (MarketSimulationEngine.TradeData trade : trades) {
            assertThat(trade.price).isNotNull();
            assertThat(trade.price.compareTo(BigDecimal.ZERO)).isPositive();
            assertThat(trade.qty).isNotNull();
            assertThat(trade.qty.compareTo(BigDecimal.ZERO)).isPositive();
            assertThat(trade.side).isNotNull();
            assertThat(trade.ts).isNotNull();
        }
    }

    @Test
    void shouldHaveMonotonicTimestamps_whenGeneratingMultipleTrades() {
        // Given
        String marketSymbol = "BTC-USDT";
        BigDecimal initialPrice = BigDecimal.valueOf(65000);
        long seed = 42L;
        BigDecimal midPrice = BigDecimal.valueOf(65000);
        int avgTradesPerTick = 5;
        BigDecimal minQty = BigDecimal.valueOf(0.001);
        BigDecimal maxQty = BigDecimal.valueOf(1.0);

        engine.initializeMarket(marketSymbol, initialPrice, seed);
        java.util.Random rng = new java.util.Random(seed);

        // When
        List<MarketSimulationEngine.TradeData> trades = engine.generateTrades(
            marketSymbol, midPrice, avgTradesPerTick, minQty, maxQty, rng);

        // Then - timestamps should be monotonic (or equal)
        if (trades.size() > 1) {
            for (int i = 1; i < trades.size(); i++) {
                OffsetDateTime prev = trades.get(i - 1).ts;
                OffsetDateTime curr = trades.get(i).ts;
                assertThat(curr.isAfter(prev) || curr.isEqual(prev)).isTrue();
            }
        }
    }
}
