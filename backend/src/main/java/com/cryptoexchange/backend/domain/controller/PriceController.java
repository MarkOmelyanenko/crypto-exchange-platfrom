package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.repository.MarketTickRepository;
import com.cryptoexchange.backend.domain.service.BinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/prices")
@Tag(name = "Prices", description = "Price endpoints")
public class PriceController {

    private static final Logger log = LoggerFactory.getLogger(PriceController.class);
    private final MarketTickRepository marketTickRepository;
    private final BinanceService binanceService;

    public PriceController(MarketTickRepository marketTickRepository, BinanceService binanceService) {
        this.marketTickRepository = marketTickRepository;
        this.binanceService = binanceService;
    }

    @GetMapping("/snapshot")
    @Operation(summary = "Get price snapshot", description = "Returns current prices for specified symbols")
    public ResponseEntity<List<PriceSnapshot>> getSnapshot(@RequestParam String symbols) {
        String[] symbolArray = symbols.split(",");
        List<PriceSnapshot> snapshots = new ArrayList<>();

        for (String symbol : symbolArray) {
            String assetSymbol = symbol.trim().toUpperCase();
            BigDecimal price = null;
            OffsetDateTime timestamp = OffsetDateTime.now();

            // Try Binance API first for real-time prices
            String binanceSymbol = binanceService.toBinanceSymbol(assetSymbol);
            price = binanceService.getCurrentPrice(binanceSymbol);
            
            if (price != null) {
                log.debug("Got price from Binance for {}: {}", assetSymbol, price);
            } else {
                // Fallback to database
                String marketSymbol = assetSymbol + "/USDT";
                Optional<MarketTick> latestTick = marketTickRepository.findFirstByMarketSymbolOrderByTsDesc(marketSymbol);
                
                if (latestTick.isPresent()) {
                    MarketTick tick = latestTick.get();
                    price = tick.getLastPrice();
                    timestamp = tick.getTs();
                    log.debug("Got price from database for {}: {}", assetSymbol, price);
                } else {
                    // Last resort: fallback price
                    price = getFallbackPrice(assetSymbol);
                    log.debug("Using fallback price for {}: {}", assetSymbol, price);
                }
            }

            snapshots.add(new PriceSnapshot(assetSymbol, price, timestamp));
        }

        return ResponseEntity.ok(snapshots);
    }

    @GetMapping("/history")
    @Operation(summary = "Get price history", description = "Returns price history for a symbol")
    public ResponseEntity<List<PriceHistoryPoint>> getHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "24h") String range) {
        
        String assetSymbol = symbol.trim().toUpperCase();
        String binanceSymbol = binanceService.toBinanceSymbol(assetSymbol);
        OffsetDateTime from = calculateFromTime(range);
        OffsetDateTime to = OffsetDateTime.now();
        
        List<PriceHistoryPoint> history = new ArrayList<>();

        // Try Binance API first for real price history
        String interval = getBinanceInterval(range);
        int limit = getBinanceLimit(range);
        
        List<BinanceService.PriceHistoryPoint> binanceHistory = binanceService.getKlines(
            binanceSymbol, interval, limit);
        
        if (!binanceHistory.isEmpty()) {
            // Filter by time range and convert to our format
            history = binanceHistory.stream()
                .filter(point -> !point.timestamp.isBefore(from) && !point.timestamp.isAfter(to))
                .map(point -> new PriceHistoryPoint(point.timestamp, point.priceUsd))
                .toList();
            
            log.debug("Got {} price points from Binance for {}", history.size(), assetSymbol);
        }

        // Fallback to database if Binance fails or returns empty
        if (history.isEmpty()) {
            String marketSymbol = assetSymbol + "/USDT";
            var ticks = marketTickRepository.findByMarketSymbolAndTsBetween(
                marketSymbol,
                from,
                to,
                PageRequest.of(0, 1000)
            );

            history = ticks.getContent().stream()
                .map(tick -> new PriceHistoryPoint(tick.getTs(), tick.getLastPrice()))
                .toList();
            
            log.debug("Got {} price points from database for {}", history.size(), assetSymbol);
        }

        // Last resort: generate mock data
        if (history.isEmpty()) {
            history = generateMockHistory(assetSymbol, from, to);
            log.debug("Generated {} mock price points for {}", history.size(), assetSymbol);
        }

        return ResponseEntity.ok(history);
    }

    private String getBinanceInterval(String range) {
        return switch (range.toLowerCase()) {
            case "24h", "1d" -> "1h";  // 1 hour intervals for 24h
            case "7d", "1w" -> "4h";   // 4 hour intervals for 7 days
            case "30d", "1m" -> "1d";  // 1 day intervals for 30 days
            default -> "1h";
        };
    }

    private int getBinanceLimit(String range) {
        return switch (range.toLowerCase()) {
            case "24h", "1d" -> 24;    // 24 hours
            case "7d", "1w" -> 42;     // 7 days * 6 (4h intervals) = 42
            case "30d", "1m" -> 30;    // 30 days
            default -> 24;
        };
    }

    private OffsetDateTime calculateFromTime(String range) {
        OffsetDateTime now = OffsetDateTime.now();
        return switch (range.toLowerCase()) {
            case "24h", "1d" -> now.minusHours(24);
            case "7d", "1w" -> now.minusDays(7);
            case "30d", "1m" -> now.minusDays(30);
            default -> now.minusHours(24);
        };
    }

    private java.math.BigDecimal getFallbackPrice(String assetSymbol) {
        return switch (assetSymbol.toUpperCase()) {
            case "BTC" -> new java.math.BigDecimal("65000");
            case "ETH" -> new java.math.BigDecimal("3500");
            case "SOL" -> new java.math.BigDecimal("150");
            default -> new java.math.BigDecimal("1000");
        };
    }

    private List<PriceHistoryPoint> generateMockHistory(String assetSymbol, OffsetDateTime from, OffsetDateTime to) {
        List<PriceHistoryPoint> history = new ArrayList<>();
        java.math.BigDecimal basePrice = getFallbackPrice(assetSymbol);
        long hours = java.time.Duration.between(from, to).toHours();
        hours = Math.max(1, Math.min(hours, 24)); // Limit to 24 hours max

        for (int i = 0; i <= hours; i++) {
            OffsetDateTime timestamp = from.plusHours(i);
            // Simple random walk for mock data
            double change = (Math.random() - 0.5) * 0.02; // ±1% change
            java.math.BigDecimal price = basePrice.multiply(java.math.BigDecimal.valueOf(1 + change));
            history.add(new PriceHistoryPoint(timestamp, price));
        }

        return history;
    }

    public static class PriceSnapshot {
        public final String symbol;
        public final java.math.BigDecimal priceUsd;
        public final OffsetDateTime timestamp;

        public PriceSnapshot(String symbol, java.math.BigDecimal priceUsd, OffsetDateTime timestamp) {
            this.symbol = symbol;
            this.priceUsd = priceUsd;
            this.timestamp = timestamp;
        }
    }

    public static class PriceHistoryPoint {
        public final OffsetDateTime timestamp;
        public final java.math.BigDecimal priceUsd;

        public PriceHistoryPoint(OffsetDateTime timestamp, java.math.BigDecimal priceUsd) {
            this.timestamp = timestamp;
            this.priceUsd = priceUsd;
        }
    }
}
