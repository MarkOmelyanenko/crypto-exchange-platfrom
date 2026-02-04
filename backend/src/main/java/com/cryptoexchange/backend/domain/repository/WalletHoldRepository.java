package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.WalletHold;
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
public interface WalletHoldRepository extends JpaRepository<WalletHold, UUID> {
    
    @Query("SELECT h FROM WalletHold h WHERE h.user.id = :userId AND h.asset.id = :assetId " +
           "AND h.refType = :refType AND h.refId = :refId AND h.status = 'ACTIVE'")
    Optional<WalletHold> findActiveHold(@Param("userId") UUID userId, 
                                        @Param("assetId") UUID assetId,
                                        @Param("refType") String refType,
                                        @Param("refId") UUID refId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM WalletHold h WHERE h.user.id = :userId AND h.asset.id = :assetId " +
           "AND h.refType = :refType AND h.refId = :refId AND h.status = 'ACTIVE'")
    Optional<WalletHold> findActiveHoldWithLock(@Param("userId") UUID userId, 
                                                 @Param("assetId") UUID assetId,
                                                 @Param("refType") String refType,
                                                 @Param("refId") UUID refId);
    
    @Query("SELECT h FROM WalletHold h WHERE h.user.id = :userId AND h.status = 'ACTIVE'")
    List<WalletHold> findActiveHoldsByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT h FROM WalletHold h WHERE h.user.id = :userId AND h.asset.id = :assetId AND h.status = 'ACTIVE'")
    List<WalletHold> findActiveHoldsByUserIdAndAssetId(@Param("userId") UUID userId, 
                                                        @Param("assetId") UUID assetId);
}
