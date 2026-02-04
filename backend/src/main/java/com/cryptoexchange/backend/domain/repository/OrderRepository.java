package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Order;
import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface OrderRepository extends JpaRepository<Order, UUID> {
    
    @Query("SELECT o FROM Order o WHERE o.market.id = :marketId AND o.side = :side " +
           "AND o.status IN :statuses ORDER BY o.createdAt ASC")
    List<Order> findOpenOrdersByMarketIdAndSide(
        @Param("marketId") UUID marketId,
        @Param("side") OrderSide side,
        @Param("statuses") List<OrderStatus> statuses
    );
    
    default List<Order> findOpenOrdersByMarketIdAndSide(UUID marketId, OrderSide side) {
        return findOpenOrdersByMarketIdAndSide(marketId, side, 
            List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED));
    }
    
    List<Order> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
    
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    Page<Order> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);
    
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.status = :status ORDER BY o.createdAt DESC")
    Page<Order> findAllByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") OrderStatus status, Pageable pageable);
    
    /**
     * Finds best SELL orders for a BUY taker (price-time priority).
     * Returns SELL orders where price <= takerPrice, ordered by price ASC, createdAt ASC.
     * Only includes orders with status NEW or PARTIALLY_FILLED.
     */
    @Query("SELECT o FROM Order o WHERE o.market.id = :marketId " +
           "AND o.side = :side " +
           "AND o.status IN :statuses " +
           "AND o.price <= :takerPrice " +
           "ORDER BY o.price ASC, o.createdAt ASC")
    List<Order> findBestSellOrdersForBuyTaker(
        @Param("marketId") UUID marketId,
        @Param("side") OrderSide side,
        @Param("statuses") List<OrderStatus> statuses,
        @Param("takerPrice") java.math.BigDecimal takerPrice,
        Pageable pageable
    );
    
    default List<Order> findBestSellOrdersForBuyTaker(UUID marketId, java.math.BigDecimal takerPrice, Pageable pageable) {
        return findBestSellOrdersForBuyTaker(marketId, OrderSide.SELL, 
            List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED), takerPrice, pageable);
    }
    
    /**
     * Finds best BUY orders for a SELL taker (price-time priority).
     * Returns BUY orders where price >= takerPrice, ordered by price DESC, createdAt ASC.
     * Only includes orders with status NEW or PARTIALLY_FILLED.
     */
    @Query("SELECT o FROM Order o WHERE o.market.id = :marketId " +
           "AND o.side = :side " +
           "AND o.status IN :statuses " +
           "AND o.price >= :takerPrice " +
           "ORDER BY o.price DESC, o.createdAt ASC")
    List<Order> findBestBuyOrdersForSellTaker(
        @Param("marketId") UUID marketId,
        @Param("side") OrderSide side,
        @Param("statuses") List<OrderStatus> statuses,
        @Param("takerPrice") java.math.BigDecimal takerPrice,
        Pageable pageable
    );
    
    default List<Order> findBestBuyOrdersForSellTaker(UUID marketId, java.math.BigDecimal takerPrice, Pageable pageable) {
        return findBestBuyOrdersForSellTaker(marketId, OrderSide.BUY,
            List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED), takerPrice, pageable);
    }
    
    /**
     * Finds an order by ID with pessimistic write lock for matching.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdWithLock(@Param("orderId") UUID orderId);
}
