package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for TransactionService.
 * No Spring context, no DB — all dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private BalanceRepository balanceRepository;
    @Mock private UserService userService;
    @Mock private AssetService assetService;
    @Mock private BinanceService binanceService;

    @InjectMocks private TransactionService transactionService;

    private UUID userId;
    private UserAccount user;
    private Asset btcAsset;
    private Asset usdtAsset;
    private UUID btcAssetId;
    private UUID usdtAssetId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new UserAccount("testuser", "test@example.com", "hash");
        user.setId(userId);

        btcAssetId = UUID.randomUUID();
        btcAsset = new Asset("BTC", "Bitcoin", 8);
        btcAsset.setId(btcAssetId);

        usdtAssetId = UUID.randomUUID();
        usdtAsset = new Asset("USDT", "Tether", 2);
        usdtAsset.setId(usdtAssetId);
    }

    private Balance makeBalance(UserAccount u, Asset a, String available) {
        Balance b = new Balance(u, a);
        b.setId(UUID.randomUUID());
        b.setAvailable(new BigDecimal(available));
        return b;
    }

    // ─── BUY tests ───

    @Nested
    @DisplayName("BUY transactions")
    class BuyTests {

        @Test
        @DisplayName("Successful BUY deducts cash and credits asset")
        void buySuccess() {
            BigDecimal qty = new BigDecimal("0.01");
            BigDecimal price = new BigDecimal("65000.00");
            BigDecimal total = new BigDecimal("650.00");

            // Mocks
            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(price);
            when(userService.getUser(userId)).thenReturn(user);

            Balance cashBalance = makeBalance(user, usdtAsset, "10000.00");
            when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, usdtAssetId))
                    .thenReturn(Optional.of(cashBalance));

            Balance btcBalance = makeBalance(user, btcAsset, "0.5");
            when(balanceRepository.findByUserIdAndAssetId(userId, btcAssetId))
                    .thenReturn(Optional.of(btcBalance));

            when(balanceRepository.save(any(Balance.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
                Transaction t = i.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            // Execute
            Transaction result = transactionService.execute(userId, "BTC", OrderSide.BUY, qty);

            // Verify
            assertThat(result).isNotNull();
            assertThat(result.getSide()).isEqualTo(OrderSide.BUY);
            assertThat(result.getAssetSymbol()).isEqualTo("BTC");
            assertThat(result.getPriceUsd()).isEqualByComparingTo(price);

            // Verify cash was deducted
            ArgumentCaptor<Balance> balCaptor = ArgumentCaptor.forClass(Balance.class);
            verify(balanceRepository, times(2)).save(balCaptor.capture());
            // First save is cash deduction, second is asset credit
            Balance savedCash = balCaptor.getAllValues().get(0);
            assertThat(savedCash.getAsset().getSymbol()).isEqualTo("USDT");
        }

        @Test
        @DisplayName("BUY fails with insufficient cash")
        void buyInsufficientCash() {
            BigDecimal qty = new BigDecimal("1.0");
            BigDecimal price = new BigDecimal("65000.00");

            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(price);
            when(userService.getUser(userId)).thenReturn(user);

            Balance cashBalance = makeBalance(user, usdtAsset, "100.00");
            when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, usdtAssetId))
                    .thenReturn(Optional.of(cashBalance));

            assertThatThrownBy(() -> transactionService.execute(userId, "BTC", OrderSide.BUY, qty))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient cash");
        }

        @Test
        @DisplayName("BUY fails with no cash balance")
        void buyNoCashBalance() {
            BigDecimal qty = new BigDecimal("0.01");
            BigDecimal price = new BigDecimal("65000.00");

            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(price);
            when(userService.getUser(userId)).thenReturn(user);

            when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, usdtAssetId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.execute(userId, "BTC", OrderSide.BUY, qty))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("No cash balance found");
        }

        @Test
        @DisplayName("BUY creates new asset balance if none exists")
        void buyCreatesAssetBalance() {
            BigDecimal qty = new BigDecimal("0.01");
            BigDecimal price = new BigDecimal("65000.00");

            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(price);
            when(userService.getUser(userId)).thenReturn(user);

            Balance cashBalance = makeBalance(user, usdtAsset, "10000.00");
            when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, usdtAssetId))
                    .thenReturn(Optional.of(cashBalance));

            // No existing BTC balance
            when(balanceRepository.findByUserIdAndAssetId(userId, btcAssetId))
                    .thenReturn(Optional.empty());
            when(balanceRepository.save(any(Balance.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
                Transaction t = i.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            Transaction result = transactionService.execute(userId, "BTC", OrderSide.BUY, qty);

            assertThat(result).isNotNull();
            // Verify balance was saved 3 times: cash deduct + new asset balance create + asset credit
            verify(balanceRepository, atLeast(2)).save(any(Balance.class));
        }
    }

    // ─── SELL tests ───

    @Nested
    @DisplayName("SELL transactions")
    class SellTests {

        @Test
        @DisplayName("Successful SELL deducts asset and credits cash")
        void sellSuccess() {
            BigDecimal qty = new BigDecimal("0.5");
            BigDecimal price = new BigDecimal("65000.00");

            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(price);
            when(userService.getUser(userId)).thenReturn(user);

            Balance btcBalance = makeBalance(user, btcAsset, "1.0");
            when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, btcAssetId))
                    .thenReturn(Optional.of(btcBalance));

            Balance cashBalance = makeBalance(user, usdtAsset, "5000.00");
            when(balanceRepository.findByUserIdAndAssetId(userId, usdtAssetId))
                    .thenReturn(Optional.of(cashBalance));

            when(balanceRepository.save(any(Balance.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
                Transaction t = i.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            Transaction result = transactionService.execute(userId, "BTC", OrderSide.SELL, qty);

            assertThat(result).isNotNull();
            assertThat(result.getSide()).isEqualTo(OrderSide.SELL);
            assertThat(result.getAssetSymbol()).isEqualTo("BTC");
        }

        @Test
        @DisplayName("SELL fails with insufficient holdings")
        void sellInsufficientHoldings() {
            BigDecimal qty = new BigDecimal("2.0");
            BigDecimal price = new BigDecimal("65000.00");

            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(price);
            when(userService.getUser(userId)).thenReturn(user);

            Balance btcBalance = makeBalance(user, btcAsset, "1.0");
            when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, btcAssetId))
                    .thenReturn(Optional.of(btcBalance));

            assertThatThrownBy(() -> transactionService.execute(userId, "BTC", OrderSide.SELL, qty))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient holdings");
        }

        @Test
        @DisplayName("SELL fails with no holdings at all")
        void sellNoHoldings() {
            BigDecimal qty = new BigDecimal("0.5");
            BigDecimal price = new BigDecimal("65000.00");

            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(price);
            when(userService.getUser(userId)).thenReturn(user);

            when(balanceRepository.findByUserIdAndAssetIdWithLock(userId, btcAssetId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.execute(userId, "BTC", OrderSide.SELL, qty))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("No holdings found");
        }
    }

    // ─── Validation & edge case tests ───

    @Nested
    @DisplayName("Validation and edge cases")
    class ValidationTests {

        @Test
        @DisplayName("Reject zero quantity")
        void zeroQuantity() {
            assertThatThrownBy(() ->
                    transactionService.execute(userId, "BTC", OrderSide.BUY, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("Reject negative quantity")
        void negativeQuantity() {
            assertThatThrownBy(() ->
                    transactionService.execute(userId, "BTC", OrderSide.BUY, new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("Reject trading USDT")
        void rejectUsdtTrade() {
            assertThatThrownBy(() ->
                    transactionService.execute(userId, "USDT", OrderSide.BUY, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("USDT");
        }

        @Test
        @DisplayName("Unknown asset returns 404")
        void unknownAsset() {
            when(assetService.getAssetBySymbol("FAKE"))
                    .thenThrow(new NotFoundException("Asset not found with symbol: FAKE"));

            assertThatThrownBy(() ->
                    transactionService.execute(userId, "FAKE", OrderSide.BUY, new BigDecimal("1")))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Price unavailable returns 503")
        void priceUnavailable() {
            when(assetService.getAssetBySymbol("BTC")).thenReturn(btcAsset);
            when(binanceService.toBinanceSymbol("BTC")).thenReturn("BTCUSDT");
            when(binanceService.getCurrentPrice("BTCUSDT")).thenReturn(null);

            assertThatThrownBy(() ->
                    transactionService.execute(userId, "BTC", OrderSide.BUY, new BigDecimal("0.01")))
                    .isInstanceOf(TransactionService.PriceUnavailableException.class)
                    .hasMessageContaining("temporarily unavailable");
        }
    }
}
