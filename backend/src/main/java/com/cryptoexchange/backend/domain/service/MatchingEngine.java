package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.event.DomainTradeExecuted;
import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.model.Order;
import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.OrderStatus;
import com.cryptoexchange.backend.domain.model.Trade;
import com.cryptoexchange.backend.domain.repository.OrderRepository;
import com.cryptoexchange.backend.domain.repository.TradeRepository;
import com.cryptoexchange.backend.domain.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Matching engine implementing price-time priority matching for LIMIT orders.
 * 
 * Matching rules:
 * - Price priority: better prices match first
 * - Time priority: earlier orders match first at the same price
 * - Maker price: trades execute at the maker's (resting order) price
 * - Partial fills: orders can be partially filled
 */
@Service
@Transactional
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);
    private static final int MATCHING_BATCH_SIZE = 50;

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    public MatchingEngine(OrderRepository orderRepository, 
                         TradeRepository tradeRepository,
                         WalletService walletService,
                         ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.walletService = walletService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Matches an incoming order (taker) against resting orders (makers).
     * 
     * @param takerOrderId The ID of the order to match
     * @return List of trades executed
     */
    public List<Trade> matchOrder(UUID takerOrderId) {
        // Lock the taker order with pessimistic write lock
        Order takerOrder = orderRepository.findByIdWithLock(takerOrderId)
            .orElseThrow(() -> new IllegalArgumentException("Taker order not found: " + takerOrderId));

        // Check if order can be matched
        if (takerOrder.getStatus() != OrderStatus.NEW && takerOrder.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            log.debug("Order {} cannot be matched - status: {}", takerOrderId, takerOrder.getStatus());
            return List.of();
        }

        if (takerOrder.getType() != com.cryptoexchange.backend.domain.model.OrderType.LIMIT) {
            log.debug("Order {} cannot be matched - only LIMIT orders are supported", takerOrderId);
            return List.of();
        }

        Market market = takerOrder.getMarket();
        BigDecimal takerRemaining = takerOrder.getAmount().subtract(takerOrder.getFilledAmount());
        
        if (takerRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Order {} is already fully filled", takerOrderId);
            return List.of();
        }

        List<Trade> executedTrades = new ArrayList<>();

        // Match until taker is fully filled or no more makers available
        while (takerRemaining.compareTo(BigDecimal.ZERO) > 0) {
            // Reload taker order to get latest state
            takerOrder = orderRepository.findByIdWithLock(takerOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Taker order not found: " + takerOrderId));
            
            takerRemaining = takerOrder.getAmount().subtract(takerOrder.getFilledAmount());
            if (takerRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            // Find best matching maker orders
            List<Order> makers = findBestMakers(market.getId(), takerOrder.getSide(), takerOrder.getPrice());
            
            if (makers.isEmpty()) {
                log.debug("No matching makers found for order {}", takerOrderId);
                break;
            }

            // Match against the first maker
            Order maker = makers.get(0);
            
            // Lock the maker order
            Order lockedMaker = orderRepository.findByIdWithLock(maker.getId())
                .orElse(null);
            
            if (lockedMaker == null || 
                (lockedMaker.getStatus() != OrderStatus.NEW && lockedMaker.getStatus() != OrderStatus.PARTIALLY_FILLED)) {
                // Maker was filled or cancelled, continue to next iteration
                continue;
            }

            // Calculate fill quantity
            BigDecimal makerRemaining = lockedMaker.getAmount().subtract(lockedMaker.getFilledAmount());
            BigDecimal fillQty = takerRemaining.min(makerRemaining);
            
            // Normalize fill quantity to base asset scale
            fillQty = MoneyUtils.normalizeWithScaleDown(fillQty, market.getBaseAsset().getScale());
            
            if (fillQty.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            // Trade price is maker's price (price-time priority)
            BigDecimal tradePrice = lockedMaker.getPrice();
            BigDecimal quoteAmount = tradePrice.multiply(fillQty);
            
            // Normalize price and quote amount to quote asset scale
            tradePrice = MoneyUtils.normalizeWithScaleDown(tradePrice, market.getQuoteAsset().getScale());
            quoteAmount = MoneyUtils.normalizeWithScaleDown(quoteAmount, market.getQuoteAsset().getScale());

            // Execute trade
            Trade trade = executeTrade(takerOrder, lockedMaker, fillQty, tradePrice, quoteAmount);
            executedTrades.add(trade);

            // Update order statuses
            updateOrderStatus(takerOrder, fillQty);
            updateOrderStatus(lockedMaker, fillQty);

            // Reload taker for next iteration
            takerOrder = orderRepository.findByIdWithLock(takerOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Taker order not found: " + takerOrderId));
            takerRemaining = takerOrder.getAmount().subtract(takerOrder.getFilledAmount());
        }

        log.info("Matched order {}: {} trades executed", takerOrderId, executedTrades.size());
        return executedTrades;
    }

    /**
     * Finds the best maker orders for a taker order (price-time priority).
     */
    private List<Order> findBestMakers(UUID marketId, OrderSide takerSide, BigDecimal takerPrice) {
        PageRequest pageRequest = PageRequest.of(0, MATCHING_BATCH_SIZE);
        
        if (takerSide == OrderSide.BUY) {
            // For BUY taker, find SELL makers with price <= takerPrice, ordered by price ASC, createdAt ASC
            return orderRepository.findBestSellOrdersForBuyTaker(marketId, takerPrice, pageRequest);
        } else {
            // For SELL taker, find BUY makers with price >= takerPrice, ordered by price DESC, createdAt ASC
            return orderRepository.findBestBuyOrdersForSellTaker(marketId, takerPrice, pageRequest);
        }
    }

    /**
     * Executes a trade between taker and maker orders.
     * Handles balance settlement with price improvement for taker.
     * Creates two Trade records: one for the buyer, one for the seller.
     * Returns the taker's Trade record.
     */
    private Trade executeTrade(Order takerOrder, Order makerOrder, BigDecimal fillQty, 
                               BigDecimal tradePrice, BigDecimal quoteAmount) {
        Market market = takerOrder.getMarket();
        
        // Determine buyer and seller
        Order buyerOrder = takerOrder.getSide() == OrderSide.BUY ? takerOrder : makerOrder;
        Order sellerOrder = takerOrder.getSide() == OrderSide.BUY ? makerOrder : takerOrder;

        // Settle balances
        settleTradeBalances(buyerOrder, sellerOrder, fillQty, tradePrice, quoteAmount, market);

        // Create trade records (one per side)
        Trade buyTrade = new Trade(buyerOrder.getUser(), market, OrderSide.BUY, tradePrice, fillQty, quoteAmount);
        Trade sellTrade = new Trade(sellerOrder.getUser(), market, OrderSide.SELL, tradePrice, fillQty, quoteAmount);
        buyTrade = tradeRepository.save(buyTrade);
        sellTrade = tradeRepository.save(sellTrade);

        // Return the taker's trade
        Trade takerTrade = takerOrder.getSide() == OrderSide.BUY ? buyTrade : sellTrade;

        log.info("Executed trade: {} {} at {} {} (maker: {}, taker: {})",
            fillQty, market.getBaseAsset().getSymbol(),
            tradePrice, market.getQuoteAsset().getSymbol(),
            makerOrder.getId(), takerOrder.getId());

        // Publish domain event - will be sent to Kafka AFTER transaction commit
        Instant executedAtInstant = takerTrade.getCreatedAt().toInstant();
        eventPublisher.publishEvent(new DomainTradeExecuted(
            this, 
            takerTrade.getId(), 
            market.getSymbol(), 
            tradePrice, 
            fillQty, 
            executedAtInstant
        ));
        log.debug("Published DomainTradeExecuted event for trade {} (will be sent to Kafka after commit)", takerTrade.getId());

        return takerTrade;
    }

    /**
     * Settles balances for a trade execution.
     * Handles price improvement for taker (refund if taker price > maker price).
     */
    private void settleTradeBalances(Order buyerOrder, Order sellerOrder, BigDecimal fillQty,
                                     BigDecimal tradePrice, BigDecimal quoteAmount, Market market) {
        UUID buyerId = buyerOrder.getUser().getId();
        UUID sellerId = sellerOrder.getUser().getId();
        
        // Buyer settlement
        if (buyerOrder.getSide() == OrderSide.BUY) {
            // Calculate what was initially reserved at taker price
            BigDecimal reservedAtTakerPrice = buyerOrder.getPrice().multiply(fillQty);
            reservedAtTakerPrice = MoneyUtils.normalizeWithScaleDown(reservedAtTakerPrice, market.getQuoteAsset().getScale());
            
            // Actual amount spent at maker price
            BigDecimal actualSpent = quoteAmount;
            
            // Price improvement: refund difference if taker price > maker price
            if (reservedAtTakerPrice.compareTo(actualSpent) > 0) {
                BigDecimal refund = reservedAtTakerPrice.subtract(actualSpent);
                refund = MoneyUtils.normalize(refund);
                
                // Capture the actual spent amount
                walletService.captureReserved(buyerId, market.getQuoteAsset().getId(), actualSpent, buyerOrder.getId());
                
                // Refund the difference back to available
                walletService.deposit(buyerId, market.getQuoteAsset().getId(), refund);
            } else {
                // Capture the reserved amount (should be exactly actualSpent in normal case)
                walletService.captureReserved(buyerId, market.getQuoteAsset().getId(), actualSpent, buyerOrder.getId());
            }
            
            // Credit base currency to buyer
            walletService.deposit(buyerId, market.getBaseAsset().getId(), fillQty);
        }

        // Seller settlement
        if (sellerOrder.getSide() == OrderSide.SELL) {
            // Capture reserved base currency
            walletService.captureReserved(sellerId, market.getBaseAsset().getId(), fillQty, sellerOrder.getId());
            
            // Credit quote currency to seller
            walletService.deposit(sellerId, market.getQuoteAsset().getId(), quoteAmount);
        }
    }

    /**
     * Updates order filled amount and status.
     */
    private void updateOrderStatus(Order order, BigDecimal fillQty) {
        BigDecimal newFilledAmount = order.getFilledAmount().add(fillQty);
        order.setFilledAmount(newFilledAmount);

        if (newFilledAmount.compareTo(order.getAmount()) >= 0) {
            order.setStatus(OrderStatus.FILLED);
        } else if (newFilledAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }

        orderRepository.save(order);
    }
}
