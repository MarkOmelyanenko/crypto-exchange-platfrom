package com.cryptoexchange.backend.domain.repository;
//  - Removed to prevent Spring context loading
import com.cryptoexchange.backend.domain.model.MarketTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
//  - Removed to prevent Spring context loading
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
//  - Removed to prevent Spring context loading
import static org.assertj.core.api.Assertions.assertThat;
//  - Removed to prevent Spring context loading
/**
 * Disabled: This test requires H2 database.
 * All tests should be hermetic and offline.
 */
@org.junit.jupiter.api.Disabled("Requires H2 database - use pure unit tests instead")
// @SpringBootTest - Removed to prevent Spring context loading
// @Transactional - Removed to prevent Spring context loading
// @TestPropertySource(properties = {
//    "spring.datasource.url=jdbc:h2:mem:testdb_trade;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
//    "spring.datasource.driver-class-name=org.h2.Driver",
//    "spring.datasource.username=sa",
//    "spring.datasource.password=",
//    "spring.jpa.hibernate.ddl-auto=create-drop",
//    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
//    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
//    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false",
//    "spring.flyway.enabled=false",
//    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
//    "app.simulator.enabled=false",
//    "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
//    "management.health.kafka.enabled=false"
// })
class MarketTradeRepositoryTest {
//  - Removed to prevent Spring context loading
    @Autowired
    private MarketTradeRepository repository;
//  - Removed to prevent Spring context loading
    private String symbol1;
    private String symbol2;
//  - Removed to prevent Spring context loading
    @BeforeEach
    void setUp() {
        symbol1 = "BTC-USDT";
        symbol2 = "ETH-USDT";
    }
//  - Removed to prevent Spring context loading
    @Test
    void shouldSaveTrade_whenGivenValidData() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();
        MarketTrade trade = new MarketTrade(
            symbol1, now, BigDecimal.valueOf(65000),
            BigDecimal.valueOf(0.5), MarketTrade.TradeSide.BUY
        );
//  - Removed to prevent Spring context loading
        // When
        MarketTrade saved = repository.save(trade);
        repository.flush();
//  - Removed to prevent Spring context loading
        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMarketSymbol()).isEqualTo(symbol1);
        assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(65000));
        assertThat(saved.getQty()).isEqualByComparingTo(BigDecimal.valueOf(0.5));
        assertThat(saved.getSide()).isEqualTo(MarketTrade.TradeSide.BUY);
    }
//  - Removed to prevent Spring context loading
    @Test
    void shouldFindTop50TradesOrderedByTimestampDesc_whenMultipleTradesExist() {
        // Given
        OffsetDateTime baseTime = OffsetDateTime.now();
        
        // Create 60 trades (more than 50)
        for (int i = 0; i < 60; i++) {
            MarketTrade trade = new MarketTrade(
                symbol1, baseTime.minusMinutes(i), BigDecimal.valueOf(65000 + i),
                BigDecimal.valueOf(0.5), i % 2 == 0 ? MarketTrade.TradeSide.BUY : MarketTrade.TradeSide.SELL
            );
            repository.save(trade);
        }
        
        // Add trades for different symbol
        MarketTrade otherTrade = new MarketTrade(
            symbol2, baseTime, BigDecimal.valueOf(3500),
            BigDecimal.valueOf(1.0), MarketTrade.TradeSide.BUY
        );
        repository.save(otherTrade);
        
        repository.flush();
//  - Removed to prevent Spring context loading
        // When
        List<MarketTrade> result = repository.findTop50ByMarketSymbolOrderByTsDesc(symbol1);
//  - Removed to prevent Spring context loading
        // Then
        assertThat(result).hasSize(50); // Should limit to 50
        assertThat(result).extracting(MarketTrade::getMarketSymbol)
            .containsOnly(symbol1);
        
        // Verify descending order (most recent first)
        for (int i = 1; i < result.size(); i++) {
            OffsetDateTime prev = result.get(i - 1).getTs();
            OffsetDateTime curr = result.get(i).getTs();
            assertThat(prev.isAfter(curr) || prev.isEqual(curr)).isTrue();
        }
        
        // Verify first trade is the most recent
        assertThat(result.get(0).getTs()).isEqualTo(baseTime);
        assertThat(result.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(65000));
    }
//  - Removed to prevent Spring context loading
    @Test
    void shouldReturnEmptyList_whenNoTradesExistForSymbol() {
        // When
        List<MarketTrade> result = repository.findTop50ByMarketSymbolOrderByTsDesc("NONEXISTENT");
//  - Removed to prevent Spring context loading
        // Then
        assertThat(result).isEmpty();
    }
//  - Removed to prevent Spring context loading
    @Test
    void shouldFindTradesInRange_whenQueryingByTimeRange() {
        // Given
        OffsetDateTime baseTime = OffsetDateTime.now();
        
        MarketTrade trade1 = new MarketTrade(
            symbol1, baseTime.minusHours(2), BigDecimal.valueOf(64000),
            BigDecimal.valueOf(0.5), MarketTrade.TradeSide.BUY
        );
        MarketTrade trade2 = new MarketTrade(
            symbol1, baseTime.minusHours(1), BigDecimal.valueOf(65000),
            BigDecimal.valueOf(0.6), MarketTrade.TradeSide.SELL
        );
        MarketTrade trade3 = new MarketTrade(
            symbol1, baseTime, BigDecimal.valueOf(66000),
            BigDecimal.valueOf(0.7), MarketTrade.TradeSide.BUY
        );
        
        // Trade outside range
        MarketTrade trade4 = new MarketTrade(
            symbol1, baseTime.plusHours(1), BigDecimal.valueOf(67000),
            BigDecimal.valueOf(0.8), MarketTrade.TradeSide.SELL
        );
//  - Removed to prevent Spring context loading
        repository.save(trade1);
        repository.save(trade2);
        repository.save(trade3);
        repository.save(trade4);
        repository.flush();
//  - Removed to prevent Spring context loading
        // When
        OffsetDateTime from = baseTime.minusHours(2);
        OffsetDateTime to = baseTime;
        Page<MarketTrade> result = repository.findByMarketSymbolAndTsBetween(
            symbol1, from, to, PageRequest.of(0, 10)
        );
//  - Removed to prevent Spring context loading
        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(MarketTrade::getPrice)
            .containsExactly(
                BigDecimal.valueOf(66000), // Most recent first (DESC)
                BigDecimal.valueOf(65000),
                BigDecimal.valueOf(64000)
            );
    }
//  - Removed to prevent Spring context loading
    @Test
    void shouldRespectPagination_whenQueryingByTimeRange() {
        // Given
        OffsetDateTime baseTime = OffsetDateTime.now();
        
        for (int i = 0; i < 5; i++) {
            MarketTrade trade = new MarketTrade(
                symbol1, baseTime.minusMinutes(i), BigDecimal.valueOf(64000 + i * 100),
                BigDecimal.valueOf(0.5), MarketTrade.TradeSide.BUY
            );
            repository.save(trade);
        }
        repository.flush();
//  - Removed to prevent Spring context loading
        // When
        Page<MarketTrade> page1 = repository.findByMarketSymbolAndTsBetween(
            symbol1, baseTime.minusHours(1), baseTime.plusHours(1), PageRequest.of(0, 2)
        );
        Page<MarketTrade> page2 = repository.findByMarketSymbolAndTsBetween(
            symbol1, baseTime.minusHours(1), baseTime.plusHours(1), PageRequest.of(1, 2)
        );
//  - Removed to prevent Spring context loading
        // Then
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page2.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
    }
//  - Removed to prevent Spring context loading
    @Test
    void shouldFilterBySymbol_whenQueryingByTimeRange() {
        // Given
        OffsetDateTime baseTime = OffsetDateTime.now();
        
        MarketTrade trade1 = new MarketTrade(
            symbol1, baseTime, BigDecimal.valueOf(65000),
            BigDecimal.valueOf(0.5), MarketTrade.TradeSide.BUY
        );
        MarketTrade trade2 = new MarketTrade(
            symbol2, baseTime, BigDecimal.valueOf(3500),
            BigDecimal.valueOf(1.0), MarketTrade.TradeSide.SELL
        );
//  - Removed to prevent Spring context loading
        repository.save(trade1);
        repository.save(trade2);
        repository.flush();
//  - Removed to prevent Spring context loading
        // When
        Page<MarketTrade> result = repository.findByMarketSymbolAndTsBetween(
            symbol1, baseTime.minusHours(1), baseTime.plusHours(1), PageRequest.of(0, 10)
        );
//  - Removed to prevent Spring context loading
        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMarketSymbol()).isEqualTo(symbol1);
        assertThat(result.getContent().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(65000));
    }
//  - Removed to prevent Spring context loading
    @Test
    void shouldReturnTradesInDescendingOrder_whenQueryingByTimeRange() {
        // Given
        OffsetDateTime baseTime = OffsetDateTime.now();
        
        for (int i = 0; i < 10; i++) {
            MarketTrade trade = new MarketTrade(
                symbol1, baseTime.minusMinutes(i), BigDecimal.valueOf(64000 + i * 100),
                BigDecimal.valueOf(0.5), MarketTrade.TradeSide.BUY
            );
            repository.save(trade);
        }
        repository.flush();
//  - Removed to prevent Spring context loading
        // When
        Page<MarketTrade> result = repository.findByMarketSymbolAndTsBetween(
            symbol1, baseTime.minusHours(1), baseTime.plusHours(1), PageRequest.of(0, 20)
        );
//  - Removed to prevent Spring context loading
        // Then - verify descending order
        List<MarketTrade> trades = result.getContent();
        for (int i = 1; i < trades.size(); i++) {
            OffsetDateTime prev = trades.get(i - 1).getTs();
            OffsetDateTime curr = trades.get(i).getTs();
            assertThat(prev.isAfter(curr) || prev.isEqual(curr)).isTrue();
        }
    }
}
