package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    @Operation(summary = "Get user balances", description = "Returns all balances for the authenticated user")
    public ResponseEntity<List<Balance>> getBalances(@RequestParam UUID userId) {
        List<Balance> balances = walletService.getBalances(userId);
        return ResponseEntity.ok(balances);
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds", description = "Deposits funds to user's wallet (simulation-friendly)")
    public ResponseEntity<Balance> deposit(@RequestParam UUID userId, @Valid @RequestBody DepositRequest request) {
        Balance balance = walletService.depositByCurrency(userId, request.currency, request.amount);
        return ResponseEntity.status(HttpStatus.CREATED).body(balance);
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds", description = "Withdraws funds from user's wallet")
    public ResponseEntity<Balance> withdraw(@RequestParam UUID userId, @Valid @RequestBody WithdrawRequest request) {
        Balance balance = walletService.withdrawByCurrency(userId, request.currency, request.amount);
        return ResponseEntity.ok(balance);
    }

    // DTOs
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
}
