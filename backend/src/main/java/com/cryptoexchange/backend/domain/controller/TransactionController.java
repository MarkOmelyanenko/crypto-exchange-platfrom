package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.Trade;
import com.cryptoexchange.backend.domain.model.Transaction;
import com.cryptoexchange.backend.domain.repository.TradeRepository;
import com.cryptoexchange.backend.domain.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Transaction endpoints: create BUY/SELL, list with filters, get by ID.
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Transaction endpoints for buying and selling assets")
@Validated
public class TransactionController {

    private final TransactionService transactionService;
    private final TradeRepository tradeRepository;

    public TransactionController(TransactionService transactionService, TradeRepository tradeRepository) {
        this.transactionService = transactionService;
        this.tradeRepository = tradeRepository;
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
               description = "Returns paginated, merged view of all transactions and market-order trades " +
                             "for the authenticated user with optional filters")
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
        String normalizedSymbol = (symbol != null && !symbol.isBlank())
                ? symbol.trim().toUpperCase() : null;

        // ── 1) Fetch legacy Transaction records ──
        Page<Transaction> txPage = transactionService.listTransactions(
                userId, normalizedSymbol, sideEnum, from, to, sort, dir, 0, 10_000);

        List<TransactionDto> merged = new ArrayList<>();
        for (Transaction tx : txPage.getContent()) {
            merged.add(toDto(tx));
        }

        // ── 2) Fetch Trade records (market orders from Trading page) ──
        Page<Trade> tradePage = tradeRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt")));

        for (Trade t : tradePage.getContent()) {
            TransactionDto dto = tradeToDto(t);

            // Apply the same filters as for Transaction records
            if (normalizedSymbol != null) {
                boolean matchBase = dto.symbol.contains(normalizedSymbol);
                boolean matchPair = dto.pairSymbol != null && dto.pairSymbol.contains(normalizedSymbol);
                if (!matchBase && !matchPair) continue;
            }
            if (sideEnum != null && !dto.side.equals(sideEnum.name())) continue;
            if (from != null && dto.createdAt.isBefore(from)) continue;
            if (to != null && dto.createdAt.isAfter(to)) continue;

            merged.add(dto);
        }

        // ── 3) Sort merged list ──
        boolean ascending = "asc".equalsIgnoreCase(dir);
        Comparator<TransactionDto> cmp;
        if ("totalUsd".equalsIgnoreCase(sort)) {
            cmp = Comparator.comparing(d -> d.totalUsd);
        } else {
            cmp = Comparator.comparing(d -> d.createdAt);
        }
        if (!ascending) cmp = cmp.reversed();
        merged.sort(cmp);

        // ── 4) Manual pagination ──
        int totalItems = merged.size();
        int fromIdx = Math.min(page * size, totalItems);
        int toIdx = Math.min(fromIdx + size, totalItems);
        List<TransactionDto> pageItems = merged.subList(fromIdx, toIdx);
        int totalPages = (int) Math.ceil((double) totalItems / size);

        return ResponseEntity.ok(new PagedResponse(
                pageItems, totalItems, page, size, totalPages));
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
        dto.pairSymbol = tx.getAssetSymbol() + "/USDT";
        dto.quoteAsset = "USDT";
        dto.side = tx.getSide().name();
        dto.quantity = tx.getQuantity().stripTrailingZeros();
        dto.priceUsd = tx.getPriceUsd().setScale(2, RoundingMode.HALF_UP);
        dto.totalUsd = tx.getTotalUsd().setScale(2, RoundingMode.HALF_UP);
        dto.feeUsd = tx.getFeeUsd().setScale(2, RoundingMode.HALF_UP);
        dto.createdAt = tx.getCreatedAt();
        dto.source = "TRANSACTION";
        return dto;
    }

    private TransactionDto tradeToDto(Trade t) {
        TransactionDto dto = new TransactionDto();
        dto.id = t.getId();
        dto.symbol = t.getPair().getBaseAsset().getSymbol();
        dto.pairSymbol = t.getPair().getSymbol();
        dto.quoteAsset = t.getPair().getQuoteAsset().getSymbol();
        dto.side = t.getSide().name();
        dto.quantity = t.getBaseQty().stripTrailingZeros();
        dto.priceUsd = t.getPrice().stripTrailingZeros();
        dto.totalUsd = t.getQuoteQty().stripTrailingZeros();
        dto.feeUsd = BigDecimal.ZERO;
        dto.createdAt = t.getCreatedAt();
        dto.source = "TRADE";
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
        public String symbol;        // base asset symbol (e.g., "BTC", "ETH")
        public String pairSymbol;    // trading pair (e.g., "BTC/USDT", "ETH/BTC")
        public String quoteAsset;    // quote currency (e.g., "USDT", "BTC")
        public String side;
        public BigDecimal quantity;
        public BigDecimal priceUsd;  // price in quote currency (USD for USDT pairs)
        public BigDecimal totalUsd;  // total in quote currency
        public BigDecimal feeUsd;
        public OffsetDateTime createdAt;
        public String source;        // "TRANSACTION" or "TRADE"
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
