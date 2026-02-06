package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.repository.TradeRepository;
import com.cryptoexchange.backend.domain.repository.UserAccountRepository;
import com.cryptoexchange.backend.domain.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes spot market orders (BUY / SELL) against the current Binance price.
 * <p>
 * BUY:  user provides quoteAmount (USDT to spend) → receives baseQty of base asset.
 * SELL: user provides baseAmount of base asset to sell → receives quoteQty USDT.
 * <p>
 * All balance updates happen atomically in a single DB transaction.
 * Pessimistic locking prevents race conditions on balance rows.
 */
@Service
@Transactional
public class MarketOrderService {

    private static final Logger log = LoggerFactory.getLogger(MarketOrderService.class);
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);

    private final MarketService marketService;
    private final BinanceService binanceService;
    private final UserService userService;
    private final BalanceRepository balanceRepository;
    private final UserAccountRepository userAccountRepository;
    private final TradeRepository tradeRepository;

    public MarketOrderService(MarketService marketService,
                              BinanceService binanceService,
                              UserService userService,
                              BalanceRepository balanceRepository,
                              UserAccountRepository userAccountRepository,
                              TradeRepository tradeRepository) {
        this.marketService = marketService;
        this.binanceService = binanceService;
        this.userService = userService;
        this.balanceRepository = balanceRepository;
        this.userAccountRepository = userAccountRepository;
        this.tradeRepository = tradeRepository;
    }

    /**
     * Execute a market BUY order: spend quoteAmount of quote asset to receive base asset.
     *
     * @param userId      authenticated user ID
     * @param pairId      market/pair UUID
     * @param quoteAmount amount of quote asset (USDT) to spend
     * @return the recorded Trade
     */
    public Trade executeBuy(UUID userId, UUID pairId, BigDecimal quoteAmount) {
        MoneyUtils.validatePositive(quoteAmount);

        Market pair = marketService.getMarket(pairId);
        if (!pair.getActive()) {
            throw new IllegalArgumentException("Market " + pair.getSymbol() + " is not active");
        }

        BigDecimal price = fetchPrice(pair.getSymbol());

        // baseQty = quoteAmount / price, scaled to base asset precision
        int baseScale = pair.getBaseAsset().getScale();
        BigDecimal baseQty = quoteAmount.divide(price, baseScale, RoundingMode.DOWN);
        if (baseQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quote amount too small to buy any " + pair.getBaseAsset().getSymbol());
        }

        // Recalculate actual quote spent = baseQty * price (may be slightly less due to rounding)
        BigDecimal actualQuote = MoneyUtils.normalize(baseQty.multiply(price, MC));

        UserAccount user = userService.getUser(userId);
        Asset quoteAsset = pair.getQuoteAsset();
        Asset baseAsset = pair.getBaseAsset();

        // Lock and debit quote balance
        Balance quoteBalance = lockBalance(userId, quoteAsset.getId());
        if (quoteBalance.getAvailable().compareTo(actualQuote) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient %s. Available: %s, Required: %s",
                            quoteAsset.getSymbol(),
                            quoteBalance.getAvailable().stripTrailingZeros().toPlainString(),
                            actualQuote.stripTrailingZeros().toPlainString()));
        }
        quoteBalance.setAvailable(quoteBalance.getAvailable().subtract(actualQuote));
        balanceRepository.save(quoteBalance);

        // Credit base balance
        Balance baseBalance = getOrCreateBalance(user, baseAsset);
        baseBalance.setAvailable(baseBalance.getAvailable().add(MoneyUtils.normalize(baseQty)));
        balanceRepository.save(baseBalance);

        // Sync UserAccount.cashBalanceUsd if quote is USDT
        if ("USDT".equalsIgnoreCase(quoteAsset.getSymbol())) {
            syncCashBalance(user, actualQuote.negate());
        }

        // Record trade
        Trade trade = new Trade(user, pair, OrderSide.BUY, price,
                MoneyUtils.normalize(baseQty), actualQuote);
        trade = tradeRepository.save(trade);

        log.info("BUY {} {} @ {} {} (spent {} {}) for user {}",
                baseQty.stripTrailingZeros().toPlainString(), baseAsset.getSymbol(),
                price.stripTrailingZeros().toPlainString(), quoteAsset.getSymbol(),
                actualQuote.stripTrailingZeros().toPlainString(), quoteAsset.getSymbol(),
                userId);

        return trade;
    }

    /**
     * Execute a market SELL order: sell baseAmount of base asset to receive quote asset.
     *
     * @param userId     authenticated user ID
     * @param pairId     market/pair UUID
     * @param baseAmount amount of base asset to sell
     * @return the recorded Trade
     */
    public Trade executeSell(UUID userId, UUID pairId, BigDecimal baseAmount) {
        MoneyUtils.validatePositive(baseAmount);

        Market pair = marketService.getMarket(pairId);
        if (!pair.getActive()) {
            throw new IllegalArgumentException("Market " + pair.getSymbol() + " is not active");
        }

        BigDecimal price = fetchPrice(pair.getSymbol());

        BigDecimal normalizedBase = MoneyUtils.normalize(baseAmount);
        BigDecimal quoteQty = MoneyUtils.normalize(normalizedBase.multiply(price, MC));

        UserAccount user = userService.getUser(userId);
        Asset quoteAsset = pair.getQuoteAsset();
        Asset baseAsset = pair.getBaseAsset();

        // Lock and debit base balance
        Balance baseBalance = lockBalance(userId, baseAsset.getId());
        if (baseBalance.getAvailable().compareTo(normalizedBase) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient %s. Available: %s, Required: %s",
                            baseAsset.getSymbol(),
                            baseBalance.getAvailable().stripTrailingZeros().toPlainString(),
                            normalizedBase.stripTrailingZeros().toPlainString()));
        }
        baseBalance.setAvailable(baseBalance.getAvailable().subtract(normalizedBase));
        balanceRepository.save(baseBalance);

        // Credit quote balance
        Balance quoteBalance = getOrCreateBalance(user, quoteAsset);
        quoteBalance.setAvailable(quoteBalance.getAvailable().add(quoteQty));
        balanceRepository.save(quoteBalance);

        // Sync UserAccount.cashBalanceUsd if quote is USDT
        if ("USDT".equalsIgnoreCase(quoteAsset.getSymbol())) {
            syncCashBalance(user, quoteQty);
        }

        // Record trade
        Trade trade = new Trade(user, pair, OrderSide.SELL, price, normalizedBase, quoteQty);
        trade = tradeRepository.save(trade);

        log.info("SELL {} {} @ {} {} (received {} {}) for user {}",
                normalizedBase.stripTrailingZeros().toPlainString(), baseAsset.getSymbol(),
                price.stripTrailingZeros().toPlainString(), quoteAsset.getSymbol(),
                quoteQty.stripTrailingZeros().toPlainString(), quoteAsset.getSymbol(),
                userId);

        return trade;
    }

    /**
     * List recent trades for a user with pagination.
     */
    @Transactional(readOnly = true)
    public Page<Trade> listUserTrades(UUID userId, int page, int size) {
        return tradeRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    // ── Internal helpers ──

    private BigDecimal fetchPrice(String marketSymbol) {
        // Convert market symbol (e.g., "BTC/USDT") to Binance format (e.g., "BTCUSDT")
        String binanceSymbol = marketSymbol.replace("/", "").toUpperCase();
        BigDecimal price = binanceService.getCurrentPrice(binanceSymbol);
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionService.PriceUnavailableException(
                    "Price temporarily unavailable for " + marketSymbol + ". Please try again later.");
        }
        return price;
    }

    private Balance lockBalance(UUID userId, UUID assetId) {
        return balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No balance found. Please deposit funds first."));
    }

    private Balance getOrCreateBalance(UserAccount user, Asset asset) {
        Optional<Balance> existing = balanceRepository.findByUserIdAndAssetId(user.getId(), asset.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Balance newBalance = new Balance(user, asset);
        return balanceRepository.save(newBalance);
    }

    private void syncCashBalance(UserAccount user, BigDecimal delta) {
        UserAccount locked = userAccountRepository.findByIdWithLock(user.getId())
                .orElse(user);
        BigDecimal newBal = locked.getCashBalanceUsd().add(delta);
        if (newBal.compareTo(BigDecimal.ZERO) < 0) {
            newBal = BigDecimal.ZERO;
        }
        locked.setCashBalanceUsd(newBal);
        userAccountRepository.save(locked);
    }
}
