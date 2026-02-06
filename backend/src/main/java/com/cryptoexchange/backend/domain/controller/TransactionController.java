package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.Transaction;
import com.cryptoexchange.backend.domain.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
 * Transaction endpoints: create BUY/SELL, list with filters, get by ID.
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Transaction endpoints for buying and selling assets")
@Validated
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ─── Create Transaction ───

    @PostMapping
    @Operation(summary = "Create transaction", description = "Execute a BUY or SELL at the current market price")
    public ResponseEntity<TransactionDto> createTransaction(
            Authentication authentication,
            @Valid @RequestBody CreateTransactionRequest request) {

        UUID userId = extractUserId(authentication);
        OrderSide side = OrderSide.valueOf(request.side.trim().toUpperCase());

        Transaction tx = transactionService.execute(userId, request.symbol, side, request.quantity);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(tx));
    }

    // ─── List Transactions ───

    @GetMapping
    @Operation(summary = "List transactions",
               description = "Returns paginated transactions for the authenticated user with optional filters")
    public ResponseEntity<PagedResponse> listTransactions(
            Authentication authentication,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = extractUserId(authentication);

        OrderSide sideEnum = null;
        if (side != null && !side.isBlank()) {
            sideEnum = OrderSide.valueOf(side.trim().toUpperCase());
        }

        Page<Transaction> result = transactionService.listTransactions(
                userId, symbol, sideEnum, from, to, sort, dir, page, size);

        List<TransactionDto> items = result.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse(
                items, (int) result.getTotalElements(), result.getNumber(),
                result.getSize(), result.getTotalPages()));
    }

    // ─── Get Transaction by ID ───

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID", description = "Returns a single transaction")
    public ResponseEntity<TransactionDto> getTransaction(
            Authentication authentication,
            @PathVariable UUID id) {

        UUID userId = extractUserId(authentication);
        Transaction tx = transactionService.getTransaction(userId, id);
        return ResponseEntity.ok(toDto(tx));
    }

    // ─── Helpers ───

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }

    private TransactionDto toDto(Transaction tx) {
        TransactionDto dto = new TransactionDto();
        dto.id = tx.getId();
        dto.symbol = tx.getAssetSymbol();
        dto.side = tx.getSide().name();
        dto.quantity = tx.getQuantity().stripTrailingZeros();
        dto.priceUsd = tx.getPriceUsd().setScale(2, RoundingMode.HALF_UP);
        dto.totalUsd = tx.getTotalUsd().setScale(2, RoundingMode.HALF_UP);
        dto.feeUsd = tx.getFeeUsd().setScale(2, RoundingMode.HALF_UP);
        dto.createdAt = tx.getCreatedAt();
        return dto;
    }

    // ─── DTOs ───

    public static class CreateTransactionRequest {
        @NotBlank(message = "Symbol is required")
        public String symbol;

        @NotBlank(message = "Side is required (BUY or SELL)")
        public String side;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.00000001", message = "Quantity must be positive")
        public BigDecimal quantity;
    }

    public static class TransactionDto {
        public UUID id;
        public String symbol;
        public String side;
        public BigDecimal quantity;
        public BigDecimal priceUsd;
        public BigDecimal totalUsd;
        public BigDecimal feeUsd;
        public OffsetDateTime createdAt;
    }

    public static class PagedResponse {
        public final List<TransactionDto> items;
        public final int total;
        public final int page;
        public final int size;
        public final int totalPages;

        public PagedResponse(List<TransactionDto> items, int total, int page, int size, int totalPages) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = totalPages;
        }
    }
}
