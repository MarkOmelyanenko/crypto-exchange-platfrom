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

import java.math.RoundingMode;
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
        Page<Trade> trades = tradeRepository.findAllByPairId(marketId, pageable);
        Page<TradeResponse> response = trades.map(TradeResponse::from);
        return ResponseEntity.ok(response);
    }

    // DTO
    public static class TradeResponse {
        public UUID id;
        public UUID pairId;
        public String pairSymbol;
        public String side;
        public java.math.BigDecimal price;
        public java.math.BigDecimal baseQty;
        public java.math.BigDecimal quoteQty;
        public java.time.OffsetDateTime createdAt;

        public static TradeResponse from(Trade trade) {
            TradeResponse response = new TradeResponse();
            response.id = trade.getId();
            response.pairId = trade.getPair().getId();
            response.pairSymbol = trade.getPair().getSymbol();
            response.side = trade.getSide().name();
            response.price = trade.getPrice().stripTrailingZeros();
            response.baseQty = trade.getBaseQty().stripTrailingZeros();
            response.quoteQty = trade.getQuoteQty().setScale(2, RoundingMode.HALF_UP);
            response.createdAt = trade.getCreatedAt();
            return response;
        }
    }
}
