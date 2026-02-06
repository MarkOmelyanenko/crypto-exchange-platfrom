package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.config.JacksonConfig;
import com.cryptoexchange.backend.config.JwtService;
import com.cryptoexchange.backend.config.RateLimitProperties;
import com.cryptoexchange.backend.config.RateLimitService;
import com.cryptoexchange.backend.domain.service.CashDepositService;
import com.cryptoexchange.backend.domain.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
@Import(JacksonConfig.class)
class WalletControllerCashDepositTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private CashDepositService cashDepositService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Creates an Authentication matching the real JwtAuthenticationFilter behavior:
     * principal is the userId string, not a UserDetails object.
     */
    private static UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void getCashBalance_returnsBalanceInfo() throws Exception {
        // Given
        CashDepositService.CashBalanceInfo info = new CashDepositService.CashBalanceInfo(
                new BigDecimal("350.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("600.00"),
                new BigDecimal("400.00")
        );
        when(cashDepositService.getCashBalance(USER_ID)).thenReturn(info);

        // When/Then
        mockMvc.perform(get("/api/wallet/balance")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashUsd").value(350.00))
                .andExpect(jsonPath("$.depositLimit24h").value(1000.00))
                .andExpect(jsonPath("$.depositedLast24h").value(600.00))
                .andExpect(jsonPath("$.remainingLimit24h").value(400.00));
    }

    @Test
    void cashDeposit_success_returnsCreated() throws Exception {
        // Given
        CashDepositService.CashBalanceInfo info = new CashDepositService.CashBalanceInfo(
                new BigDecimal("200.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00")
        );
        when(cashDepositService.deposit(eq(USER_ID), any(BigDecimal.class))).thenReturn(info);

        // When/Then
        mockMvc.perform(post("/api/wallet/cash-deposit")
                        .with(authentication(userAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountUsd\": 200.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cashUsd").value(200.00))
                .andExpect(jsonPath("$.remainingLimit24h").value(800.00));
    }

    @Test
    void cashDeposit_exceedsLimit_returns400() throws Exception {
        // Given
        when(cashDepositService.deposit(eq(USER_ID), any(BigDecimal.class)))
                .thenThrow(new CashDepositService.DepositLimitExceededException(
                        "Deposit of 500.00 USDT would exceed the 24-hour limit of 1000.00 USDT. " +
                                "You have deposited 800.00 USDT in the last 24 hours. Remaining limit: 200.00 USDT.",
                        new BigDecimal("200.00")
                ));

        // When/Then
        mockMvc.perform(post("/api/wallet/cash-deposit")
                        .with(authentication(userAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountUsd\": 500.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPOSIT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceed the 24-hour limit")));
    }

    @Test
    void cashDeposit_invalidAmount_returns400() throws Exception {
        // When/Then - negative amount triggers Bean Validation
        mockMvc.perform(post("/api/wallet/cash-deposit")
                        .with(authentication(userAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountUsd\": -10.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCashBalance_unauthenticated_returns401() throws Exception {
        // When/Then - no authentication header
        mockMvc.perform(get("/api/wallet/balance"))
                .andExpect(status().isUnauthorized());
    }
}
