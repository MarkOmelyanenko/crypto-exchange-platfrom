package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.service.MarketSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints for controlling the market simulator.
 * 
 * <p>Provides endpoints to start, stop, and query the status of the market price simulator.
 * Only available when {@code app.simulator.enabled=true}.
 * 
 * <p><b>Security Note:</b> These endpoints should be protected with authentication/authorization
 * in production environments. Currently accessible without authentication for development.
 */
@RestController
@RequestMapping("/api/admin/simulator")
@Tag(name = "Simulator", description = "Market simulator control endpoints (admin)")
@ConditionalOnProperty(name = "app.simulator.enabled", havingValue = "true", matchIfMissing = false)
public class SimulatorController {

    private final MarketSimulatorService simulatorService;

    public SimulatorController(MarketSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/start")
    @Operation(summary = "Start simulator", description = "Starts the market simulator")
    public ResponseEntity<Map<String, String>> start() {
        simulatorService.start();
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop simulator", description = "Stops the market simulator")
    public ResponseEntity<Map<String, String>> stop() {
        simulatorService.stop();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @GetMapping("/status")
    @Operation(summary = "Get simulator status", description = "Returns current simulator status and configuration")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(simulatorService.getStatus());
    }
}
