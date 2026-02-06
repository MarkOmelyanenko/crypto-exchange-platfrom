package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.repository.TradeRepository;
import com.cryptoexchange.backend.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketOrderServiceTest {

    @Mock private MarketService marketService;
    @Mock private BinanceService binanceService;
    @Mock private UserService userService;
    @Mock private BalanceRepository balanceRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private TradeRepository tradeRepository;

    @InjectMocks
    private MarketOrderService marketOrderService;

    private UUID userId;
    private UUID pairId;
    private UserAccount user;
    private Asset btcAsset;
    private Asset usdtAsset;
    private Market btcUsdtPair;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        pairId = UUID.randomUUID();

        user = new UserAccount("testuser", "test@example.com", "hashed");
        user.setId(userId);
        user.setCashBalanceUsd(new BigDecimal("1000.00"));

        btcAsset = new Asset("BTC", "Bitcoin", 8);
        btcAsset.setId(UUID.randomUUID());

        usdtAsset = new Asset("USDT", "Tether", 2);
        usdtAsset.setId(UUID.randomUUID());

        btcUsdtPair = new Market(btcAsset, usdtAsset, "BTCUSDT");
        btcUsdtPair.setId(pairId);
        btcUsdtPair.setActive(true);
    }

    // ── BUY tests ──

    @Test
    void executeBuy_success() {
        // Given: user has 1000 USDT, BTC price is 50000, buying with 100 USDT
        BigDecimal quoteAmount = new BigDecimal("100.00");
        BigDecimal btcPrice = new BigDecimal("50000.00");

        Balance usdtBalance = new Balance(user, usdtAsset);
        usdtBalance.setAvailable(new BigDecimal("1000.000000000000000000"));

        when(marketService.getMarket(pairId)).thenReturn(btcUsdtPair);
        when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(btcPrice);
        when(userService.getUser(userId)).thenReturn(user);
        when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, usdtAsset.getId()))
                .thenReturn(Optional.of(usdtBalance));
        when(balanceRepository.findByUserIdAndAssetId(userId, btcAsset.getId()))
                .thenReturn(Optional.of(new Balance(user, btcAsset)));
        when(balanceRepository.save(any(Balance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> {
            Trade t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(userAccountRepository.findByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        Trade trade = marketOrderService.executeBuy(userId, pairId, quoteAmount);

        // Then
        assertThat(trade.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(trade.getPrice()).isEqualByComparingTo(btcPrice);
        // 100 / 50000 = 0.002 BTC (with 8 decimal places)
        assertThat(trade.getBaseQty()).isGreaterThan(BigDecimal.ZERO);
        assertThat(trade.getQuoteQty()).isGreaterThan(BigDecimal.ZERO);
        verify(tradeRepository).save(any(Trade.class));
    }

    @Test
    void executeBuy_insufficientBalance_throws() {
        // Given: user has only 10 USDT, trying to buy with 100
        BigDecimal quoteAmount = new BigDecimal("100.00");
        BigDecimal btcPrice = new BigDecimal("50000.00");

        Balance usdtBalance = new Balance(user, usdtAsset);
        usdtBalance.setAvailable(new BigDecimal("10.000000000000000000"));

        when(marketService.getMarket(pairId)).thenReturn(btcUsdtPair);
        when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(btcPrice);
        when(userService.getUser(userId)).thenReturn(user);
        when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, usdtAsset.getId()))
                .thenReturn(Optional.of(usdtBalance));

        // When/Then
        assertThatThrownBy(() -> marketOrderService.executeBuy(userId, pairId, quoteAmount))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient USDT");

        verify(tradeRepository, never()).save(any());
    }

    @Test
    void executeBuy_priceUnavailable_throws() {
        BigDecimal quoteAmount = new BigDecimal("100.00");

        when(marketService.getMarket(pairId)).thenReturn(btcUsdtPair);
        when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(null);

        assertThatThrownBy(() -> marketOrderService.executeBuy(userId, pairId, quoteAmount))
                .isInstanceOf(TransactionService.PriceUnavailableException.class);
    }

    // ── SELL tests ──

    @Test
    void executeSell_success() {
        // Given: user has 0.01 BTC, BTC price is 50000
        BigDecimal baseAmount = new BigDecimal("0.01");
        BigDecimal btcPrice = new BigDecimal("50000.00");

        Balance btcBalance = new Balance(user, btcAsset);
        btcBalance.setAvailable(new BigDecimal("0.100000000000000000"));

        when(marketService.getMarket(pairId)).thenReturn(btcUsdtPair);
        when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(btcPrice);
        when(userService.getUser(userId)).thenReturn(user);
        when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, btcAsset.getId()))
                .thenReturn(Optional.of(btcBalance));
        when(balanceRepository.findByUserIdAndAssetId(userId, usdtAsset.getId()))
                .thenReturn(Optional.of(new Balance(user, usdtAsset)));
        when(balanceRepository.save(any(Balance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> {
            Trade t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(userAccountRepository.findByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        Trade trade = marketOrderService.executeSell(userId, pairId, baseAmount);

        // Then
        assertThat(trade.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(trade.getPrice()).isEqualByComparingTo(btcPrice);
        // 0.01 * 50000 = 500 USDT
        assertThat(trade.getQuoteQty()).isGreaterThan(BigDecimal.ZERO);
        verify(tradeRepository).save(any(Trade.class));
    }

    @Test
    void executeSell_insufficientBalance_throws() {
        // Given: user has 0.001 BTC, trying to sell 0.01
        BigDecimal baseAmount = new BigDecimal("0.01");
        BigDecimal btcPrice = new BigDecimal("50000.00");

        Balance btcBalance = new Balance(user, btcAsset);
        btcBalance.setAvailable(new BigDecimal("0.001000000000000000"));

        when(marketService.getMarket(pairId)).thenReturn(btcUsdtPair);
        when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(btcPrice);
        when(userService.getUser(userId)).thenReturn(user);
        when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, btcAsset.getId()))
                .thenReturn(Optional.of(btcBalance));

        assertThatThrownBy(() -> marketOrderService.executeSell(userId, pairId, baseAmount))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient BTC");

        verify(tradeRepository, never()).save(any());
    }

    @Test
    void executeBuy_negativeAmount_throws() {
        assertThatThrownBy(() -> marketOrderService.executeBuy(userId, pairId, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeSell_zeroAmount_throws() {
        assertThatThrownBy(() -> marketOrderService.executeSell(userId, pairId, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeBuy_inactiveMarket_throws() {
        btcUsdtPair.setActive(false);
        when(marketService.getMarket(pairId)).thenReturn(btcUsdtPair);

        assertThatThrownBy(() -> marketOrderService.executeBuy(userId, pairId, new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }
}
