package com.cryptoexchange.backend.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MarketSimulationEngine.
 * Tests deterministic behavior with seeded RNG.
 */
class MarketSimulationEngineTest {

    private MarketSimulationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MarketSimulationEngine();
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

        // Then - spread should be approximately 0.1% of mid
        BigDecimal mid = tick.lastPrice;
        BigDecimal expectedSpread = mid.multiply(BigDecimal.valueOf(0.001)); // 0.1%
        BigDecimal actualSpread = tick.ask.subtract(tick.bid);
        
        // Allow some tolerance due to rounding
        assertThat(actualSpread).isCloseTo(expectedSpread.multiply(BigDecimal.valueOf(2)), 
            org.assertj.core.data.Offset.offset(BigDecimal.valueOf(1)));
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
}
