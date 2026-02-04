package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.model.WalletHold;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.repository.WalletHoldRepository;
import com.cryptoexchange.backend.domain.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final String REF_TYPE_ORDER = "ORDER";

    private final BalanceRepository balanceRepository;
    private final WalletHoldRepository walletHoldRepository;
    private final UserService userService;
    private final AssetService assetService;

    public WalletService(BalanceRepository balanceRepository, 
                        WalletHoldRepository walletHoldRepository,
                        UserService userService, 
                        AssetService assetService) {
        this.balanceRepository = balanceRepository;
        this.walletHoldRepository = walletHoldRepository;
        this.userService = userService;
        this.assetService = assetService;
    }

    @Transactional(readOnly = true)
    public List<Balance> getBalances(UUID userId) {
        return balanceRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Balance getBalance(UUID userId, UUID assetId) {
        return balanceRepository.findByUserIdAndAssetId(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
    }

    @Transactional(readOnly = true)
    public Balance getBalanceByCurrency(UUID userId, String currency) {
        Asset asset = assetService.getAssetBySymbol(currency.toUpperCase());
        return getBalance(userId, asset.getId());
    }

    public Balance getOrCreateBalance(UUID userId, UUID assetId) {
        return balanceRepository.findByUserIdAndAssetId(userId, assetId)
            .orElseGet(() -> {
                UserAccount user = userService.getUser(userId);
                Asset asset = assetService.getAsset(assetId);
                Balance newBalance = new Balance(user, asset);
                return balanceRepository.save(newBalance);
            });
    }

    public Balance getOrCreateBalanceByCurrency(UUID userId, String currency) {
        Asset asset = assetService.getAssetBySymbol(currency.toUpperCase());
        return getOrCreateBalance(userId, asset.getId());
    }

    public Balance deposit(UUID userId, UUID assetId, BigDecimal amount) {
        MoneyUtils.validatePositive(amount);
        BigDecimal normalizedAmount = MoneyUtils.normalize(amount);
        
        Asset asset = assetService.getAsset(assetId);
        
        Balance balance = getOrCreateBalance(userId, assetId);
        
        balance.setAvailable(balance.getAvailable().add(normalizedAmount));
        Balance saved = balanceRepository.save(balance);
        
        log.info("Deposited {} {} to user {}", normalizedAmount, asset.getSymbol(), userId);
        return saved;
    }

    public Balance depositByCurrency(UUID userId, String currency, BigDecimal amount) {
        Asset asset = assetService.getAssetBySymbol(currency.toUpperCase());
        return deposit(userId, asset.getId(), amount);
    }

    public Balance withdraw(UUID userId, UUID assetId, BigDecimal amount) {
        MoneyUtils.validatePositive(amount);
        BigDecimal normalizedAmount = MoneyUtils.normalize(amount);
        
        Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
        
        if (balance.getAvailable().compareTo(normalizedAmount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient available balance. Available: %s, Required: %s",
                    balance.getAvailable(), normalizedAmount));
        }
        
        balance.setAvailable(balance.getAvailable().subtract(normalizedAmount));
        Balance saved = balanceRepository.save(balance);
        
        log.info("Withdrew {} {} from user {}", normalizedAmount, balance.getAsset().getSymbol(), userId);
        return saved;
    }

    public Balance withdrawByCurrency(UUID userId, String currency, BigDecimal amount) {
        Asset asset = assetService.getAssetBySymbol(currency.toUpperCase());
        return withdraw(userId, asset.getId(), amount);
    }

    /**
     * Reserves funds for an order. Idempotent - if a hold already exists, returns without error.
     */
    public WalletHold reserveForOrder(UUID userId, UUID assetId, BigDecimal amount, UUID orderId) {
        MoneyUtils.validatePositive(amount);
        BigDecimal normalizedAmount = MoneyUtils.normalize(amount);
        
        // Check if hold already exists (idempotency)
        Optional<WalletHold> existingHold = walletHoldRepository.findActiveHold(
            userId, assetId, REF_TYPE_ORDER, orderId);
        if (existingHold.isPresent()) {
            log.debug("Hold already exists for order {} - skipping reservation", orderId);
            return existingHold.get();
        }
        
        Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
        
        if (balance.getAvailable().compareTo(normalizedAmount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient available balance. Available: %s, Required: %s",
                    balance.getAvailable(), normalizedAmount));
        }
        
        // Move from available to locked
        balance.setAvailable(balance.getAvailable().subtract(normalizedAmount));
        balance.setLocked(balance.getLocked().add(normalizedAmount));
        balanceRepository.save(balance);
        
        // Create hold record
        UserAccount user = userService.getUser(userId);
        Asset asset = assetService.getAsset(assetId);
        WalletHold hold = new WalletHold(user, asset, normalizedAmount, REF_TYPE_ORDER, orderId);
        WalletHold saved = walletHoldRepository.save(hold);
        
        log.info("Reserved {} {} for order {} (user {})", normalizedAmount, asset.getSymbol(), orderId, userId);
        return saved;
    }

    /**
     * Releases reserved funds back to available. Idempotent.
     */
    public void releaseReservation(UUID userId, UUID assetId, BigDecimal amount, UUID orderId) {
        MoneyUtils.validatePositive(amount);
        BigDecimal normalizedAmount = MoneyUtils.normalize(amount);
        
        WalletHold hold = walletHoldRepository.findActiveHoldWithLock(userId, assetId, REF_TYPE_ORDER, orderId)
            .orElse(null);
        
        if (hold == null) {
            log.debug("No active hold found for order {} - may have been already released", orderId);
            return;
        }
        
        if (hold.getStatus() != WalletHold.HoldStatus.ACTIVE) {
            log.debug("Hold {} is not ACTIVE (status: {}) - skipping release", hold.getId(), hold.getStatus());
            return;
        }
        
        BigDecimal releaseAmount = normalizedAmount.min(hold.getAmount());
        
        Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
        
        if (balance.getLocked().compareTo(releaseAmount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient locked balance. Locked: %s, Required: %s",
                    balance.getLocked(), releaseAmount));
        }
        
        // Move from locked back to available
        balance.setLocked(balance.getLocked().subtract(releaseAmount));
        balance.setAvailable(balance.getAvailable().add(releaseAmount));
        balanceRepository.save(balance);
        
        // Mark hold as released
        hold.setStatus(WalletHold.HoldStatus.RELEASED);
        walletHoldRepository.save(hold);
        
        log.info("Released {} {} reservation for order {} (user {})", releaseAmount, balance.getAsset().getSymbol(), orderId, userId);
    }

    /**
     * Captures reserved funds (spends them). Used when a trade executes.
     */
    public void captureReserved(UUID userId, UUID assetId, BigDecimal amount, UUID orderId) {
        MoneyUtils.validatePositive(amount);
        BigDecimal normalizedAmount = MoneyUtils.normalize(amount);
        
        WalletHold hold = walletHoldRepository.findActiveHoldWithLock(userId, assetId, REF_TYPE_ORDER, orderId)
            .orElse(null);
        
        if (hold == null) {
            log.warn("No active hold found for order {} when capturing - this may indicate a problem", orderId);
            // Still proceed with balance update for safety
            Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
                .orElseThrow(() -> new NotFoundException(
                    String.format("Balance not found for user %s and asset %s", userId, assetId)));
            
            if (balance.getLocked().compareTo(normalizedAmount) < 0) {
                throw new InsufficientBalanceException(
                    String.format("Insufficient locked balance. Locked: %s, Required: %s",
                        balance.getLocked(), normalizedAmount));
            }
            
            balance.setLocked(balance.getLocked().subtract(normalizedAmount));
            balanceRepository.save(balance);
            return;
        }
        
        BigDecimal captureAmount = normalizedAmount.min(hold.getAmount());
        
        Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
        
        if (balance.getLocked().compareTo(captureAmount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient locked balance. Locked: %s, Required: %s",
                    balance.getLocked(), captureAmount));
        }
        
        // Decrease locked (spend the reserved funds)
        balance.setLocked(balance.getLocked().subtract(captureAmount));
        balanceRepository.save(balance);
        
        // Update hold - if fully captured, mark as CAPTURED; otherwise reduce amount
        if (captureAmount.compareTo(hold.getAmount()) >= 0) {
            hold.setStatus(WalletHold.HoldStatus.CAPTURED);
        } else {
            hold.setAmount(hold.getAmount().subtract(captureAmount));
        }
        walletHoldRepository.save(hold);
        
        log.info("Captured {} {} reservation for order {} (user {})", captureAmount, balance.getAsset().getSymbol(), orderId, userId);
    }

    /**
     * Transfers funds between two users atomically. Locks balances in deterministic order to prevent deadlocks.
     */
    public void transfer(UUID fromUserId, UUID toUserId, UUID assetId, BigDecimal amount, 
                        String reason, UUID referenceId) {
        MoneyUtils.validatePositive(amount);
        BigDecimal normalizedAmount = MoneyUtils.normalize(amount);
        
        // Lock balances in deterministic order (by user ID) to prevent deadlocks
        UUID firstUserId, secondUserId;
        if (fromUserId.compareTo(toUserId) < 0) {
            firstUserId = fromUserId;
            secondUserId = toUserId;
        } else {
            firstUserId = toUserId;
            secondUserId = fromUserId;
        }
        
        Balance firstBalance = balanceRepository.findByUserIdAndAssetIdWithLock(firstUserId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", firstUserId, assetId)));
        
        Balance secondBalance = balanceRepository.findByUserIdAndAssetIdWithLock(secondUserId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", secondUserId, assetId)));
        
        // Determine which is from and which is to
        Balance fromBalance = firstUserId.equals(fromUserId) ? firstBalance : secondBalance;
        Balance toBalance = firstUserId.equals(fromUserId) ? secondBalance : firstBalance;
        
        // Check sufficient balance
        if (fromBalance.getAvailable().compareTo(normalizedAmount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient available balance for transfer. Available: %s, Required: %s",
                    fromBalance.getAvailable(), normalizedAmount));
        }
        
        // Perform transfer
        fromBalance.setAvailable(fromBalance.getAvailable().subtract(normalizedAmount));
        toBalance.setAvailable(toBalance.getAvailable().add(normalizedAmount));
        
        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);
        
        log.info("Transferred {} {} from user {} to user {} (reason: {}, ref: {})", 
            normalizedAmount, fromBalance.getAsset().getSymbol(), fromUserId, toUserId, reason, referenceId);
    }

    public void transferByCurrency(UUID fromUserId, UUID toUserId, String currency, BigDecimal amount,
                                   String reason, UUID referenceId) {
        Asset asset = assetService.getAssetBySymbol(currency.toUpperCase());
        transfer(fromUserId, toUserId, asset.getId(), amount, reason, referenceId);
    }

    // Legacy methods for backward compatibility
    public Balance lockFunds(UUID userId, UUID assetId, BigDecimal amount) {
        UUID tempOrderId = UUID.randomUUID();
        reserveForOrder(userId, assetId, amount, tempOrderId);
        return getBalance(userId, assetId);
    }

    public Balance unlockFunds(UUID userId, UUID assetId, BigDecimal amount) {
        releaseReservation(userId, assetId, amount, UUID.randomUUID());
        return getBalance(userId, assetId);
    }

    public Balance spendLocked(UUID userId, UUID assetId, BigDecimal amount) {
        captureReserved(userId, assetId, amount, UUID.randomUUID());
        return getBalance(userId, assetId);
    }
}
