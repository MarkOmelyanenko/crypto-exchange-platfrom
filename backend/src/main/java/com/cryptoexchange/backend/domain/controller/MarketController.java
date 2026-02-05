package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.model.MarketTrade;
import com.cryptoexchange.backend.domain.service.MarketService;
import com.cryptoexchange.backend.domain.service.MarketTickStore;
import com.cryptoexchange.backend.domain.service.MarketTradeStore;
import com.cryptoexchange.backend.domain.service.MarketSnapshotStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Public REST endpoints for market data (ticker, trades, etc.).
 */
@RestController
@RequestMapping("/api/markets")
@Tag(name = "Markets", description = "Public market data endpoints")
@Validated
public class MarketController {

    private final MarketService marketService;
    private final MarketSnapshotStore snapshotStore;
    private final MarketTickStore tickStore;
    private final MarketTradeStore tradeStore;

    public MarketController(
            MarketService marketService,
            MarketSnapshotStore snapshotStore,
            MarketTickStore tickStore,
            MarketTradeStore tradeStore) {
        this.marketService = marketService;
        this.snapshotStore = snapshotStore;
        this.tickStore = tickStore;
        this.tradeStore = tradeStore;
    }

    @GetMapping
    @Operation(summary = "List active markets", description = "Returns list of active markets")
    public ResponseEntity<List<MarketResponse>> listMarkets() {
        List<Market> markets = marketService.listActiveMarkets();
        List<MarketResponse> response = markets.stream()
            .map(MarketResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/ticker")
    @Operation(summary = "Get market ticker", description = "Returns latest ticker data for a market")
    public ResponseEntity<TickerResponse> getTicker(@PathVariable String symbol) {
        // Try snapshot store first
        MarketSnapshotStore.TickerSnapshot snapshot = snapshotStore.getTicker(symbol);
        
        if (snapshot != null) {
            return ResponseEntity.ok(TickerResponse.fromSnapshot(snapshot));
        }
        
        // Fallback to DB
        MarketTick latestTick = tickStore.findLatest(symbol);
        if (latestTick == null) {
            throw new com.cryptoexchange.backend.domain.exception.NotFoundException(
                "No ticker data found for market: " + symbol);
        }
        
        return ResponseEntity.ok(TickerResponse.fromTick(latestTick));
    }

    @GetMapping("/{symbol}/trades")
    @Operation(summary = "Get recent trades", description = "Returns recent trades for a market")
    public ResponseEntity<List<TradeResponse>> getTrades(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "50") @jakarta.validation.constraints.Min(value = 1, message = "Limit must be at least 1") @jakarta.validation.constraints.Max(value = 1000, message = "Limit must be at most 1000") int limit) {
        
        // Try snapshot store first
        List<MarketSnapshotStore.TradeSnapshot> snapshots = snapshotStore.getRecentTrades(symbol, limit);
        
        if (!snapshots.isEmpty()) {
            List<TradeResponse> response = snapshots.stream()
                .map(TradeResponse::fromSnapshot)
                .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        }
        
        // Fallback to DB
        List<MarketTrade> trades = tradeStore.findRecent(symbol, limit);
        List<TradeResponse> response = trades.stream()
            .map(TradeResponse::fromTrade)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/ticks")
    @Operation(summary = "Get historical ticks", description = "Returns historical tick data for a market")
    public ResponseEntity<Page<TickerResponse>> getTicks(
            @PathVariable String symbol,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(value = 0, message = "Page must be non-negative") int page,
            @RequestParam(defaultValue = "100") @jakarta.validation.constraints.Min(value = 1, message = "Size must be at least 1") @jakarta.validation.constraints.Max(value = 1000, message = "Size must be at most 1000") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        
        if (from == null) {
            from = OffsetDateTime.now().minusDays(1);
        }
        if (to == null) {
            to = OffsetDateTime.now();
        }
        
        List<MarketTick> ticks = tickStore.findRange(symbol, from, to, page, size);
        // Convert to Page manually (simplified - in production you'd want proper pagination)
        Page<TickerResponse> response = new org.springframework.data.domain.PageImpl<>(
            ticks.stream().map(TickerResponse::fromTick).collect(Collectors.toList()),
            pageable,
            ticks.size()
        );
        return ResponseEntity.ok(response);
    }

    // DTOs
    public static class MarketResponse {
        public String symbol;
        public String baseAssetSymbol;
        public String quoteAssetSymbol;
        public Boolean active;

        public static MarketResponse from(Market market) {
            MarketResponse response = new MarketResponse();
            response.symbol = market.getSymbol();
            response.baseAssetSymbol = market.getBaseAsset().getSymbol();
            response.quoteAssetSymbol = market.getQuoteAsset().getSymbol();
            response.active = market.getActive();
            return response;
        }
    }

    public static class TickerResponse {
        public String marketSymbol;
        public OffsetDateTime ts;
        public java.math.BigDecimal last;
        public java.math.BigDecimal bid;
        public java.math.BigDecimal ask;
        public java.math.BigDecimal volume;

        public static TickerResponse fromSnapshot(MarketSnapshotStore.TickerSnapshot snapshot) {
            TickerResponse response = new TickerResponse();
            response.ts = snapshot.ts;
            response.last = snapshot.last;
            response.bid = snapshot.bid;
            response.ask = snapshot.ask;
            response.volume = snapshot.volume;
            return response;
        }

        public static TickerResponse fromTick(MarketTick tick) {
            TickerResponse response = new TickerResponse();
            response.marketSymbol = tick.getMarketSymbol();
            response.ts = tick.getTs();
            response.last = tick.getLastPrice();
            response.bid = tick.getBid();
            response.ask = tick.getAsk();
            response.volume = tick.getVolume();
            return response;
        }
    }

    public static class TradeResponse {
        public String marketSymbol;
        public OffsetDateTime ts;
        public java.math.BigDecimal price;
        public java.math.BigDecimal qty;
        public String side;

        public static TradeResponse fromSnapshot(MarketSnapshotStore.TradeSnapshot snapshot) {
            TradeResponse response = new TradeResponse();
            response.ts = snapshot.ts;
            response.price = snapshot.price;
            response.qty = snapshot.qty;
            response.side = snapshot.side;
            return response;
        }

        public static TradeResponse fromTrade(MarketTrade trade) {
            TradeResponse response = new TradeResponse();
            response.marketSymbol = trade.getMarketSymbol();
            response.ts = trade.getTs();
            response.price = trade.getPrice();
            response.qty = trade.getQty();
            response.side = trade.getSide().name();
            return response;
        }
    }
}
