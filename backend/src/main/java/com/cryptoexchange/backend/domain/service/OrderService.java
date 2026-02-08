package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.event.DomainOrderCreated;
import com.cryptoexchange.backend.domain.exception.InvalidOrderException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.model.Order;
import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.OrderStatus;
import com.cryptoexchange.backend.domain.model.OrderType;
import com.cryptoexchange.backend.domain.model.UserAccount;
import com.cryptoexchange.backend.domain.repository.OrderRepository;
import com.cryptoexchange.backend.domain.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing limit orders on the exchange.
 * 
 * <p>Handles order placement, cancellation, and trade settlement. All operations are transactional.
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Validates orders (amount, price, market status)</li>
 *   <li>Reserves funds when placing orders (quote currency for BUY, base currency for SELL)</li>
 *   <li>Releases funds when canceling orders</li>
 *   <li>Settles trades by transferring funds between buyer and seller</li>
 *   <li>Publishes domain events for order creation (sent to Kafka after transaction commit)</li>
 * </ul>
 * 
 * <p>Throws {@link InvalidOrderException} for invalid order parameters or operations.
 * Throws {@link NotFoundException} if order or user not found.
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final MarketService marketService;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, UserService userService, 
                       MarketService marketService, WalletService walletService,
                       ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.marketService = marketService;
        this.walletService = walletService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places a new limit order on the exchange.
     * 
     * <p>Validates order parameters, normalizes amounts to asset scales, reserves funds,
     * and publishes a domain event for order matching. The event is sent to Kafka after
     * the transaction commits.
     * 
     * <p>Funds are reserved based on order side:
     * <ul>
     *   <li>BUY orders: reserve quote currency (e.g., USDT)</li>
     *   <li>SELL orders: reserve base currency (e.g., BTC)</li>
     * </ul>
     * 
     * @param userId the user placing the order
     * @param marketId the market to trade on
     * @param side BUY or SELL
     * @param type LIMIT (price required) or MARKET
     * @param price order price (required for LIMIT orders)
     * @param amount order quantity
     * @return the created order with status NEW
     * @throws InvalidOrderException if validation fails or funds cannot be reserved
     */
    public Order placeOrder(UUID userId, UUID marketId, OrderSide side, OrderType type, 
                           BigDecimal price, BigDecimal amount) {
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
        
        BigDecimal normalizedQuantity = MoneyUtils.normalizeWithScaleDown(amount, market.getBaseAsset().getScale());
        BigDecimal normalizedPrice = price != null 
            ? MoneyUtils.normalizeWithScaleDown(price, market.getQuoteAsset().getScale())
            : null;
        
        Order order = new Order(user, market, side, type, normalizedPrice, normalizedQuantity);
        order.setStatus(OrderStatus.NEW);
        order = orderRepository.save(order);
        
        try {
            if (side == OrderSide.BUY) {
                BigDecimal reservedAmount = MoneyUtils.normalize(normalizedPrice.multiply(normalizedQuantity));
                walletService.reserveForOrder(userId, market.getQuoteAsset().getId(), reservedAmount, order.getId());
            } else {
                BigDecimal reservedAmount = MoneyUtils.normalize(normalizedQuantity);
                walletService.reserveForOrder(userId, market.getBaseAsset().getId(), reservedAmount, order.getId());
            }
        } catch (Exception e) {
            log.error("Failed to reserve funds for order {}: {}", order.getId(), e.getMessage());
            throw new InvalidOrderException("Failed to reserve funds: " + e.getMessage());
        }
        
        eventPublisher.publishEvent(new DomainOrderCreated(this, order.getId(), market.getSymbol(), side.name()));
        log.debug("Published DomainOrderCreated event for order {} (will be sent to Kafka after commit)", order.getId());
        
        return order;
    }

    /**
     * Cancels an order and releases reserved funds.
     * 
     * <p>Only orders with status NEW or PARTIALLY_FILLED can be canceled.
     * Releases the remaining reserved amount (unfilled portion) back to available balance.
     * 
     * @param orderId the order to cancel
     * @return the canceled order
     * @throws NotFoundException if order not found
     * @throws InvalidOrderException if order cannot be canceled (wrong status)
     */
    public Order cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new InvalidOrderException(
                String.format("Cannot cancel order with status %s. Only NEW or PARTIALLY_FILLED orders can be canceled",
                    order.getStatus()));
        }
        
        BigDecimal remainingAmount = order.getAmount().subtract(order.getFilledAmount());
        
        try {
            if (order.getSide() == OrderSide.BUY) {
                BigDecimal reservedAmount = MoneyUtils.normalize(order.getPrice().multiply(remainingAmount));
                walletService.releaseReservation(order.getUser().getId(), 
                    order.getMarket().getQuoteAsset().getId(), reservedAmount, orderId);
            } else {
                BigDecimal reservedAmount = MoneyUtils.normalize(remainingAmount);
                walletService.releaseReservation(order.getUser().getId(), 
                    order.getMarket().getBaseAsset().getId(), reservedAmount, orderId);
            }
        } catch (Exception e) {
            log.error("Failed to release reservation for order {}: {}", orderId, e.getMessage());
        }
        
        order.setStatus(OrderStatus.CANCELED);
        return orderRepository.save(order);
    }
    
    /**
     * Cancels an order for a specific user (ensures user can only cancel their own orders).
     */
    public Order cancelMyOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new InvalidOrderException(
                String.format("Cannot cancel order with status %s. Only NEW or PARTIALLY_FILLED orders can be canceled",
                    order.getStatus()));
        }
        
        // Calculate remaining reserved amount
        BigDecimal remainingAmount = order.getAmount().subtract(order.getFilledAmount());
        
        // Release reserved funds
        try {
            if (order.getSide() == OrderSide.BUY) {
                // BUY order: release quote currency
                BigDecimal reservedAmount = MoneyUtils.normalize(order.getPrice().multiply(remainingAmount));
                walletService.releaseReservation(userId, 
                    order.getMarket().getQuoteAsset().getId(), reservedAmount, orderId);
            } else {
                // SELL order: release base currency
                BigDecimal reservedAmount = MoneyUtils.normalize(remainingAmount);
                walletService.releaseReservation(userId, 
                    order.getMarket().getBaseAsset().getId(), reservedAmount, orderId);
            }
        } catch (Exception e) {
            log.error("Failed to release reservation for order {}: {}", orderId, e.getMessage());
            // Continue with cancellation even if release fails (may have been already released)
        }
        
        order.setStatus(OrderStatus.CANCELED);
        return orderRepository.save(order);
    }
    
    /**
     * Settles a trade execution by transferring funds between buyer and seller.
     * 
     * <p>Captures reserved funds and credits the appropriate currencies:
     * <ul>
     *   <li>Buyer: captures reserved quote currency, receives base currency</li>
     *   <li>Seller: captures reserved base currency, receives quote currency</li>
     * </ul>
     * 
     * <p>This method should be called atomically when a trade is executed.
     * 
     * @param buyerOrder the buyer's order
     * @param sellerOrder the seller's order
     * @param tradeAmount the amount of base currency traded
     * @param tradePrice the execution price
     */
    public void settleTrade(Order buyerOrder, Order sellerOrder, BigDecimal tradeAmount, BigDecimal tradePrice) {
        Market market = buyerOrder.getMarket();
        UUID buyerId = buyerOrder.getUser().getId();
        UUID sellerId = sellerOrder.getUser().getId();
        
        BigDecimal normalizedAmount = MoneyUtils.normalize(tradeAmount);
        BigDecimal normalizedPrice = MoneyUtils.normalize(tradePrice);
        BigDecimal quoteAmount = MoneyUtils.normalize(normalizedPrice.multiply(normalizedAmount));
        
        walletService.captureReserved(buyerId, market.getQuoteAsset().getId(), quoteAmount, buyerOrder.getId());
        walletService.deposit(buyerId, market.getBaseAsset().getId(), normalizedAmount);
        
        walletService.captureReserved(sellerId, market.getBaseAsset().getId(), normalizedAmount, sellerOrder.getId());
        walletService.deposit(sellerId, market.getQuoteAsset().getId(), quoteAmount);
        
        log.info("Settled trade: {} {} at {} {} (buyer: {}, seller: {})", 
            normalizedAmount, market.getBaseAsset().getSymbol(), 
            normalizedPrice, market.getQuoteAsset().getSymbol(),
            buyerId, sellerId);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
    }
    
    /**
     * Gets an order by ID for a specific user (ensures user can only access their own orders).
     */
    @Transactional(readOnly = true)
    public Order getMyOrder(UUID orderId, UUID userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(UUID userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Lists orders for a specific user with pagination support.
     */
    @Transactional(readOnly = true)
    public Page<Order> listMyOrders(UUID userId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findAllByUserIdAndStatus(userId, status, pageable);
        }
        return orderRepository.findAllByUserId(userId, pageable);
    }
}
