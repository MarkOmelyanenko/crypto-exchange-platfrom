package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Balance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, UUID> {
    @Query("SELECT b FROM Balance b WHERE b.user.id = :userId AND b.asset.id = :assetId")
    Optional<Balance> findByUserIdAndAssetId(@Param("userId") UUID userId, @Param("assetId") UUID assetId);
    
    @Query("SELECT b FROM Balance b WHERE b.user.id = :userId")
    List<Balance> findAllByUserId(@Param("userId") UUID userId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Balance b WHERE b.user.id = :userId AND b.asset.id = :assetId")
    Optional<Balance> findByUserIdAndAssetIdWithLock(@Param("userId") UUID userId, @Param("assetId") UUID assetId);
}
