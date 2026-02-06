package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.repository.TransactionRepository;
import com.cryptoexchange.backend.domain.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for executing simple BUY/SELL transactions at current Binance market price.
 * Each transaction atomically updates user balances (USDT cash ↔ asset holdings).
 */
@Service
@Transactional
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final String CASH_SYMBOL = "USDT";

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final UserService userService;
    private final AssetService assetService;
    private final BinanceService binanceService;

    public TransactionService(TransactionRepository transactionRepository,
                              BalanceRepository balanceRepository,
                              UserService userService,
                              AssetService assetService,
                              BinanceService binanceService) {
        this.transactionRepository = transactionRepository;
        this.balanceRepository = balanceRepository;
        this.userService = userService;
        this.assetService = assetService;
        this.binanceService = binanceService;
    }

    /**
     * Execute a BUY or SELL transaction at the current Binance market price.
     * All balance updates and transaction creation happen in a single DB transaction.
     *
     * @param userId   authenticated user ID
     * @param symbol   asset symbol (e.g. "BTC", "ETH")
     * @param side     BUY or SELL
     * @param quantity amount of the asset to buy/sell (must be > 0)
     * @return the saved Transaction
     */
    public Transaction execute(UUID userId, String symbol, OrderSide side, BigDecimal quantity) {
        MoneyUtils.validatePositive(quantity);

        String normalizedSymbol = symbol.trim().toUpperCase();

        if (CASH_SYMBOL.equalsIgnoreCase(normalizedSymbol)) {
            throw new IllegalArgumentException("Cannot trade the cash asset (USDT)");
        }

        // Validate asset exists in DB
        Asset asset = assetService.getAssetBySymbol(normalizedSymbol);

        // Fetch current price from Binance
        BigDecimal priceUsd = fetchCurrentPrice(normalizedSymbol);

        BigDecimal normalizedQuantity = MoneyUtils.normalize(quantity);
        BigDecimal totalUsd = normalizedQuantity.multiply(priceUsd, MC);
        totalUsd = MoneyUtils.normalize(totalUsd);

        UserAccount user = userService.getUser(userId);
        Asset cashAsset = assetService.getAssetBySymbol(CASH_SYMBOL);

        if (side == OrderSide.BUY) {
            executeBuy(user, asset, cashAsset, normalizedQuantity, totalUsd);
        } else {
            executeSell(user, asset, cashAsset, normalizedQuantity, totalUsd);
        }

        // Save transaction record
        Transaction tx = new Transaction(user, normalizedSymbol, side, normalizedQuantity, priceUsd, totalUsd);
        Transaction saved = transactionRepository.save(tx);

        log.info("Transaction executed: {} {} {} @ {} USD (total: {} USD) for user {}",
                side, normalizedQuantity, normalizedSymbol, priceUsd, totalUsd, userId);

        return saved;
    }

    /**
     * Fetch current price from Binance. Throws 503 if unavailable.
     */
    BigDecimal fetchCurrentPrice(String symbol) {
        String binanceSymbol = binanceService.toBinanceSymbol(symbol);
        BigDecimal price = binanceService.getCurrentPrice(binanceSymbol);
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PriceUnavailableException(
                    "Price temporarily unavailable for " + symbol + ". Please try again later.");
        }
        return price;
    }

    /**
     * BUY: deduct cash (USDT), credit asset holdings.
     */
    private void executeBuy(UserAccount user, Asset asset, Asset cashAsset,
                            BigDecimal quantity, BigDecimal totalUsd) {
        UUID userId = user.getId();

        // Lock and check USDT balance
        Balance cashBalance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, cashAsset.getId())
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No cash balance found. Please deposit USDT first."));

        if (cashBalance.getAvailable().compareTo(totalUsd) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient cash. Available: %s USDT, Required: %s USDT",
                            cashBalance.getAvailable().setScale(2, RoundingMode.HALF_UP),
                            totalUsd.setScale(2, RoundingMode.HALF_UP)));
        }

        // Deduct cash
        cashBalance.setAvailable(cashBalance.getAvailable().subtract(totalUsd));
        balanceRepository.save(cashBalance);

        // Credit asset
        Balance assetBalance = getOrCreateBalance(user, asset);
        assetBalance.setAvailable(assetBalance.getAvailable().add(quantity));
        balanceRepository.save(assetBalance);
    }

    /**
     * SELL: deduct asset holdings, credit cash (USDT).
     */
    private void executeSell(UserAccount user, Asset asset, Asset cashAsset,
                             BigDecimal quantity, BigDecimal totalUsd) {
        UUID userId = user.getId();

        // Lock and check asset balance
        Balance assetBalance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, asset.getId())
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No holdings found for " + asset.getSymbol() + ". Nothing to sell."));

        if (assetBalance.getAvailable().compareTo(quantity) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient holdings for %s. Available: %s, Required: %s",
                            asset.getSymbol(),
                            assetBalance.getAvailable().stripTrailingZeros().toPlainString(),
                            quantity.stripTrailingZeros().toPlainString()));
        }

        // Deduct asset
        assetBalance.setAvailable(assetBalance.getAvailable().subtract(quantity));
        balanceRepository.save(assetBalance);

        // Credit cash
        Balance cashBalance = getOrCreateBalance(user, cashAsset);
        cashBalance.setAvailable(cashBalance.getAvailable().add(totalUsd));
        balanceRepository.save(cashBalance);
    }

    private Balance getOrCreateBalance(UserAccount user, Asset asset) {
        Optional<Balance> existing = balanceRepository.findByUserIdAndAssetId(user.getId(), asset.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Balance newBalance = new Balance(user, asset);
        return balanceRepository.save(newBalance);
    }

    /**
     * List transactions for a user with optional filters and pagination.
     */
    @Transactional(readOnly = true)
    public Page<Transaction> listTransactions(UUID userId, String symbol, OrderSide side,
                                              OffsetDateTime from, OffsetDateTime to,
                                              String sortField, String sortDir,
                                              int page, int size) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String field = "totalUsd".equalsIgnoreCase(sortField) ? "totalUsd" : "createdAt";
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, field));

        String normalizedSymbol = symbol != null ? symbol.trim().toUpperCase() : null;
        if (normalizedSymbol != null && normalizedSymbol.isEmpty()) {
            normalizedSymbol = null;
        }

        return transactionRepository.findFiltered(userId, normalizedSymbol, side, from, to, pageable);
    }

    /**
     * Get a single transaction by ID for the authenticated user.
     */
    @Transactional(readOnly = true)
    public Transaction getTransaction(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));
    }

    /**
     * Custom exception for price service unavailability — mapped to 503 in controller advice.
     */
    public static class PriceUnavailableException extends RuntimeException {
        public PriceUnavailableException(String message) {
            super(message);
        }
    }
}
