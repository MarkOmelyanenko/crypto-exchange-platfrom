package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.model.PriceTick;
import com.cryptoexchange.backend.domain.repository.MarketTickRepository;
import com.cryptoexchange.backend.domain.repository.PriceTickRepository;
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
    private final PriceTickRepository priceTickRepository;
    private final BinanceService binanceService;

    public PriceController(MarketTickRepository marketTickRepository,
                           PriceTickRepository priceTickRepository,
                           BinanceService binanceService) {
        this.marketTickRepository = marketTickRepository;
        this.priceTickRepository = priceTickRepository;
        this.binanceService = binanceService;
    }

    @GetMapping("/snapshot")
    @Operation(summary = "Get price snapshot", description = "Returns current prices for specified symbols from Binance")
    public ResponseEntity<List<PriceSnapshot>> getSnapshot(@RequestParam String symbols) {
        String[] symbolArray = symbols.split(",");
        List<PriceSnapshot> snapshots = new ArrayList<>();

        for (String symbol : symbolArray) {
            String assetSymbol = symbol.trim().toUpperCase();
            BigDecimal price = null;
            OffsetDateTime timestamp = OffsetDateTime.now();

            // 1) Try Binance API for real-time prices
            String binanceSymbol = binanceService.toBinanceSymbol(assetSymbol);
            price = binanceService.getCurrentPrice(binanceSymbol);

            if (price != null) {
                log.debug("Got price from Binance for {}: {}", assetSymbol, price);
            } else {
                // 2) Fallback to PriceTick DB (Binance-fetched ticks)
                Optional<PriceTick> latestPriceTick = priceTickRepository.findFirstBySymbolOrderByTsDesc(assetSymbol);
                if (latestPriceTick.isPresent()) {
                    price = latestPriceTick.get().getPriceUsd();
                    timestamp = latestPriceTick.get().getTs();
                    log.debug("Got price from PriceTick DB for {}: {}", assetSymbol, price);
                } else {
                    // 3) Fallback to MarketTick DB (simulator ticks)
                    String marketSymbol = assetSymbol + "/USDT";
                    Optional<MarketTick> latestTick = marketTickRepository.findFirstByMarketSymbolOrderByTsDesc(marketSymbol);
                    if (latestTick.isPresent()) {
                        price = latestTick.get().getLastPrice();
                        timestamp = latestTick.get().getTs();
                        log.debug("Got price from MarketTick DB for {}: {}", assetSymbol, price);
                    }
                }
            }

            // If no price source available, return null price (frontend handles it)
            snapshots.add(new PriceSnapshot(assetSymbol, price, timestamp));
        }

        return ResponseEntity.ok(snapshots);
    }

    @GetMapping("/history")
    @Operation(summary = "Get price history", description = "Returns Binance-driven price history for a symbol")
    public ResponseEntity<List<PriceHistoryPoint>> getHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "24h") String range) {

        String assetSymbol = symbol.trim().toUpperCase();
        String binanceSymbol = binanceService.toBinanceSymbol(assetSymbol);
        OffsetDateTime from = calculateFromTime(range);
        OffsetDateTime to = OffsetDateTime.now();

        List<PriceHistoryPoint> history = new ArrayList<>();

        // 1) Try Binance klines API for real price history
        String interval = getBinanceInterval(range);
        int limit = getBinanceLimit(range);

        List<BinanceService.PriceHistoryPoint> binanceHistory = binanceService.getKlines(
            binanceSymbol, interval, limit);

        if (!binanceHistory.isEmpty()) {
            history = binanceHistory.stream()
                .filter(point -> !point.timestamp.isBefore(from) && !point.timestamp.isAfter(to))
                .map(point -> new PriceHistoryPoint(point.timestamp, point.priceUsd))
                .toList();

            log.debug("Got {} price points from Binance for {}", history.size(), assetSymbol);
        }

        // 2) Fallback to PriceTick DB (Binance-fetched ticks stored by scheduled job)
        if (history.isEmpty()) {
            List<PriceTick> ticks = priceTickRepository.findBySymbolAndTsBetween(assetSymbol, from, to);
            if (!ticks.isEmpty()) {
                history = ticks.stream()
                    .map(tick -> new PriceHistoryPoint(tick.getTs(), tick.getPriceUsd()))
                    .toList();
                log.debug("Got {} price points from PriceTick DB for {}", history.size(), assetSymbol);
            }
        }

        // 3) Last fallback: MarketTick DB (simulator data)
        if (history.isEmpty()) {
            String marketSymbol = assetSymbol + "/USDT";
            var ticks = marketTickRepository.findByMarketSymbolAndTsBetween(
                marketSymbol, from, to, PageRequest.of(0, 1000));

            history = ticks.getContent().stream()
                .map(tick -> new PriceHistoryPoint(tick.getTs(), tick.getLastPrice()))
                .toList();

            log.debug("Got {} price points from MarketTick DB for {}", history.size(), assetSymbol);
        }

        // No mock/random data generation - all sources are Binance-driven
        return ResponseEntity.ok(history);
    }

    private String getBinanceInterval(String range) {
        return switch (range.toLowerCase()) {
            case "24h", "1d" -> "1h";
            case "7d", "1w" -> "4h";
            case "30d", "1m" -> "1d";
            default -> "1h";
        };
    }

    private int getBinanceLimit(String range) {
        return switch (range.toLowerCase()) {
            case "24h", "1d" -> 24;
            case "7d", "1w" -> 42;
            case "30d", "1m" -> 30;
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

    // DTOs
    public static class PriceSnapshot {
        public final String symbol;
        public final BigDecimal priceUsd;
        public final OffsetDateTime timestamp;

        public PriceSnapshot(String symbol, BigDecimal priceUsd, OffsetDateTime timestamp) {
            this.symbol = symbol;
            this.priceUsd = priceUsd;
            this.timestamp = timestamp;
        }
    }

    public static class PriceHistoryPoint {
        public final OffsetDateTime timestamp;
        public final BigDecimal priceUsd;

        public PriceHistoryPoint(OffsetDateTime timestamp, BigDecimal priceUsd) {
            this.timestamp = timestamp;
            this.priceUsd = priceUsd;
        }
    }
}
