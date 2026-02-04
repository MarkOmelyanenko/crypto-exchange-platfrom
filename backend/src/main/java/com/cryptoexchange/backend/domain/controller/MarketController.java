package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.model.MarketTrade;
import com.cryptoexchange.backend.domain.repository.MarketTickRepository;
import com.cryptoexchange.backend.domain.repository.MarketTradeRepository;
import com.cryptoexchange.backend.domain.service.MarketService;
import com.cryptoexchange.backend.domain.service.MarketSimulatorRedisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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
public class MarketController {

    private final MarketService marketService;
    private final MarketSimulatorRedisService redisService;
    private final MarketTickRepository marketTickRepository;
    private final MarketTradeRepository marketTradeRepository;

    public MarketController(
            MarketService marketService,
            MarketSimulatorRedisService redisService,
            MarketTickRepository marketTickRepository,
            MarketTradeRepository marketTradeRepository) {
        this.marketService = marketService;
        this.redisService = redisService;
        this.marketTickRepository = marketTickRepository;
        this.marketTradeRepository = marketTradeRepository;
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
        // Try Redis first
        MarketSimulatorRedisService.TickerSnapshot snapshot = redisService.getTicker(symbol);
        
        if (snapshot != null) {
            return ResponseEntity.ok(TickerResponse.fromSnapshot(snapshot));
        }
        
        // Fallback to DB
        MarketTick latestTick = marketTickRepository.findFirstByMarketSymbolOrderByTsDesc(symbol)
            .orElseThrow(() -> new com.cryptoexchange.backend.domain.exception.NotFoundException(
                "No ticker data found for market: " + symbol));
        
        return ResponseEntity.ok(TickerResponse.fromTick(latestTick));
    }

    @GetMapping("/{symbol}/trades")
    @Operation(summary = "Get recent trades", description = "Returns recent trades for a market")
    public ResponseEntity<List<TradeResponse>> getTrades(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "50") int limit) {
        
        // Try Redis first
        List<MarketSimulatorRedisService.TradeSnapshot> snapshots = redisService.getRecentTrades(symbol, limit);
        
        if (!snapshots.isEmpty()) {
            List<TradeResponse> response = snapshots.stream()
                .map(TradeResponse::fromSnapshot)
                .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        }
        
        // Fallback to DB
        List<MarketTrade> trades = marketTradeRepository.findTop50ByMarketSymbolOrderByTsDesc(symbol);
        List<TradeResponse> response = trades.stream()
            .limit(limit)
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        
        if (from == null) {
            from = OffsetDateTime.now().minusDays(1);
        }
        if (to == null) {
            to = OffsetDateTime.now();
        }
        
        Page<MarketTick> ticks = marketTickRepository.findByMarketSymbolAndTsBetween(symbol, from, to, pageable);
        Page<TickerResponse> response = ticks.map(TickerResponse::fromTick);
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

        public static TickerResponse fromSnapshot(MarketSimulatorRedisService.TickerSnapshot snapshot) {
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

        public static TradeResponse fromSnapshot(MarketSimulatorRedisService.TradeSnapshot snapshot) {
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
