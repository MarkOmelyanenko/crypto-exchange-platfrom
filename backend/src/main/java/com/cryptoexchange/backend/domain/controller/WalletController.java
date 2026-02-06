package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.service.CashDepositService;
import com.cryptoexchange.backend.domain.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet", description = "Wallet and balance management endpoints")
@Validated
public class WalletController {

    private final WalletService walletService;
    private final CashDepositService cashDepositService;

    public WalletController(WalletService walletService, CashDepositService cashDepositService) {
        this.walletService = walletService;
        this.cashDepositService = cashDepositService;
    }

    @GetMapping
    @Operation(summary = "Get user balances", description = "Returns all asset balances for the authenticated user")
    public ResponseEntity<List<Balance>> getBalances(@RequestParam UUID userId) {
        List<Balance> balances = walletService.getBalances(userId);
        return ResponseEntity.ok(balances);
    }

    @GetMapping("/balances")
    @Operation(summary = "Get wallet balances", description = "Returns all asset balances for the authenticated user as a simple list")
    public ResponseEntity<List<BalanceDto>> getWalletBalances(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        List<Balance> balances = walletService.getBalances(userId);
        List<BalanceDto> dtos = balances.stream()
                .map(b -> new BalanceDto(b.getAsset().getSymbol(), b.getAvailable().stripTrailingZeros().toPlainString()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/balance")
    @Operation(summary = "Get cash balance", description = "Returns USD cash balance and 24h deposit limit info for the authenticated user")
    public ResponseEntity<CashBalanceResponse> getCashBalance(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        CashDepositService.CashBalanceInfo info = cashDepositService.getCashBalance(userId);
        return ResponseEntity.ok(new CashBalanceResponse(
                info.cashUsd(),
                info.depositLimit24h(),
                info.depositedLast24h(),
                info.remainingLimit24h()
        ));
    }

    @PostMapping("/cash-deposit")
    @Operation(summary = "Deposit USD cash", description = "Deposits USD to user account. Limited to 1,000 USDT per rolling 24-hour window.")
    public ResponseEntity<CashBalanceResponse> cashDeposit(Authentication authentication,
                                                           @Valid @RequestBody CashDepositRequest request) {
        UUID userId = extractUserId(authentication);
        CashDepositService.CashBalanceInfo info = cashDepositService.deposit(userId, request.amountUsd);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CashBalanceResponse(
                info.cashUsd(),
                info.depositLimit24h(),
                info.depositedLast24h(),
                info.remainingLimit24h()
        ));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit USDT", description = "Deposits USDT to user's wallet with 24h rolling limit. Request: {\"amount\":\"100.00\"}")
    public ResponseEntity<CashBalanceResponse> simpleDeposit(Authentication authentication,
                                                             @Valid @RequestBody SimpleDepositRequest request) {
        UUID userId = extractUserId(authentication);
        CashDepositService.CashBalanceInfo info = cashDepositService.deposit(userId, request.amount);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CashBalanceResponse(
                info.cashUsd(),
                info.depositLimit24h(),
                info.depositedLast24h(),
                info.remainingLimit24h()
        ));
    }

    @PostMapping("/deposit-by-currency")
    @Operation(summary = "Deposit funds by currency", description = "Deposits funds to user's wallet (simulation-friendly)")
    public ResponseEntity<Balance> depositByCurrency(@RequestParam UUID userId, @Valid @RequestBody DepositRequest request) {
        Balance balance = walletService.depositByCurrency(userId, request.currency, request.amount);
        return ResponseEntity.status(HttpStatus.CREATED).body(balance);
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds", description = "Withdraws funds from user's wallet")
    public ResponseEntity<Balance> withdraw(@RequestParam UUID userId, @Valid @RequestBody WithdrawRequest request) {
        Balance balance = walletService.withdrawByCurrency(userId, request.currency, request.amount);
        return ResponseEntity.ok(balance);
    }

    // ── Helper ──

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }

    // ── DTOs ──

    public static class CashDepositRequest {
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01 USDT")
        public BigDecimal amountUsd;
    }

    public static class CashBalanceResponse {
        public BigDecimal cashUsd;
        public BigDecimal depositLimit24h;
        public BigDecimal depositedLast24h;
        public BigDecimal remainingLimit24h;

        public CashBalanceResponse(BigDecimal cashUsd, BigDecimal depositLimit24h,
                                   BigDecimal depositedLast24h, BigDecimal remainingLimit24h) {
            this.cashUsd = cashUsd;
            this.depositLimit24h = depositLimit24h;
            this.depositedLast24h = depositedLast24h;
            this.remainingLimit24h = remainingLimit24h;
        }
    }

    public static class DepositRequest {
        @NotBlank(message = "Currency is required")
        public String currency;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.00000001", message = "Amount must be positive")
        public BigDecimal amount;
    }

    public static class WithdrawRequest {
        @NotBlank(message = "Currency is required")
        public String currency;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.00000001", message = "Amount must be positive")
        public BigDecimal amount;
    }

    /**
     * Simple DTO for wallet balances endpoint.
     */
    public record BalanceDto(String asset, String available) {}

    /**
     * Request body for the simplified deposit endpoint.
     */
    public static class SimpleDepositRequest {
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
        public BigDecimal amount;
    }
}
