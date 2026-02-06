package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.model.CashDeposit;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.repository.CashDepositRepository;
import com.cryptoexchange.backend.domain.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Handles fictitious USD cash deposits with a rolling 24-hour limit of $1,000.
 */
@Service
@Transactional
public class CashDepositService {

    private static final Logger log = LoggerFactory.getLogger(CashDepositService.class);
    private static final String CASH_SYMBOL = "USDT";

    /** Maximum USD that can be deposited within any rolling 24-hour window. */
    public static final BigDecimal DEPOSIT_LIMIT_24H = new BigDecimal("1000.00");

    private final UserAccountRepository userAccountRepository;
    private final CashDepositRepository cashDepositRepository;
    private final BalanceRepository balanceRepository;
    private final AssetService assetService;

    public CashDepositService(UserAccountRepository userAccountRepository,
                              CashDepositRepository cashDepositRepository,
                              BalanceRepository balanceRepository,
                              AssetService assetService) {
        this.userAccountRepository = userAccountRepository;
        this.cashDepositRepository = cashDepositRepository;
        this.balanceRepository = balanceRepository;
        this.assetService = assetService;
    }

    /**
     * Returns the current cash balance and 24h deposit limit information for the given user.
     */
    @Transactional(readOnly = true)
    public CashBalanceInfo getCashBalance(UUID userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        BigDecimal depositedLast24h = getDepositedLast24h(userId);
        BigDecimal remaining = DEPOSIT_LIMIT_24H.subtract(depositedLast24h).max(BigDecimal.ZERO);

        return new CashBalanceInfo(
                user.getCashBalanceUsd(),
                DEPOSIT_LIMIT_24H,
                depositedLast24h,
                remaining
        );
    }

    /**
     * Processes a cash deposit. Validates amount and 24-hour rolling limit.
     * Uses pessimistic locking on the user row to prevent race conditions.
     *
     * @return updated balance info after the deposit
     * @throws IllegalArgumentException if amount is not positive or exceeds remaining limit
     */
    public CashBalanceInfo deposit(UUID userId, BigDecimal amountUsd) {
        // Validate positive amount
        if (amountUsd == null || amountUsd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        // Lock the user row to prevent concurrent deposits from exceeding the limit
        UserAccount user = userAccountRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        // Check 24h rolling limit
        BigDecimal depositedLast24h = getDepositedLast24h(userId);
        BigDecimal remaining = DEPOSIT_LIMIT_24H.subtract(depositedLast24h).max(BigDecimal.ZERO);

        if (depositedLast24h.add(amountUsd).compareTo(DEPOSIT_LIMIT_24H) > 0) {
            throw new DepositLimitExceededException(
                    String.format("Deposit of $%s would exceed the 24-hour limit of $%s. " +
                                    "You have deposited $%s in the last 24 hours. Remaining limit: $%s.",
                            amountUsd.toPlainString(),
                            DEPOSIT_LIMIT_24H.toPlainString(),
                            depositedLast24h.toPlainString(),
                            remaining.toPlainString()),
                    remaining
            );
        }

        // Record the deposit
        CashDeposit deposit = new CashDeposit(user, amountUsd);
        cashDepositRepository.save(deposit);

        // Update user cash balance
        user.setCashBalanceUsd(user.getCashBalanceUsd().add(amountUsd));
        userAccountRepository.save(user);

        // Also credit the USDT Balance row so the trading engine can use the funds
        creditUsdtBalance(user, amountUsd);

        log.info("Cash deposit of ${} for user {} successful. New balance: ${}",
                amountUsd.toPlainString(), userId, user.getCashBalanceUsd().toPlainString());

        BigDecimal newDeposited = depositedLast24h.add(amountUsd);
        BigDecimal newRemaining = DEPOSIT_LIMIT_24H.subtract(newDeposited).max(BigDecimal.ZERO);

        return new CashBalanceInfo(
                user.getCashBalanceUsd(),
                DEPOSIT_LIMIT_24H,
                newDeposited,
                newRemaining
        );
    }

    /**
     * Credits the USDT Balance row for the user (used by the trading engine).
     * Creates the row if it doesn't exist yet.
     */
    private void creditUsdtBalance(UserAccount user, BigDecimal amount) {
        Asset cashAsset;
        try {
            cashAsset = assetService.getAssetBySymbol(CASH_SYMBOL);
        } catch (Exception e) {
            log.warn("USDT asset not found in DB — skipping Balance row update. " +
                     "Trading will not see deposited funds until USDT asset is seeded.");
            return;
        }
        if (cashAsset == null) {
            return;
        }

        Balance cashBalance = balanceRepository
                .findByUserIdAndAssetId(user.getId(), cashAsset.getId())
                .orElseGet(() -> {
                    Balance b = new Balance(user, cashAsset);
                    return balanceRepository.save(b);
                });

        cashBalance.setAvailable(cashBalance.getAvailable().add(amount));
        balanceRepository.save(cashBalance);
    }

    /**
     * Returns total deposited in the last 24 hours for the given user.
     */
    BigDecimal getDepositedLast24h(UUID userId) {
        OffsetDateTime since = OffsetDateTime.now().minusHours(24);
        return cashDepositRepository.sumDepositsSince(userId, since);
    }

    // ── DTOs ──

    /**
     * Immutable DTO for cash balance and deposit limit information.
     */
    public record CashBalanceInfo(
            BigDecimal cashUsd,
            BigDecimal depositLimit24h,
            BigDecimal depositedLast24h,
            BigDecimal remainingLimit24h
    ) {}

    /**
     * Thrown when a deposit would exceed the rolling 24-hour limit.
     */
    public static class DepositLimitExceededException extends RuntimeException {
        private final BigDecimal remainingLimit;

        public DepositLimitExceededException(String message, BigDecimal remainingLimit) {
            super(message);
            this.remainingLimit = remainingLimit;
        }

        public BigDecimal getRemainingLimit() {
            return remainingLimit;
        }
    }
}
