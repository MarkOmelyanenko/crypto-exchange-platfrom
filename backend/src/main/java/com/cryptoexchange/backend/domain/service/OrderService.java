package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InvalidOrderException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.model.Order;
import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.OrderStatus;
import com.cryptoexchange.backend.domain.model.OrderType;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final MarketService marketService;

    public OrderService(OrderRepository orderRepository, UserService userService, MarketService marketService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.marketService = marketService;
    }

    public Order placeOrder(UUID userId, UUID marketId, OrderSide side, OrderType type, 
                           BigDecimal price, BigDecimal amount) {
        // Validation
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Order amount must be positive");
        }
        
        if (type == OrderType.LIMIT) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidOrderException("LIMIT orders must have a positive price");
            }
        }
        
        UserAccount user = userService.getUser(userId);
        Market market = marketService.getMarket(marketId);
        
        if (!market.getActive()) {
            throw new InvalidOrderException("Cannot place order on inactive market: " + market.getSymbol());
        }
        
        Order order = new Order(user, market, side, type, price, amount);
        return orderRepository.save(order);
    }

    public Order cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new InvalidOrderException(
                String.format("Cannot cancel order with status %s. Only NEW or PARTIALLY_FILLED orders can be canceled",
                    order.getStatus()));
        }
        
        order.setStatus(OrderStatus.CANCELED);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(UUID userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }
}
