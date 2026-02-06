package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.repository.CashDepositRepository;
import com.cryptoexchange.backend.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashDepositServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private CashDepositRepository cashDepositRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private AssetService assetService;

    @InjectMocks
    private CashDepositService cashDepositService;

    private UUID userId;
    private UserAccount user;
    private Asset usdtAsset;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new UserAccount("testuser", "test@example.com", "hashed");
        user.setId(userId);
        user.setCashBalanceUsd(BigDecimal.ZERO);

        usdtAsset = new Asset("USDT", "Tether", 2);
        usdtAsset.setId(UUID.randomUUID());
    }

    /** Stubs for the USDT Balance credit that happens on every successful deposit. */
    private void stubUsdtBalanceCredit() {
        when(assetService.getAssetBySymbol("USDT")).thenReturn(usdtAsset);
        when(balanceRepository.findByUserIdAndAssetId(eq(userId), eq(usdtAsset.getId())))
                .thenReturn(Optional.of(new Balance(user, usdtAsset)));
        when(balanceRepository.save(any(Balance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deposit_underLimit_succeeds() {
        // Given
        BigDecimal amount = new BigDecimal("500.00");
        when(userAccountRepository.findByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(cashDepositRepository.sumDepositsSince(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(BigDecimal.ZERO);
        when(cashDepositRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubUsdtBalanceCredit();

        // When
        CashDepositService.CashBalanceInfo result = cashDepositService.deposit(userId, amount);

        // Then
        assertThat(result.cashUsd()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.depositedLast24h()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.remainingLimit24h()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.depositLimit24h()).isEqualByComparingTo(new BigDecimal("1000.00"));
        verify(cashDepositRepository).save(any());
        verify(userAccountRepository).save(user);
    }

    @Test
    void deposit_exactlyAtLimit_succeeds() {
        // Given: already deposited $700, depositing $300 more (total = $1000, exactly at limit)
        BigDecimal amount = new BigDecimal("300.00");
        user.setCashBalanceUsd(new BigDecimal("700.00"));
        when(userAccountRepository.findByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(cashDepositRepository.sumDepositsSince(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("700.00"));
        when(cashDepositRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubUsdtBalanceCredit();

        // When
        CashDepositService.CashBalanceInfo result = cashDepositService.deposit(userId, amount);

        // Then
        assertThat(result.cashUsd()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.depositedLast24h()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.remainingLimit24h()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deposit_exceedingLimit_throwsException() {
        // Given: already deposited $800, trying to deposit $300 (would be $1100 > $1000)
        BigDecimal amount = new BigDecimal("300.00");
        when(userAccountRepository.findByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(cashDepositRepository.sumDepositsSince(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("800.00"));

        // When/Then
        assertThatThrownBy(() -> cashDepositService.deposit(userId, amount))
                .isInstanceOf(CashDepositService.DepositLimitExceededException.class)
                .hasMessageContaining("exceed the 24-hour limit")
                .hasMessageContaining("200.00"); // remaining limit

        verify(cashDepositRepository, never()).save(any());
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void deposit_fullLimitAlreadyUsed_throwsException() {
        // Given: already deposited $1000 (limit fully used)
        BigDecimal amount = new BigDecimal("1.00");
        when(userAccountRepository.findByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(cashDepositRepository.sumDepositsSince(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("1000.00"));

        // When/Then
        assertThatThrownBy(() -> cashDepositService.deposit(userId, amount))
                .isInstanceOf(CashDepositService.DepositLimitExceededException.class);

        verify(cashDepositRepository, never()).save(any());
    }

    @Test
    void deposit_negativeAmount_throwsIllegalArgument() {
        // When/Then
        assertThatThrownBy(() -> cashDepositService.deposit(userId, new BigDecimal("-10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void deposit_zeroAmount_throwsIllegalArgument() {
        // When/Then
        assertThatThrownBy(() -> cashDepositService.deposit(userId, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void deposit_nullAmount_throwsIllegalArgument() {
        // When/Then
        assertThatThrownBy(() -> cashDepositService.deposit(userId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void getCashBalance_returnsCorrectInfo() {
        // Given
        user.setCashBalanceUsd(new BigDecimal("350.00"));
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cashDepositRepository.sumDepositsSince(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("600.00"));

        // When
        CashDepositService.CashBalanceInfo result = cashDepositService.getCashBalance(userId);

        // Then
        assertThat(result.cashUsd()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(result.depositLimit24h()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.depositedLast24h()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(result.remainingLimit24h()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void deposit_multipleDepositsUnderLimit_allSucceed() {
        // Given: first deposit $400
        when(userAccountRepository.findByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(cashDepositRepository.sumDepositsSince(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(BigDecimal.ZERO);
        when(cashDepositRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubUsdtBalanceCredit();

        CashDepositService.CashBalanceInfo result1 = cashDepositService.deposit(userId, new BigDecimal("400.00"));
        assertThat(result1.cashUsd()).isEqualByComparingTo(new BigDecimal("400.00"));

        // Given: second deposit $500 (total $900, under limit)
        user.setCashBalanceUsd(new BigDecimal("400.00"));
        when(cashDepositRepository.sumDepositsSince(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("400.00"));

        CashDepositService.CashBalanceInfo result2 = cashDepositService.deposit(userId, new BigDecimal("500.00"));
        assertThat(result2.cashUsd()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(result2.remainingLimit24h()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
