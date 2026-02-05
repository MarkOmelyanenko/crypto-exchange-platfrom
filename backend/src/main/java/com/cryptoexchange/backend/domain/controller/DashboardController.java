package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Dashboard endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary", description = "Returns portfolio summary with total value, cash, and PnL")
    public ResponseEntity<DashboardService.DashboardSummary> getSummary(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        DashboardService.DashboardSummary summary = dashboardService.getSummary(userId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/holdings")
    @Operation(summary = "Get user holdings", description = "Returns list of user's asset holdings with current prices and PnL")
    public ResponseEntity<List<DashboardService.Holding>> getHoldings(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        List<DashboardService.Holding> holdings = dashboardService.getHoldings(userId);
        return ResponseEntity.ok(holdings);
    }

    @GetMapping("/recent-transactions")
    @Operation(summary = "Get recent transactions", description = "Returns recent user transactions")
    public ResponseEntity<List<DashboardService.TransactionSummary>> getRecentTransactions(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        List<DashboardService.TransactionSummary> transactions = dashboardService.getRecentTransactions(userId, limit);
        return ResponseEntity.ok(transactions);
    }
}
