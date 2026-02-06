package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.config.JacksonConfig;
import com.cryptoexchange.backend.config.JwtService;
import com.cryptoexchange.backend.config.RateLimitProperties;
import com.cryptoexchange.backend.config.RateLimitService;
import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.service.MarketOrderService;
import com.cryptoexchange.backend.domain.service.TransactionService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketOrderController.class)
@Import(JacksonConfig.class)
class MarketOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketOrderService marketOrderService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PAIR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private Trade createMockTrade(OrderSide side) {
        Asset btc = new Asset("BTC", "Bitcoin", 8);
        btc.setId(UUID.randomUUID());
        Asset usdt = new Asset("USDT", "Tether", 2);
        usdt.setId(UUID.randomUUID());

        UserAccount user = new UserAccount("test", "test@test.com", "pwd");
        user.setId(USER_ID);

        Market pair = new Market(btc, usdt, "BTC/USDT");
        pair.setId(PAIR_ID);
        pair.setActive(true);

        Trade trade = new Trade(user, pair, side,
                new BigDecimal("50000.00"),
                new BigDecimal("0.002"),
                new BigDecimal("100.00"));
        trade.setId(UUID.randomUUID());
        trade.setCreatedAt(OffsetDateTime.now());
        return trade;
    }

    @Test
    void marketBuy_success_returnsCreated() throws Exception {
        Trade mockTrade = createMockTrade(OrderSide.BUY);
        when(marketOrderService.executeBuy(eq(USER_ID), eq(PAIR_ID), any(BigDecimal.class)))
                .thenReturn(mockTrade);

        String body = String.format("{\"pairId\":\"%s\",\"side\":\"BUY\",\"quoteAmount\":\"100.00\"}", PAIR_ID);

        mockMvc.perform(post("/api/orders/market")
                        .with(authentication(userAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.pairSymbol").value("BTC/USDT"))
                .andExpect(jsonPath("$.baseAsset").value("BTC"))
                .andExpect(jsonPath("$.quoteAsset").value("USDT"));
    }

    @Test
    void marketSell_success_returnsCreated() throws Exception {
        Trade mockTrade = createMockTrade(OrderSide.SELL);
        when(marketOrderService.executeSell(eq(USER_ID), eq(PAIR_ID), any(BigDecimal.class)))
                .thenReturn(mockTrade);

        String body = String.format("{\"pairId\":\"%s\",\"side\":\"SELL\",\"baseAmount\":\"0.01\"}", PAIR_ID);

        mockMvc.perform(post("/api/orders/market")
                        .with(authentication(userAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"));
    }

    @Test
    void marketBuy_insufficientBalance_returns400() throws Exception {
        when(marketOrderService.executeBuy(eq(USER_ID), eq(PAIR_ID), any(BigDecimal.class)))
                .thenThrow(new InsufficientBalanceException("Insufficient USDT. Available: 10, Required: 100"));

        String body = String.format("{\"pairId\":\"%s\",\"side\":\"BUY\",\"quoteAmount\":\"100.00\"}", PAIR_ID);

        mockMvc.perform(post("/api/orders/market")
                        .with(authentication(userAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void marketOrder_priceUnavailable_returns503() throws Exception {
        when(marketOrderService.executeBuy(eq(USER_ID), eq(PAIR_ID), any(BigDecimal.class)))
                .thenThrow(new TransactionService.PriceUnavailableException("Price unavailable"));

        String body = String.format("{\"pairId\":\"%s\",\"side\":\"BUY\",\"quoteAmount\":\"100.00\"}", PAIR_ID);

        mockMvc.perform(post("/api/orders/market")
                        .with(authentication(userAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRICE_UNAVAILABLE"));
    }

    @Test
    void marketOrder_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/orders/market")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairId\":\"" + PAIR_ID + "\",\"side\":\"BUY\",\"quoteAmount\":\"100\"}"))
                .andExpect(status().isUnauthorized());
    }
}
