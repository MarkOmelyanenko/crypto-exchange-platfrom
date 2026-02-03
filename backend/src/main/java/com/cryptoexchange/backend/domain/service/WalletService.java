package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WalletService {

    private final BalanceRepository balanceRepository;
    private final UserService userService;
    private final AssetService assetService;

    public WalletService(BalanceRepository balanceRepository, UserService userService, AssetService assetService) {
        this.balanceRepository = balanceRepository;
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

    public Balance deposit(UUID userId, UUID assetId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        
        UserAccount user = userService.getUser(userId);
        Asset asset = assetService.getAsset(assetId);
        
        Balance balance = balanceRepository.findByUserIdAndAssetId(userId, assetId)
            .orElseGet(() -> {
                Balance newBalance = new Balance(user, asset);
                return balanceRepository.save(newBalance);
            });
        
        balance.setAvailable(balance.getAvailable().add(amount));
        return balanceRepository.save(balance);
    }

    public Balance lockFunds(UUID userId, UUID assetId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Lock amount must be positive");
        }
        
        Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
        
        if (balance.getAvailable().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient available balance. Available: %s, Required: %s",
                    balance.getAvailable(), amount));
        }
        
        balance.setAvailable(balance.getAvailable().subtract(amount));
        balance.setLocked(balance.getLocked().add(amount));
        return balanceRepository.save(balance);
    }

    public Balance unlockFunds(UUID userId, UUID assetId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unlock amount must be positive");
        }
        
        Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
        
        if (balance.getLocked().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient locked balance. Locked: %s, Required: %s",
                    balance.getLocked(), amount));
        }
        
        balance.setLocked(balance.getLocked().subtract(amount));
        balance.setAvailable(balance.getAvailable().add(amount));
        return balanceRepository.save(balance);
    }

    public Balance spendLocked(UUID userId, UUID assetId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Spend amount must be positive");
        }
        
        Balance balance = balanceRepository.findByUserIdAndAssetIdWithLock(userId, assetId)
            .orElseThrow(() -> new NotFoundException(
                String.format("Balance not found for user %s and asset %s", userId, assetId)));
        
        if (balance.getLocked().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient locked balance. Locked: %s, Required: %s",
                    balance.getLocked(), amount));
        }
        
        balance.setLocked(balance.getLocked().subtract(amount));
        return balanceRepository.save(balance);
    }
}
