package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.Trade;
import com.cryptoexchange.backend.domain.service.MarketOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for spot market order execution.
 * 
 * <p>Provides endpoints for executing immediate market orders (BUY/SELL) at current market prices.
 * Market orders execute immediately and do not require a price specification.
 * 
 * <p>For BUY orders, provide {@code quoteAmount} (amount of quote currency to spend).
 * For SELL orders, provide {@code baseAmount} (amount of base currency to sell).
 * 
 * <p>All endpoints require authentication and extract user ID from JWT token.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Market Orders", description = "Spot market order endpoints")
@Validated
public class MarketOrderController {

    private final MarketOrderService marketOrderService;

    public MarketOrderController(MarketOrderService marketOrderService) {
        this.marketOrderService = marketOrderService;
    }

    @PostMapping("/market")
    @Operation(summary = "Execute market order",
               description = "Execute a spot market order. BUY: provide quoteAmount (USDT to spend). SELL: provide baseAmount (asset to sell).")
    public ResponseEntity<TradeDto> executeMarketOrder(
            Authentication authentication,
            @Valid @RequestBody MarketOrderRequest request) {

        UUID userId = extractUserId(authentication);
        OrderSide side = OrderSide.valueOf(request.side.trim().toUpperCase());

        Trade trade;
        if (side == OrderSide.BUY) {
            if (request.quoteAmount == null || request.quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("quoteAmount is required and must be positive for BUY orders");
            }
            trade = marketOrderService.executeBuy(userId, request.pairId, request.quoteAmount);
        } else {
            if (request.baseAmount == null || request.baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("baseAmount is required and must be positive for SELL orders");
            }
            trade = marketOrderService.executeSell(userId, request.pairId, request.baseAmount);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(TradeDto.from(trade));
    }

    @GetMapping("/trades")
    @Operation(summary = "List user trades", description = "Returns recent trades for the authenticated user")
    public ResponseEntity<TradePageResponse> listTrades(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = extractUserId(authentication);
        Page<Trade> trades = marketOrderService.listUserTrades(userId, page, size);

        List<TradeDto> items = trades.getContent().stream()
                .map(TradeDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new TradePageResponse(
                items, (int) trades.getTotalElements(),
                trades.getNumber(), trades.getSize(), trades.getTotalPages()));
    }

    // ── Helper ──

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }

    // ── DTOs ──

    public static class MarketOrderRequest {
        @NotNull(message = "pairId is required")
        public UUID pairId;

        @NotNull(message = "side is required (BUY or SELL)")
        public String side;

        @DecimalMin(value = "0.00000001", message = "quoteAmount must be positive")
        public BigDecimal quoteAmount;

        @DecimalMin(value = "0.00000001", message = "baseAmount must be positive")
        public BigDecimal baseAmount;
    }

    public static class TradeDto {
        public UUID id;
        public UUID pairId;
        public String pairSymbol;
        public String baseAsset;
        public String quoteAsset;
        public String side;
        public BigDecimal price;
        public BigDecimal baseQty;
        public BigDecimal quoteQty;
        public OffsetDateTime createdAt;

        public static TradeDto from(Trade t) {
            TradeDto dto = new TradeDto();
            dto.id = t.getId();
            dto.pairId = t.getPair().getId();
            dto.pairSymbol = t.getPair().getSymbol();
            dto.baseAsset = t.getPair().getBaseAsset().getSymbol();
            dto.quoteAsset = t.getPair().getQuoteAsset().getSymbol();
            dto.side = t.getSide().name();
            dto.price = t.getPrice().stripTrailingZeros();
            dto.baseQty = t.getBaseQty().stripTrailingZeros();
            dto.quoteQty = t.getQuoteQty().setScale(2, RoundingMode.HALF_UP);
            dto.createdAt = t.getCreatedAt();
            return dto;
        }
    }

    public static class TradePageResponse {
        public final List<TradeDto> items;
        public final int total;
        public final int page;
        public final int size;
        public final int totalPages;

        public TradePageResponse(List<TradeDto> items, int total, int page, int size, int totalPages) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = totalPages;
        }
    }
}
