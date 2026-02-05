package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Trade;
import com.cryptoexchange.backend.domain.repository.TradeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/trades")
@Tag(name = "Trades", description = "Trade history endpoints")
@Validated
public class TradeController {

    private final TradeRepository tradeRepository;

    public TradeController(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @GetMapping
    @Operation(summary = "List trades", description = "Returns paginated list of trades for a market")
    public ResponseEntity<Page<TradeResponse>> listTrades(
            @RequestParam @NotNull(message = "Market ID is required") UUID marketId,
            Pageable pageable) {
        Page<Trade> trades = tradeRepository.findAllByMarketId(marketId, pageable);
        Page<TradeResponse> response = trades.map(TradeResponse::from);
        return ResponseEntity.ok(response);
    }

    // DTO
    public static class TradeResponse {
        public UUID id;
        public UUID marketId;
        public String marketSymbol;
        public UUID makerOrderId;
        public UUID takerOrderId;
        public java.math.BigDecimal price;
        public java.math.BigDecimal quantity;
        public java.math.BigDecimal quoteAmount;
        public java.time.OffsetDateTime executedAt;

        public static TradeResponse from(Trade trade) {
            TradeResponse response = new TradeResponse();
            response.id = trade.getId();
            response.marketId = trade.getMarket().getId();
            response.marketSymbol = trade.getMarket().getSymbol();
            response.makerOrderId = trade.getMakerOrder().getId();
            response.takerOrderId = trade.getTakerOrder().getId();
            response.price = trade.getPrice();
            response.quantity = trade.getAmount();
            response.quoteAmount = trade.getQuoteAmount();
            response.executedAt = trade.getExecutedAt();
            return response;
        }
    }
}
