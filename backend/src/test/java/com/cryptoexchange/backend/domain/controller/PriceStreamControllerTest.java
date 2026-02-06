package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.service.PriceBroadcastService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceStreamControllerTest {

    @Mock
    private PriceBroadcastService broadcastService;

    @InjectMocks
    private PriceStreamController controller;

    @Test
    void streamPrices_validSymbols_returnsEmitter() {
        SseEmitter mockEmitter = new SseEmitter();
        when(broadcastService.subscribe(any())).thenReturn(mockEmitter);

        SseEmitter result = controller.streamPrices("BTC,ETH,SOL");

        assertSame(mockEmitter, result);
        verify(broadcastService).subscribe(Set.of("BTC", "ETH", "SOL"));
    }

    @Test
    void streamPrices_emptySymbols_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.streamPrices(""));
    }

    @Test
    void streamPrices_tooManySymbols_throwsException() {
        String symbols = "A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U";
        assertThrows(IllegalArgumentException.class,
                () -> controller.streamPrices(symbols));
    }

    @Test
    void streamPrices_symbolsNormalized_uppercaseAndTrimmed() {
        SseEmitter mockEmitter = new SseEmitter();
        when(broadcastService.subscribe(any())).thenReturn(mockEmitter);

        controller.streamPrices(" btc , eth ");

        verify(broadcastService).subscribe(Set.of("BTC", "ETH"));
    }

    @Test
    void getStatus_returnsConnectionInfo() {
        when(broadcastService.getConnectionCount()).thenReturn(5);

        var response = controller.getStatus();

        assertNotNull(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(5, body.get("activeConnections"));
        assertEquals(PriceBroadcastService.MAX_CONNECTIONS, body.get("maxConnections"));
    }
}
