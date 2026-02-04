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
import com.cryptoexchange.backend.domain.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final MarketService marketService;
    private final WalletService walletService;

    public OrderService(OrderRepository orderRepository, UserService userService, 
                       MarketService marketService, WalletService walletService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.marketService = marketService;
        this.walletService = walletService;
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
        
        // Create order first
        Order order = new Order(user, market, side, type, price, amount);
        order = orderRepository.save(order);
        
        // Reserve funds based on order side
        try {
            if (side == OrderSide.BUY) {
                // BUY order: reserve quote currency (e.g., USDT)
                BigDecimal reservedAmount = MoneyUtils.normalize(price.multiply(amount));
                walletService.reserveForOrder(userId, market.getQuoteAsset().getId(), reservedAmount, order.getId());
            } else {
                // SELL order: reserve base currency (e.g., BTC)
                BigDecimal reservedAmount = MoneyUtils.normalize(amount);
                walletService.reserveForOrder(userId, market.getBaseAsset().getId(), reservedAmount, order.getId());
            }
        } catch (Exception e) {
            // If reservation fails, the transaction will rollback and order won't be saved
            log.error("Failed to reserve funds for order {}: {}", order.getId(), e.getMessage());
            throw new InvalidOrderException("Failed to reserve funds: " + e.getMessage());
        }
        
        return order;
    }

    public Order cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
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
                walletService.releaseReservation(order.getUser().getId(), 
                    order.getMarket().getQuoteAsset().getId(), reservedAmount, orderId);
            } else {
                // SELL order: release base currency
                BigDecimal reservedAmount = MoneyUtils.normalize(remainingAmount);
                walletService.releaseReservation(order.getUser().getId(), 
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
     * Settles a trade execution. Transfers funds between buyer and seller and captures reserved amounts.
     * This method should be called when a trade is executed.
     */
    public void settleTrade(Order buyerOrder, Order sellerOrder, BigDecimal tradeAmount, BigDecimal tradePrice) {
        Market market = buyerOrder.getMarket();
        UUID buyerId = buyerOrder.getUser().getId();
        UUID sellerId = sellerOrder.getUser().getId();
        
        BigDecimal normalizedAmount = MoneyUtils.normalize(tradeAmount);
        BigDecimal normalizedPrice = MoneyUtils.normalize(tradePrice);
        BigDecimal quoteAmount = MoneyUtils.normalize(normalizedPrice.multiply(normalizedAmount));
        
        // Buyer: capture reserved quote currency, credit base currency
        walletService.captureReserved(buyerId, market.getQuoteAsset().getId(), quoteAmount, buyerOrder.getId());
        walletService.deposit(buyerId, market.getBaseAsset().getId(), normalizedAmount);
        
        // Seller: capture reserved base currency, credit quote currency
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

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(UUID userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }
}
