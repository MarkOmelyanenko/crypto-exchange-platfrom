package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {

    @Query("SELECT t FROM Trade t JOIN FETCH t.pair p JOIN FETCH p.baseAsset JOIN FETCH p.quoteAsset " +
           "WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    Page<Trade> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT t FROM Trade t JOIN FETCH t.pair p JOIN FETCH p.baseAsset JOIN FETCH p.quoteAsset " +
           "WHERE t.pair.id = :pairId ORDER BY t.createdAt DESC")
    Page<Trade> findAllByPairId(@Param("pairId") UUID pairId, Pageable pageable);

    @Query("SELECT t FROM Trade t JOIN FETCH t.pair p JOIN FETCH p.baseAsset JOIN FETCH p.quoteAsset " +
           "WHERE t.pair.id = :pairId ORDER BY t.createdAt DESC")
    List<Trade> findAllByPairIdOrderByCreatedAtDesc(@Param("pairId") UUID pairId);

    @Query("SELECT t FROM Trade t JOIN FETCH t.pair p JOIN FETCH p.baseAsset JOIN FETCH p.quoteAsset " +
           "WHERE t.user.id = :userId AND t.pair.id = :pairId ORDER BY t.createdAt DESC")
    List<Trade> findAllByUserIdAndPairIdOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("pairId") UUID pairId);
}
