package com.cryptoexchange.backend.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceBroadcastServiceTest {

    @Mock
    private BinanceService binanceService;

    private PriceBroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new PriceBroadcastService(binanceService);
    }

    @Test
    void subscribe_incrementsConnectionCount() {
        SseEmitter emitter = broadcastService.subscribe(Set.of("BTC"));

        assertNotNull(emitter);
        assertEquals(1, broadcastService.getConnectionCount());
    }

    @Test
    void subscribe_multipleClients_tracksAll() {
        broadcastService.subscribe(Set.of("BTC"));
        broadcastService.subscribe(Set.of("ETH"));
        broadcastService.subscribe(Set.of("SOL"));

        assertEquals(3, broadcastService.getConnectionCount());
    }

    @Test
    void fetchAndBroadcast_noSubscribers_skips() {
        // No subscribers => should not call Binance at all
        broadcastService.fetchAndBroadcast();

        verifyNoInteractions(binanceService);
    }

    @Test
    void fetchAndBroadcast_withSubscribers_fetchesBatch() {
        when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");

        BinanceService.BinanceTicker24h ticker = new BinanceService.BinanceTicker24h();
        ticker.symbol = "BTCUSDT";
        ticker.lastPrice = "67000.50";
        ticker.priceChangePercent = "2.15";

        when(binanceService.getBatchTicker24h(anyList())).thenReturn(Map.of("BTCUSDT", ticker));

        broadcastService.subscribe(Set.of("BTC"));
        broadcastService.fetchAndBroadcast();

        // Verify batch fetch was called
        verify(binanceService).getBatchTicker24h(anyList());

        // Verify price was cached
        Map<String, PriceBroadcastService.PriceEvent> prices = broadcastService.getLatestPrices();
        assertTrue(prices.containsKey("BTC"));
        assertEquals(new BigDecimal("67000.50"), prices.get("BTC").priceUsd);
    }

    @Test
    void fetchAndBroadcast_binanceFailure_doesNotThrow() {
        when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
        when(binanceService.getBatchTicker24h(anyList())).thenThrow(new RuntimeException("Binance down"));

        broadcastService.subscribe(Set.of("BTC"));

        // Should not throw
        assertDoesNotThrow(() -> broadcastService.fetchAndBroadcast());
    }

    @Test
    void subscribe_respectsMaxConnections() {
        // Fill up to max
        for (int i = 0; i < PriceBroadcastService.MAX_CONNECTIONS; i++) {
            broadcastService.subscribe(Set.of("BTC"));
        }

        // Next subscription should throw
        assertThrows(IllegalStateException.class,
                () -> broadcastService.subscribe(Set.of("BTC")));
    }
}
