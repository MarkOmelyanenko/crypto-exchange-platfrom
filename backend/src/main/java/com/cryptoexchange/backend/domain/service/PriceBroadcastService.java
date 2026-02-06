package com.cryptoexchange.backend.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared price broadcasting service.
 * Periodically fetches prices from WhiteBit (reusing WhiteBitService's cached batch fetch)
 * and pushes SSE events to all connected clients.
 *
 * Key design:
 * - ONE WhiteBit request per tick (shared across all clients)
 * - Clients register with a set of symbols (max 20)
 * - Heartbeat every ~15s to keep connections alive
 */
@Service
public class PriceBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcastService.class);
    public static final int MAX_SYMBOLS = 20;
    public static final int MAX_CONNECTIONS = 200;

    private final WhiteBitService whiteBitService;

    /** Currently cached prices (updated every tick) */
    private final ConcurrentHashMap<String, PriceEvent> latestPrices = new ConcurrentHashMap<>();

    /** All active SSE subscriptions */
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private long lastHeartbeatMs = System.currentTimeMillis();

    public PriceBroadcastService(WhiteBitService whiteBitService) {
        this.whiteBitService = whiteBitService;
    }

    /**
     * Register a new SSE emitter for the given symbols.
     */
    public SseEmitter subscribe(Set<String> symbols) {
        if (connectionCount.get() >= MAX_CONNECTIONS) {
            throw new IllegalStateException("Too many SSE connections");
        }

        // 60-second timeout; client will reconnect on timeout
        SseEmitter emitter = new SseEmitter(60_000L);
        Subscription sub = new Subscription(emitter, symbols);
        subscriptions.add(sub);
        connectionCount.incrementAndGet();

        // Cleanup on completion/timeout/error
        Runnable cleanup = () -> {
            subscriptions.remove(sub);
            connectionCount.decrementAndGet();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send current cached prices immediately so client doesn't wait for next tick
        sendCachedPrices(sub);

        return emitter;
    }

    /**
     * Scheduled: fetch prices from WhiteBit every 2 seconds and broadcast to all subscribers.
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 3000)
    public void fetchAndBroadcast() {
        if (subscriptions.isEmpty()) {
            return; // No clients connected — skip WhiteBit call
        }

        // Collect unique symbols across all subscriptions
        Set<String> allSymbols = new HashSet<>();
        for (Subscription sub : subscriptions) {
            allSymbols.addAll(sub.symbols);
        }

        if (allSymbols.isEmpty()) {
            return;
        }

        // Fetch using batch (single WhiteBit API call, cached 5s in WhiteBitService)
        List<String> whiteBitSymbols = allSymbols.stream()
                .map(whiteBitService::toWhiteBitSymbol)
                .toList();

        Map<String, WhiteBitService.WhiteBitTicker24h> tickers;
        try {
            tickers = whiteBitService.getBatchTicker24h(whiteBitSymbols);
        } catch (Exception e) {
            log.warn("Failed to fetch batch tickers for SSE broadcast: {}", e.getMessage());
            return;
        }

        // Build price events
        OffsetDateTime now = OffsetDateTime.now();
        for (String symbol : allSymbols) {
            String whiteBitSymbol = whiteBitService.toWhiteBitSymbol(symbol);
            WhiteBitService.WhiteBitTicker24h ticker = tickers.get(whiteBitSymbol.toUpperCase());
            if (ticker != null && ticker.lastPrice != null) {
                try {
                    BigDecimal price = new BigDecimal(ticker.lastPrice);
                    BigDecimal change24h = ticker.priceChangePercent != null
                            ? new BigDecimal(ticker.priceChangePercent)
                            : null;
                    PriceEvent event = new PriceEvent(symbol, price, change24h, now);
                    latestPrices.put(symbol, event);
                } catch (NumberFormatException e) {
                    log.debug("Invalid price format for {}: {}", symbol, ticker.lastPrice);
                }
            }
        }

        // Broadcast to each subscription
        broadcastToAll();

        // Heartbeat check (~every 15s)
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastHeartbeatMs >= 15_000) {
            sendHeartbeat();
            lastHeartbeatMs = nowMs;
        }
    }

    private void broadcastToAll() {
        List<Subscription> dead = new ArrayList<>();
        for (Subscription sub : subscriptions) {
            try {
                for (String symbol : sub.symbols) {
                    PriceEvent event = latestPrices.get(symbol);
                    if (event != null) {
                        sub.emitter.send(SseEmitter.event()
                                .name("price")
                                .data(event));
                    }
                }
            } catch (IOException e) {
                dead.add(sub);
            } catch (Exception e) {
                log.debug("Error sending SSE event: {}", e.getMessage());
                dead.add(sub);
            }
        }
        // Cleanup dead connections
        for (Subscription sub : dead) {
            try {
                sub.emitter.complete();
            } catch (Exception ignored) {
            }
            subscriptions.remove(sub);
            connectionCount.decrementAndGet();
        }
    }

    private void sendCachedPrices(Subscription sub) {
        try {
            for (String symbol : sub.symbols) {
                PriceEvent event = latestPrices.get(symbol);
                if (event != null) {
                    sub.emitter.send(SseEmitter.event()
                            .name("price")
                            .data(event));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send cached prices to new subscriber");
        }
    }

    private void sendHeartbeat() {
        List<Subscription> dead = new ArrayList<>();
        for (Subscription sub : subscriptions) {
            try {
                sub.emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(Map.of("ts", System.currentTimeMillis())));
            } catch (IOException e) {
                dead.add(sub);
            }
        }
        for (Subscription sub : dead) {
            try {
                sub.emitter.complete();
            } catch (Exception ignored) {
            }
            subscriptions.remove(sub);
            connectionCount.decrementAndGet();
        }
    }

    /** Get current connection count (for monitoring). */
    public int getConnectionCount() {
        return connectionCount.get();
    }

    /** Get a snapshot of current cached prices. For testing purposes. */
    public Map<String, PriceEvent> getLatestPrices() {
        return Collections.unmodifiableMap(latestPrices);
    }

    // ─── Inner classes ───

    public static class PriceEvent {
        public final String symbol;
        public final BigDecimal priceUsd;
        public final BigDecimal change24hPercent;
        public final OffsetDateTime ts;

        public PriceEvent(String symbol, BigDecimal priceUsd, BigDecimal change24hPercent, OffsetDateTime ts) {
            this.symbol = symbol;
            this.priceUsd = priceUsd;
            this.change24hPercent = change24hPercent;
            this.ts = ts;
        }
    }

    private static class Subscription {
        final SseEmitter emitter;
        final Set<String> symbols;

        Subscription(SseEmitter emitter, Set<String> symbols) {
            this.emitter = emitter;
            this.symbols = symbols;
        }
    }
}
