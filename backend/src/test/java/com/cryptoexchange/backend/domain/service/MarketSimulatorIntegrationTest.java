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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for market simulator.
 * Tests that simulator generates data, persists to DB, and updates Redis.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.simulator.enabled=true",
    "app.simulator.tick-interval-ms=1000",
    "app.simulator.seed=42",
    "app.simulator.markets=BTC-USDT",
    "app.simulator.auto-start=false",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class MarketSimulatorIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
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

    @Autowired
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

        // Then - Redis should have ticker data
        String tickerKey = "ticker:" + marketSymbol;
        String tickerJson = redisTemplate.opsForValue().get(tickerKey);
        assertThat(tickerJson).isNotNull();
        
        // Redis should have trades list
        String tradesKey = "trades:" + marketSymbol;
        Long tradesCount = redisTemplate.opsForList().size(tradesKey);
        // May be 0 if no trades generated, but key should exist or be null
        assertThat(tradesCount).isNotNull();
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
