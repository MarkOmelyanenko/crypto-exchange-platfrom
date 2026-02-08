package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.model.MarketTrade;
import com.cryptoexchange.backend.domain.repository.AssetRepository;
import com.cryptoexchange.backend.domain.repository.MarketRepository;
import com.cryptoexchange.backend.domain.repository.MarketTickRepository;
import com.cryptoexchange.backend.domain.repository.MarketTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Disabled: This test requires H2 database.
 * All tests should be hermetic and offline.
 * Use MarketSimulatorServiceTest instead (pure unit test with mocks).
 */
@org.junit.jupiter.api.Disabled("Requires H2 database - use pure unit tests instead")
class MarketSimulatorIntegrationTest {
    @TestConfiguration
    static class MockConfiguration {
        @Bean
        @Primary
        public MarketSimulatorRedisService mockRedisService(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
            return new MarketSimulatorRedisService(null, objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper()) {
                @Override
                public void updateTicker(String marketSymbol, MarketSimulationEngine.TickData tickData) {
                    // Stub - no-op
                }

                @Override
                public void updateRecentTrades(String marketSymbol, List<MarketSimulationEngine.TradeData> trades) {
                    // Stub - no-op
                }
            };
        }
    }

    @Autowired
    private MarketSimulatorService simulatorService;

    @Autowired
    private MarketSimulationEngine simulationEngine;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private MarketTickRepository marketTickRepository;

    @Autowired
    private MarketTradeRepository marketTradeRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private Market testMarket;
    @BeforeEach
    void setUp() {
        // Create test market
        var btc = assetRepository.findBySymbol("BTC")
            .orElseGet(() -> assetRepository.save(new com.cryptoexchange.backend.domain.model.Asset("BTC", "Bitcoin", 8)));
        var usdt = assetRepository.findBySymbol("USDT")
            .orElseGet(() -> assetRepository.save(new com.cryptoexchange.backend.domain.model.Asset("USDT", "Tether", 2)));

        testMarket = marketRepository.findBySymbol("BTC-USDT")
            .orElseGet(() -> marketRepository.save(new Market(btc, usdt, "BTC-USDT")));
    }

    @Test
    void testSimulatorGeneratesAndPersistsData() {
        // Given - simulator is initialized but not running
        assertThat(simulatorService.isRunning()).isFalse();

        // When - manually process a tick
        simulatorService.processMarketTick("BTC-USDT");

        // Then - data should be persisted
        List<MarketTick> ticks = marketTickRepository.findAll();
        assertThat(ticks).isNotEmpty();
        
        List<MarketTrade> trades = marketTradeRepository.findAll();
        // May or may not have trades depending on random generation
        // But at least ticks should exist
        assertThat(ticks.size()).isGreaterThan(0);
    }

    @Test
    void testRedisSnapshotUpdate() {
        // Given
        String marketSymbol = "BTC-USDT";
        
        // When - process a tick
        simulatorService.processMarketTick(marketSymbol);

        // Then - verify tick was persisted (Redis is stubbed, so we verify DB persistence)
        var latestTick = marketTickRepository.findFirstByMarketSymbolOrderByTsDesc(marketSymbol);
        assertThat(latestTick).isPresent();
        
        // Verify trades may have been generated
        List<MarketTrade> trades = marketTradeRepository.findTop50ByMarketSymbolOrderByTsDesc(marketSymbol);
        // Trades are optional depending on random generation
    }

    @Test
    void testSimulatorStartStop() {
        // Given
        assertThat(simulatorService.isRunning()).isFalse();

        // When - start
        simulatorService.start();
        assertThat(simulatorService.isRunning()).isTrue();

        // When - stop
        simulatorService.stop();
        assertThat(simulatorService.isRunning()).isFalse();
    }

    @Test
    void testSimulatorStatus() {
        // When
        var status = simulatorService.getStatus();

        // Then
        assertThat(status).isNotNull();
        assertThat(status.get("enabled")).isEqualTo(true);
        assertThat(status.get("running")).isNotNull();
    }
}
