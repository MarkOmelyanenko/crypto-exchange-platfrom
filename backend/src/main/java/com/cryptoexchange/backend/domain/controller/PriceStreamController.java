package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.service.PriceBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SSE endpoint for real-time price streaming.
 * Public (no JWT required) — prices are public market data.
 */
@RestController
@RequestMapping("/api/stream")
@Tag(name = "Price Stream", description = "Real-time price streaming via SSE")
public class PriceStreamController {

    private static final Logger log = LoggerFactory.getLogger(PriceStreamController.class);

    private final PriceBroadcastService broadcastService;

    public PriceStreamController(PriceBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    /**
     * Subscribe to real-time price updates for given symbols.
     *
     * Example: GET /api/stream/prices?symbols=BTC,ETH,SOL
     *
     * SSE events:
     *   event: price
     *   data: {"symbol":"BTC","priceUsd":67123.45,"change24hPercent":2.31,"ts":"2026-02-06T..."}
     *
     *   event: heartbeat
     *   data: {"ts":1738857600000}
     */
    @GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live price stream",
               description = "Returns an SSE stream of price updates for the specified symbols. " +
                             "Max " + PriceBroadcastService.MAX_SYMBOLS + " symbols per connection.")
    public SseEmitter streamPrices(@RequestParam String symbols) {
        // Parse and validate symbols
        Set<String> symbolSet = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (symbolSet.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol is required");
        }

        if (symbolSet.size() > PriceBroadcastService.MAX_SYMBOLS) {
            throw new IllegalArgumentException("Maximum " + PriceBroadcastService.MAX_SYMBOLS + " symbols allowed per stream");
        }

        log.debug("New SSE price stream subscription for: {}", symbolSet);
        return broadcastService.subscribe(symbolSet);
    }

    /**
     * Get current stream status (for monitoring/debugging).
     */
    @GetMapping("/status")
    @Operation(summary = "Get stream status", description = "Returns current SSE connection count")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "activeConnections", broadcastService.getConnectionCount(),
                "maxConnections", PriceBroadcastService.MAX_CONNECTIONS
        ));
    }
}
