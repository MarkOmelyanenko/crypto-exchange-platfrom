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
    private WhiteBitService whiteBitService;

    private PriceBroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new PriceBroadcastService(whiteBitService);
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
        // No subscribers => should not call WhiteBit at all
        broadcastService.fetchAndBroadcast();

        verifyNoInteractions(whiteBitService);
    }

    @Test
    void fetchAndBroadcast_withSubscribers_fetchesBatch() {
        when(whiteBitService.toWhiteBitSymbol("BTC")).thenReturn("BTC_USDT");

        WhiteBitService.WhiteBitTicker24h ticker = new WhiteBitService.WhiteBitTicker24h();
        ticker.lastPrice = "67000.50";
        ticker.priceChangePercent = "2.15";

        when(whiteBitService.getBatchTicker24h(anyList())).thenReturn(Map.of("BTC_USDT", ticker));

        broadcastService.subscribe(Set.of("BTC"));
        broadcastService.fetchAndBroadcast();

        // Verify batch fetch was called
        verify(whiteBitService).getBatchTicker24h(anyList());

        // Verify price was cached
        Map<String, PriceBroadcastService.PriceEvent> prices = broadcastService.getLatestPrices();
        assertTrue(prices.containsKey("BTC"));
        assertEquals(new BigDecimal("67000.50"), prices.get("BTC").priceUsd);
    }

    @Test
    void fetchAndBroadcast_whiteBitFailure_doesNotThrow() {
        when(whiteBitService.toWhiteBitSymbol("BTC")).thenReturn("BTC_USDT");
        when(whiteBitService.getBatchTicker24h(anyList())).thenThrow(new RuntimeException("WhiteBit down"));

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
