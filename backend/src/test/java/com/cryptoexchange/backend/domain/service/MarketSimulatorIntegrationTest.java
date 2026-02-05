package com.cryptoexchange.backend.domain.service;
//  - Removed to prevent Spring context loading
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
//  - Removed to prevent Spring context loading
import java.math.BigDecimal;
import java.util.List;
//  - Removed to prevent Spring context loading
import static org.assertj.core.api.Assertions.assertThat;
//  - Removed to prevent Spring context loading
/**
 * Disabled: This test requires H2 database.
 * All tests should be hermetic and offline.
 * Use MarketSimulatorServiceTest instead (pure unit test with mocks).
 */
@org.junit.jupiter.api.Disabled("Requires H2 database - use pure unit tests instead")
// @SpringBootTest - Removed to prevent Spring context loading
// @Transactional - Removed to prevent Spring context loading
// @TestPropertySource(properties = {
//    "spring.datasource.url=jdbc:h2:mem:testdb_sim_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
//    "spring.datasource.driver-class-name=org.h2.Driver",
//    "spring.datasource.username=sa",
//    "spring.datasource.password=",
//    "spring.jpa.hibernate.ddl-auto=create-drop",
//    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
//    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
//    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
//    "spring.flyway.enabled=false",
//    "app.simulator.enabled=true",
//    "app.simulator.tick-interval-ms=1000",
//    "app.simulator.seed=42",
//    "app.simulator.markets=BTC-USDT",
//    "app.simulator.auto-start=false",
//    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
//    "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
//    "management.health.kafka.enabled=false"
// })
class MarketSimulatorIntegrationTest {
//  - Removed to prevent Spring context loading
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
//  - Removed to prevent Spring context loading
                @Override
                public void updateRecentTrades(String marketSymbol, List<MarketSimulationEngine.TradeData> trades) {
                    // Stub - no-op
                }
            };
        }
    }
//  - Removed to prevent Spring context loading
    @Autowired
    private MarketSimulatorService simulatorService;
//  - Removed to prevent Spring context loading
    @Autowired
    private MarketSimulationEngine simulationEngine;
//  - Removed to prevent Spring context loading
    @Autowired
    private MarketRepository marketRepository;
//  - Removed to prevent Spring context loading
    @Autowired
    private AssetRepository assetRepository;
//  - Removed to prevent Spring context loading
    @Autowired
    private MarketTickRepository marketTickRepository;
//  - Removed to prevent Spring context loading
    @Autowired
    private MarketTradeRepository marketTradeRepository;
//  - Removed to prevent Spring context loading
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;
//  - Removed to prevent Spring context loading
    private Market testMarket;
//  - Removed to prevent Spring context loading
    @BeforeEach
    void setUp() {
        // Create test market
        var btc = assetRepository.findBySymbol("BTC")
            .orElseGet(() -> assetRepository.save(new com.cryptoexchange.backend.domain.model.Asset("BTC", "Bitcoin", 8)));
        var usdt = assetRepository.findBySymbol("USDT")
            .orElseGet(() -> assetRepository.save(new com.cryptoexchange.backend.domain.model.Asset("USDT", "Tether", 2)));
//  - Removed to prevent Spring context loading
        testMarket = marketRepository.findBySymbol("BTC-USDT")
            .orElseGet(() -> marketRepository.save(new Market(btc, usdt, "BTC-USDT")));
    }
//  - Removed to prevent Spring context loading
    @Test
    void testSimulatorGeneratesAndPersistsData() {
        // Given - simulator is initialized but not running
        assertThat(simulatorService.isRunning()).isFalse();
//  - Removed to prevent Spring context loading
        // When - manually process a tick
        simulatorService.processMarketTick("BTC-USDT");
//  - Removed to prevent Spring context loading
        // Then - data should be persisted
        List<MarketTick> ticks = marketTickRepository.findAll();
        assertThat(ticks).isNotEmpty();
        
        List<MarketTrade> trades = marketTradeRepository.findAll();
        // May or may not have trades depending on random generation
        // But at least ticks should exist
        assertThat(ticks.size()).isGreaterThan(0);
    }
//  - Removed to prevent Spring context loading
    @Test
    void testRedisSnapshotUpdate() {
        // Given
        String marketSymbol = "BTC-USDT";
        
        // When - process a tick
        simulatorService.processMarketTick(marketSymbol);
//  - Removed to prevent Spring context loading
        // Then - verify tick was persisted (Redis is stubbed, so we verify DB persistence)
        var latestTick = marketTickRepository.findFirstByMarketSymbolOrderByTsDesc(marketSymbol);
        assertThat(latestTick).isPresent();
        
        // Verify trades may have been generated
        List<MarketTrade> trades = marketTradeRepository.findTop50ByMarketSymbolOrderByTsDesc(marketSymbol);
        // Trades are optional depending on random generation
    }
//  - Removed to prevent Spring context loading
    @Test
    void testSimulatorStartStop() {
        // Given
        assertThat(simulatorService.isRunning()).isFalse();
//  - Removed to prevent Spring context loading
        // When - start
        simulatorService.start();
        assertThat(simulatorService.isRunning()).isTrue();
//  - Removed to prevent Spring context loading
        // When - stop
        simulatorService.stop();
        assertThat(simulatorService.isRunning()).isFalse();
    }
//  - Removed to prevent Spring context loading
    @Test
    void testSimulatorStatus() {
        // When
        var status = simulatorService.getStatus();
//  - Removed to prevent Spring context loading
        // Then
        assertThat(status).isNotNull();
        assertThat(status.get("enabled")).isEqualTo(true);
        assertThat(status.get("running")).isNotNull();
    }
}
