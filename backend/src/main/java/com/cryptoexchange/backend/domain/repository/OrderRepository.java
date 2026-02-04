package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.Order;
import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
