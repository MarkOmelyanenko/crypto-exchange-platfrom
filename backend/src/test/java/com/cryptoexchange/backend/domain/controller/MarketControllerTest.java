package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.model.MarketTrade;
import com.cryptoexchange.backend.domain.service.BinanceService;
import com.cryptoexchange.backend.domain.service.MarketService;
import com.cryptoexchange.backend.domain.service.MarketSnapshotStore;
import com.cryptoexchange.backend.domain.service.MarketTickStore;
import com.cryptoexchange.backend.domain.service.MarketTradeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for MarketController using mocks.
 * No Spring context, no database, no external dependencies.
 */
@ExtendWith(MockitoExtension.class)
class MarketControllerTest {

    @Mock
    private MarketService marketService;
    
    @Mock
    private MarketSnapshotStore snapshotStore;
    
    @Mock
    private MarketTickStore tickStore;
    
    @Mock
    private MarketTradeStore tradeStore;

    @Mock
    private BinanceService binanceService;

    private MarketController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketController(marketService, snapshotStore, tickStore, tradeStore, binanceService);
    }

    @Test
    void shouldReturnTicker_whenGetTicker() {
        // Given
        String symbol = "BTC-USDT";
        MarketSnapshotStore.TickerSnapshot snapshot = new MarketSnapshotStore.TickerSnapshot(
            OffsetDateTime.now(),
            BigDecimal.valueOf(65000),
            BigDecimal.valueOf(64990),
            BigDecimal.valueOf(65010),
            BigDecimal.valueOf(1000)
        );
        
        when(snapshotStore.getTicker(symbol)).thenReturn(snapshot);

        // When
        var response = controller.getTicker(symbol);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().last).isEqualByComparingTo(BigDecimal.valueOf(65000));
        assertThat(response.getBody().bid).isEqualByComparingTo(BigDecimal.valueOf(64990));
        assertThat(response.getBody().ask).isEqualByComparingTo(BigDecimal.valueOf(65010));
        assertThat(response.getBody().volume).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void shouldReturnTickerFromDB_whenSnapshotStoreReturnsNull() {
        // Given
        String symbol = "BTC-USDT";
        MarketTick tick = new MarketTick(
            symbol,
            OffsetDateTime.now(),
            BigDecimal.valueOf(65000),
            BigDecimal.valueOf(64990),
            BigDecimal.valueOf(65010),
            BigDecimal.valueOf(1000)
        );
        
        when(snapshotStore.getTicker(symbol)).thenReturn(null);
        when(tickStore.findLatest(symbol)).thenReturn(tick);

        // When
        var response = controller.getTicker(symbol);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().last).isEqualByComparingTo(BigDecimal.valueOf(65000));
    }

    @Test
    void shouldThrow404_whenTickerNotFound() {
        // Given
        String symbol = "NONEXISTENT-USDT";
        when(snapshotStore.getTicker(symbol)).thenReturn(null);
        when(tickStore.findLatest(symbol)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.getTicker(symbol))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("No ticker data found for market: " + symbol);
    }

    @Test
    void shouldReturnTrades_whenGetTrades() {
        // Given
        String symbol = "BTC-USDT";
        MarketSnapshotStore.TradeSnapshot snapshot1 = new MarketSnapshotStore.TradeSnapshot(
            OffsetDateTime.now().minusMinutes(1),
            BigDecimal.valueOf(65000),
            BigDecimal.valueOf(0.5),
            "BUY"
        );
        MarketSnapshotStore.TradeSnapshot snapshot2 = new MarketSnapshotStore.TradeSnapshot(
            OffsetDateTime.now(),
            BigDecimal.valueOf(65010),
            BigDecimal.valueOf(0.6),
            "SELL"
        );
        
        when(snapshotStore.getRecentTrades(symbol, 10)).thenReturn(List.of(snapshot1, snapshot2));

        // When
        var response = controller.getTrades(symbol, 10);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).price).isEqualByComparingTo(BigDecimal.valueOf(65000));
        assertThat(response.getBody().get(0).qty).isEqualByComparingTo(BigDecimal.valueOf(0.5));
        assertThat(response.getBody().get(0).side).isEqualTo("BUY");
    }

    @Test
    void shouldReturnTradesFromDB_whenSnapshotStoreReturnsEmpty() {
        // Given
        String symbol = "BTC-USDT";
        MarketTrade trade1 = new MarketTrade(
            symbol,
            OffsetDateTime.now().minusMinutes(1),
            BigDecimal.valueOf(65000),
            BigDecimal.valueOf(0.5),
            MarketTrade.TradeSide.BUY
        );
        MarketTrade trade2 = new MarketTrade(
            symbol,
            OffsetDateTime.now(),
            BigDecimal.valueOf(65010),
            BigDecimal.valueOf(0.6),
            MarketTrade.TradeSide.SELL
        );
        
        when(snapshotStore.getRecentTrades(symbol, 10)).thenReturn(Collections.emptyList());
        when(tradeStore.findRecent(symbol, 10)).thenReturn(List.of(trade1, trade2));

        // When
        var response = controller.getTrades(symbol, 10);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).price).isEqualByComparingTo(BigDecimal.valueOf(65000));
    }

    @Test
    void shouldRespectLimit_whenGetTrades() {
        // Given
        String symbol = "BTC-USDT";
        when(snapshotStore.getRecentTrades(symbol, 2)).thenReturn(Collections.emptyList());
        when(tradeStore.findRecent(symbol, 2)).thenReturn(Collections.emptyList());

        // When
        var response = controller.getTrades(symbol, 2);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        verify(snapshotStore).getRecentTrades(symbol, 2);
    }

    @Test
    void shouldUseDefaultLimit_whenLimitNotProvided() {
        // Given
        String symbol = "BTC-USDT";
        when(snapshotStore.getRecentTrades(symbol, 50)).thenReturn(Collections.emptyList());
        when(tradeStore.findRecent(symbol, 50)).thenReturn(Collections.emptyList());

        // When
        var response = controller.getTrades(symbol, 50);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        verify(snapshotStore).getRecentTrades(symbol, 50);
    }
}
